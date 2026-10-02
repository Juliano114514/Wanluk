package com.wanluk.foundation.survey

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Export labels never participate in locating or renaming the original UUID audio files. */
object ResultExportNames {
  const val MAX_NAME_BYTES = 180
  private val forbidden = Regex("[\\p{Cc}\\p{Cf}<>:\"/\\\\|?*]")
  private val whitespace = Regex("\\s+")
  private val deviceName = Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")

  /** Run against the complete retained history before selecting steps or versions. */
  fun attach(bundle: SessionExport): SessionExport {
    val steps = bundle.steps.associateBy { it.position }
    val width = maxOf(4, bundle.steps.maxOfOrNull { it.position + 1 }?.toString()?.length ?: 4)
    val versions = mutableMapOf<Int, Int>()
    val used = mutableSetOf<String>()
    val names = bundle.takes.filter { it.state == "saved" }
      .sortedWith(compareBy<ExportTake> { it.position }.thenBy { it.createdAt }.thenBy { it.takeId }).associate { take ->
        val step = requireNotNull(steps[take.position]) { "录音没有对应题目" }
        val version = (versions[take.position] ?: 0) + 1
        versions[take.position] = version
        val content = if (bundle.task.planType == RecordingPlanType.PASSAGE)
          component(bundle.task.title, 36) + "-" + component(step.text, 36) else component(step.text, 48)
        val stem = "${(step.position + 1).toString().padStart(width, '0')}_${content}_" +
          "${component(bundle.speakerAlias, 36)}_${component(bundle.dialect, 24)}_第${step.repetition}次_版本$version"
        take.takeId to "audio/${unique(stem, ".wav", used)}"
      }
    require(names.size == bundle.takes.count { it.state == "saved" }) { "录音版本编号重复" }
    return bundle.copy(exportSchemaVersion = 3, takes = bundle.takes.map { it.copy(audioFile = names[it.takeId].orEmpty()) })
  }

  fun sessionFile(bundle: SessionExport): String = unique(sessionStem(bundle), ".zip", mutableSetOf())
  fun batchFile(count: Int, exportedAt: Long): String = "韵录_${count}组录制_${date(exportedAt)}.zip"
  fun sessionDirectory(bundle: SessionExport, used: MutableSet<String>): String = "sessions/${unique(sessionStem(bundle), "", used)}/"

  fun isSafeAudioFile(path: String): Boolean {
    if (!path.startsWith("audio/")) return false
    val name = path.removePrefix("audio/")
    return name.endsWith(".wav") && name.length > 4 && !name.startsWith('.') &&
      !forbidden.containsMatchIn(name) && name.trimEnd(' ', '.') == name &&
      !deviceName.matches(name) && name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES
  }

  private fun sessionStem(bundle: SessionExport): String =
    "${component(bundle.title, 54)}_${component(bundle.speakerAlias, 36)}_${component(bundle.dialect, 24)}_" +
      "${date(bundle.createdAt)}_${component(bundle.sessionId.take(8), 16)}"

  private fun date(time: Long): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(time))

  private fun component(value: String, maxBytes: Int): String {
    val cleaned = whitespace.replace(forbidden.replace(Normalizer.normalize(value, Normalizer.Form.NFC), "_"), "_")
      .trim(' ', '.', '_').ifBlank { "未填写" }
    return truncate(cleaned, maxBytes).trimEnd(' ', '.', '_').ifBlank { "未填写" }
  }

  private fun unique(stem: String, extension: String, used: MutableSet<String>): String {
    var number = 1
    while (true) {
      val suffix = if (number == 1) extension else "_$number$extension"
      val name = truncate(stem, MAX_NAME_BYTES - suffix.toByteArray(Charsets.UTF_8).size).trimEnd(' ', '.') + suffix
      if (used.add(name.lowercase(Locale.ROOT))) return name
      number++
    }
  }

  private fun truncate(value: String, maxBytes: Int): String {
    val output = StringBuilder()
    var bytes = 0
    var index = 0
    while (index < value.length) {
      val code = Character.codePointAt(value, index)
      val part = if (code in 0xD800..0xDFFF) "_" else String(Character.toChars(code))
      val size = part.toByteArray(Charsets.UTF_8).size
      if (bytes + size > maxBytes) break
      output.append(part)
      bytes += size
      index += Character.charCount(code)
    }
    return output.toString()
  }
}
