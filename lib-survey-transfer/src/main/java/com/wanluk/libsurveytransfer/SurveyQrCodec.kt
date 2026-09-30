package com.wanluk.libsurveytransfer

import android.util.Base64
import com.google.gson.JsonParser
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.foundation.survey.SurveyPackageCodec
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Offline, lossless transport. Version, checksum and bounded parts belong to transport, not task schema. */
object SurveyQrCodec {
  private const val PREFIX = "WANLUK1"
  private const val PART_LENGTH = 1200
  const val MAX_PARTS = 32
  private val checksumPattern = Regex("[0-9a-f]{64}")
  private val payloadPattern = Regex("[A-Za-z0-9_-]+")

  fun encode(task: SurveyPackage): List<String> {
    val compact = JsonParser.parseString(SurveyPackageCodec.encode(task)).toString()
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { it.write(compact.toByteArray(Charsets.UTF_8)) }
    val compressed = output.toByteArray()
    val encoded = Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    require(encoded.length <= PART_LENGTH * MAX_PARTS) { "这个方案内容较多，请通过 JSON 文件分享" }
    val chunks = encoded.chunked(PART_LENGTH)
    val digest = checksum(compressed)
    return chunks.mapIndexed { index, chunk -> "$PREFIX:$digest:${chunks.size}:${index + 1}:$chunk" }
  }

  internal data class Part(val checksum: String, val total: Int, val index: Int, val data: String)

  internal fun parse(text: String): Part {
    require(text.length <= PART_LENGTH + 100) { "二维码内容过长，请使用韵录生成的方案二维码" }
    val fields = text.split(':', limit = 5)
    require(fields.size == 5 && fields[0] == PREFIX) { "这不是支持的韵录方案二维码" }
    val total = fields[2].toIntOrNull() ?: 0
    val index = fields[3].toIntOrNull() ?: 0
    require(checksumPattern.matches(fields[1]) && total in 1..MAX_PARTS && index in 1..total &&
      fields[4].length in 1..PART_LENGTH && payloadPattern.matches(fields[4])) { "方案二维码不完整或格式不正确" }
    require(index == total || fields[4].length == PART_LENGTH) { "方案二维码分片长度不正确" }
    return Part(fields[1], total, index, fields[4])
  }

  internal fun decode(parts: List<Part>): SurveyPackage {
    val first = parts.first()
    require(parts.size == first.total && parts.map { it.index }.toSet() == (1..first.total).toSet())
    require(parts.all { it.checksum == first.checksum && it.total == first.total })
    val encoded = parts.sortedBy { it.index }.joinToString("") { it.data }
    val compressed = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    require(checksum(compressed) == first.checksum) { "方案校验未通过，请重新扫描整组二维码" }
    // Reuse the strict UTF-8/schema parser and its 2 MiB limit while inflating, before allocating a JSON tree.
    return GZIPInputStream(compressed.inputStream()).use(SurveyPackageCodec::read)
  }

  private fun checksum(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

data class SurveyQrProgress(val received: Int = 0, val total: Int = 0, val task: SurveyPackage? = null)

/** Accepts parts out of order, ignores exact duplicates, and never combines different packages. */
class SurveyQrCollector {
  private val parts = sortedMapOf<Int, SurveyQrCodec.Part>()
  val progress: SurveyQrProgress get() = SurveyQrProgress(parts.size, parts.values.firstOrNull()?.total ?: 0)

  fun accept(text: String): SurveyQrProgress {
    val part = SurveyQrCodec.parse(text)
    parts.values.firstOrNull()?.let {
      require(it.checksum == part.checksum && it.total == part.total) { "这张码属于另一个方案，请继续扫描当前方案，或先重新开始" }
    }
    require(parts[part.index] == null || parts[part.index] == part) { "同一张码的内容不一致，请重新开始" }
    parts[part.index] = part
    val task = if (parts.size == part.total) try {
      SurveyQrCodec.decode(parts.values.toList())
    } catch (error: Exception) {
      parts.clear()
      throw IllegalArgumentException("方案二维码校验失败，请重新开始；也可以导入 JSON 文件", error)
    } else null
    return SurveyQrProgress(parts.size, part.total, task)
  }

  fun reset() { parts.clear() }
}
