package com.wanluk.libexport

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.wanluk.foundation.recording.RecordingFiles
import com.wanluk.foundation.survey.SessionExport
import com.wanluk.foundation.survey.ChinesePhonology
import com.wanluk.foundation.survey.RecordingTaskSnapshot
import com.wanluk.foundation.survey.ResultExportNames
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.foundation.survey.SurveyPackageCodec
import java.io.OutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ResultExporter(private val files: RecordingFiles) {
  private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
    .registerTypeAdapter(SurveyPackage::class.java, object : TypeAdapter<SurveyPackage>() {
      override fun write(output: JsonWriter, task: SurveyPackage?) {
        if (task == null) output.nullValue() else output.jsonValue(SurveyPackageCodec.encode(task))
      }
      override fun read(input: JsonReader): SurveyPackage = throw UnsupportedOperationException("仅用于成果导出")
    }).create()

  suspend fun validate(bundle: SessionExport, checkAdopted: Boolean = true) = withContext(Dispatchers.IO) { sources(bundle, checkAdopted); Unit }

  /** Stream directly to the user-selected document; no second copy of all audio in app storage. */
  suspend fun write(output: OutputStream, bundle: SessionExport, progress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
    ZipOutputStream(output.buffered()).use { writeSession(it, bundle, "", null, progress) }
  }

  suspend fun writeBatch(output: OutputStream, ids: List<String>, load: suspend (String) -> SessionExport,
    progress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
    ZipOutputStream(output.buffered()).use { zip ->
      val index = mutableListOf<Map<String, String>>()
      val directories = mutableSetOf<String>()
      ids.forEachIndexed { number, id ->
        currentCoroutineContext().ensureActive()
        val bundle = load(id)
        require(java.util.UUID.fromString(id).toString() == id && bundle.sessionId == id)
        val directory = ResultExportNames.sessionDirectory(bundle, directories)
        writeSession(zip, bundle, directory, null) { _, _ -> progress(number + 1, ids.size) }
        index += mapOf("sessionId" to id, "title" to bundle.title, "directory" to directory)
      }
      zip.text("index.json", gson.toJson(index))
      zip.text("README.txt", "韵录批量成果包。sessions/ 下每个目录是一条完整录制，名称与会话编号见 index.json。")
    }
  }

  suspend fun writeSteps(output: OutputStream, bundle: SessionExport, positions: List<Int>, progress: (Int, Int) -> Unit) =
    withContext(Dispatchers.IO) {
      ZipOutputStream(output.buffered()).use { writeSession(it, bundle, "", positions, progress) }
    }

  suspend fun writeTakes(output: OutputStream, bundle: SessionExport, ids: List<String>, progress: (Int, Int) -> Unit) =
    withContext(Dispatchers.IO) {
      val selectedIds = ids.toSet()
      val selected = bundle.takes.filter { it.takeId in selectedIds && it.state == "saved" }
      require(selected.isNotEmpty()) { "没有可导出的录音" }
      val available = sources(bundle.copy(takes = selected), checkAdopted = false)
      ZipOutputStream(output.buffered()).use { zip ->
        val checksums = writeAudio(zip, selected, available, "", progress)
        zip.text("task.json", RecordingTaskSnapshot.encodeResult(bundle.task))
        val positions = selected.mapTo(hashSetOf()) { it.position }
        zip.text("recordings.json", gson.toJson(bundle.copy(takes = selected, steps = bundle.steps.filter { it.position in positions })))
        zip.text("selection.json", gson.toJson(mapOf("scope" to "selected_takes", "takeIds" to selected.map { it.takeId })))
        zip.text("sha256.json", gson.toJson(checksums))
        val steps = bundle.steps.associateBy { it.position }
        val items = bundle.task.items.associateBy { it.itemId }
        val rows = listOf(listOf("take_id", "position", "text", "adopted", "duration_ms", "sample_rate", "audio_file") + EXTRA_COLUMNS + "applied_gain") + selected.map {
          val step = steps.getValue(it.position)
          listOf(it.takeId, (it.position + 1).toString(), step.text, (step.selectedTakeId == it.takeId).toString(),
            it.durationMs.toString(), it.sampleRate.toString(), it.audioFile) + extraCells(bundle, items.getValue(step.itemId).phonology, it.reviewStatus) +
            it.appliedGain.toString()
        }
        zip.text("takes.csv", "\uFEFF" + rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::csv) } + "\r\n")
        zip.text("README.txt", "韵录所选录音历史。仅含 selection.json 中所列的已保存 WAV；task.json 保留完整任务快照，本包不代表整条会话。新录音保存前施加最多4倍的整段统一线性增益，recordings.json 的 appliedGain 和 takes.csv 的 applied_gain 记录倍率，旧录音为1；导出不再改变音频字节。")
      }
    }

  private fun sources(bundle: SessionExport, checkAdopted: Boolean = true): Map<String, File> {
    val saved = bundle.takes.filter { it.state == "saved" }.sortedBy { it.createdAt }
    check(saved.isNotEmpty()) { "没有可导出的录音" }
    check(saved.map { it.audioFile.lowercase(java.util.Locale.ROOT) }.distinct().size == saved.size &&
      saved.all { ResultExportNames.isSafeAudioFile(it.audioFile) }) { "导出录音名称不合法或重复" }
    val sources = saved.associate { take ->
      check(take.sampleRate == 48000 && take.channels == 1 && take.bitsPerSample == 16) { "录音参数与预设不一致" }
      val source = files.take(bundle.sessionId, take.takeId)
      check(source.isFile && source.length() >= 46) { "缺少已保存的录音文件，导出已停止；请检查该任务" }
      val header = ByteArray(44)
      java.io.DataInputStream(source.inputStream()).use { it.readFully(header) }
      val format = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
      check(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(header, 8, 8, Charsets.US_ASCII) == "WAVEfmt " &&
        String(header, 36, 4, Charsets.US_ASCII) == "data" &&
        format.getInt(4).toLong() + 8 == source.length() && format.getInt(16) == 16 &&
        format.getShort(20).toInt() == 1 && format.getShort(22).toInt() == take.channels &&
        format.getInt(24) == take.sampleRate && format.getShort(34).toInt() == take.bitsPerSample &&
        format.getInt(40).toLong() + 44 == source.length() &&
        (source.length() - 44) / 2 * 1000 / take.sampleRate == take.durationMs) {
        "录音文件头或长度与元数据不一致，导出已停止；本地文件仍保留"
      }
      take.takeId to source
    }
    if (checkAdopted) check(bundle.steps.all { it.selectedTakeId == null || it.selectedTakeId in sources }) { "采用的录音文件不完整" }
    return sources
  }

  private suspend fun writeAudio(zip: ZipOutputStream,
    saved: List<com.wanluk.foundation.survey.ExportTake>, sources: Map<String, File>, prefix: String,
    progress: (Int, Int) -> Unit): Map<String, String> {
    val checksums = linkedMapOf<String, String>()
    val buffer = ByteArray(64 * 1024)
    saved.forEachIndexed { index, take ->
      currentCoroutineContext().ensureActive()
      val entry = take.audioFile
      val digest = MessageDigest.getInstance("SHA-256")
      zip.putNextEntry(ZipEntry(prefix + entry))
      sources.getValue(take.takeId).inputStream().use { input ->
        while (true) {
          currentCoroutineContext().ensureActive()
          val count = input.read(buffer)
          if (count < 0) break
          zip.write(buffer, 0, count)
          digest.update(buffer, 0, count)
        }
      }
      zip.closeEntry()
      checksums[entry] = digest.digest().joinToString("") { "%02x".format(it) }
      progress(index + 1, saved.size)
    }
    return checksums
  }

  private suspend fun writeSession(zip: ZipOutputStream, bundle: SessionExport, prefix: String,
    positions: List<Int>?, progress: (Int, Int) -> Unit) {
      val available = sources(bundle)
      val saved = bundle.takes.filter { it.state == "saved" }.sortedBy { it.createdAt }
      zip.text(prefix + "task.json", RecordingTaskSnapshot.encodeResult(bundle.task))
      val checksums = writeAudio(zip, saved, available, prefix, progress)
      zip.text(prefix + "session.json", gson.toJson(bundle))
      zip.text(prefix + "sha256.json", gson.toJson(checksums))
      if (positions != null) zip.text(prefix + "selection.json", gson.toJson(mapOf("scope" to "selected_steps", "positionBase" to 0, "positions" to positions)))
      val takesById = bundle.takes.associateBy { it.takeId }
      val items = bundle.task.items.associateBy { it.itemId }
      val rows = mutableListOf(listOf("session_id", "speaker_alias", "dialect", "package_id", "revision",
        "position", "item_id", "type", "text", "repetition", "status", "selected_take_id", "audio_file",
        "duration_ms", "sample_rate", "channels", "bits_per_sample", "audio_source", "input_device",
        "peak", "rms", "clipped_fraction", "warning", "skip_reason", "note") + EXTRA_COLUMNS + "applied_gain")
      bundle.steps.sortedBy { it.position }.forEach { step ->
        val take = step.selectedTakeId?.let(takesById::get)
        rows.add(listOf(bundle.sessionId, bundle.speakerAlias, bundle.dialect, bundle.task.packageId,
          bundle.task.revision.toString(), (step.position + 1).toString(), step.itemId, step.type, step.text,
          step.repetition.toString(), when { take != null -> "recorded"; step.skipReason != null -> "skipped"; else -> "pending" },
          take?.takeId.orEmpty(), take?.audioFile.orEmpty(),
          take?.durationMs?.toString().orEmpty(), take?.sampleRate?.toString().orEmpty(),
          take?.channels?.toString().orEmpty(), take?.bitsPerSample?.toString().orEmpty(),
          take?.audioSource.orEmpty(), take?.inputDevice.orEmpty(), take?.peak?.toString().orEmpty(),
          take?.rms?.toString().orEmpty(), take?.clippedFraction?.toString().orEmpty(), take?.warning.orEmpty(),
          step.skipReason.orEmpty(), step.note) + extraCells(bundle, items.getValue(step.itemId).phonology, take?.reviewStatus.orEmpty()) +
          take?.appliedGain?.toString().orEmpty())
      }
      zip.text(prefix + "metadata.csv", "\uFEFF" + rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::csv) } + "\r\n")
      zip.text(prefix + "README.txt", """
        韵录 单机录制成果包 v3
        task.json：录制时的完整任务快照，不受后续编辑影响。
        metadata.csv：每个录制步骤及采用的音频，末尾追加研究信息、版本复核状态和录制时冻结的音韵字段。
        session.json：包括全部录音尝试、参数、备注、跳过原因；时间为 Unix 毫秒。
        audio/：全部成功保存的 WAV，包括尚未采用的重录版本；导出不再改变音频字节。
        新录音保存前施加整段统一线性增益，最多4倍，目标峰值不超过29000/32768；高峰值录音不放大。
        takes[].appliedGain 和 metadata.csv 的 applied_gain 记录保存增益，旧录音为1；peak/rms 为保存后数值。
        clippedFraction 和削波提示反映增益前采集信号，放大不能修复原有削波或改善信噪比。
        文件名包含题号、录制内容、发音人、方言、重复次数和录音版本；takes[].audioFile 保存实际相对路径。
        sha256.json：audio/ 中每个文件的 SHA-256。
        pending 表示未完成；skipped 表示发音人主动跳过；中断的临时音频不导出。
        CSV 对可能被表格软件解释为公式的文本添加单引号，原始文本保留在 JSON。
        本包包含发音人代号、方言背景和声音，请仅转交给获准接收的人。
        录音参数和简单质量提示不代表已通过科研质量审核。
      """.trimIndent() + if (positions != null) "\n本包仅含 selection.json 指定的步骤，编号保持原值；task.json 保留完整任务快照。" else "")
  }

  private fun extraCells(bundle: SessionExport, phonology: ChinesePhonology?, review: String): List<String> =
    listOf(bundle.title, bundle.researchCode, bundle.collectionLocation, bundle.collector, review,
      phonology?.sheng.orEmpty(), phonology?.hu.orEmpty(), phonology?.deng?.toString().orEmpty(),
      phonology?.yun.orEmpty(), phonology?.diao.orEmpty(), phonology?.she.orEmpty(), phonology?.zu.orEmpty(), phonology?.sourceId.orEmpty())

  private fun ZipOutputStream.text(name: String, value: String) {
    putNextEntry(ZipEntry(name))
    write(value.toByteArray(Charsets.UTF_8))
    closeEntry()
  }

  private companion object {
    val EXTRA_COLUMNS = listOf("recording_title", "research_code", "collection_location", "collector", "review_status",
      "sheng", "hu", "deng", "yun", "diao", "she", "zu", "source_id")
  }
  private fun csv(value: String): String {
    val guarded = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
      value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
    return "\"${guarded.replace("\"", "\"\"")}\""
  }
}
