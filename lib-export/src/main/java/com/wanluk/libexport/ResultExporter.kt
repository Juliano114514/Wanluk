package com.wanluk.libexport

import com.google.gson.GsonBuilder
import com.wanluk.foundation.recording.RecordingFiles
import com.wanluk.foundation.survey.SessionExport
import com.wanluk.foundation.survey.RecordingTaskSnapshot
import java.io.OutputStream
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
  private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

  /** Stream directly to the user-selected document; no second copy of all audio in app storage. */
  suspend fun write(output: OutputStream, bundle: SessionExport, progress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
    val saved = bundle.takes.filter { it.state == "saved" }.sortedBy { it.createdAt }
    val sources = saved.associate { take ->
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
    check(bundle.steps.all { it.selectedTakeId == null || it.selectedTakeId in sources }) { "采用的录音文件不完整" }
    val checksums = linkedMapOf<String, String>()
    val buffer = ByteArray(64 * 1024)
    ZipOutputStream(output.buffered()).use { zip ->
      zip.text("task.json", RecordingTaskSnapshot.encodeResult(bundle.task))
      saved.forEachIndexed { index, take ->
        currentCoroutineContext().ensureActive()
        val entry = "audio/${take.takeId}.wav"
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(entry))
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
      zip.text("session.json", gson.toJson(bundle))
      zip.text("sha256.json", gson.toJson(checksums))
      val takesById = bundle.takes.associateBy { it.takeId }
      val rows = mutableListOf(listOf("session_id", "speaker_alias", "dialect", "package_id", "revision",
        "position", "item_id", "type", "text", "repetition", "status", "selected_take_id", "audio_file",
        "duration_ms", "sample_rate", "channels", "bits_per_sample", "audio_source", "input_device",
        "peak", "rms", "clipped_fraction", "warning", "skip_reason", "note"))
      bundle.steps.sortedBy { it.position }.forEach { step ->
        val take = step.selectedTakeId?.let(takesById::get)
        rows.add(listOf(bundle.sessionId, bundle.speakerAlias, bundle.dialect, bundle.task.packageId,
          bundle.task.revision.toString(), (step.position + 1).toString(), step.itemId, step.type, step.text,
          step.repetition.toString(), when { take != null -> "recorded"; step.skipReason != null -> "skipped"; else -> "pending" },
          take?.takeId.orEmpty(), take?.let { "audio/${it.takeId}.wav" }.orEmpty(),
          take?.durationMs?.toString().orEmpty(), take?.sampleRate?.toString().orEmpty(),
          take?.channels?.toString().orEmpty(), take?.bitsPerSample?.toString().orEmpty(),
          take?.audioSource.orEmpty(), take?.inputDevice.orEmpty(), take?.peak?.toString().orEmpty(),
          take?.rms?.toString().orEmpty(), take?.clippedFraction?.toString().orEmpty(), take?.warning.orEmpty(),
          step.skipReason.orEmpty(), step.note))
      }
      zip.text("metadata.csv", "\uFEFF" + rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::csv) } + "\r\n")
      zip.text("README.txt", """
        韵录 单机录制成果包 v1
        task.json：录制时的完整任务快照，不受后续编辑影响。
        metadata.csv：每个录制步骤及采用的音频，可在表格软件中查看。
        session.json：包括全部录音尝试、参数、备注、跳过原因；时间为 Unix 毫秒。
        audio/：全部成功保存的原始 WAV，包括尚未采用的重录版本。
        sha256.json：audio/ 中每个文件的 SHA-256。
        pending 表示未完成；skipped 表示发音人主动跳过；中断的临时音频不导出。
        CSV 对可能被表格软件解释为公式的文本添加单引号，原始文本保留在 JSON。
        本包包含发音人代号、方言背景和声音，请仅转交给获准接收的人。
        录音参数和简单质量提示不代表已通过科研质量审核。
      """.trimIndent())
    }
  }

  private fun ZipOutputStream.text(name: String, value: String) {
    putNextEntry(ZipEntry(name))
    write(value.toByteArray(Charsets.UTF_8))
    closeEntry()
  }

  private fun csv(value: String): String {
    val guarded = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
      value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
    return "\"${guarded.replace("\"", "\"\"")}\""
  }
}
