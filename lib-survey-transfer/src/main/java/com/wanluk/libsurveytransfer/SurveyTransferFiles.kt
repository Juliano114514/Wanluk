package com.wanluk.libsurveytransfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.wanluk.foundation.survey.SurveyTransferCodec
import com.wanluk.foundation.survey.SurveyTransferDocument
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class TransferFileResult(val name: String, val document: SurveyTransferDocument? = null, val error: String? = null)

/** Bounded ZIP reading: entry names are never used as filesystem destinations. */
object SurveyTransferFiles {
  const val MAX_PLANS = 32
  const val MAX_ENTRIES = 128
  const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
  const val MAX_EXPANDED_BYTES = 64 * 1024 * 1024
  private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024

  fun read(resolver: ContentResolver, uri: Uri): List<TransferFileResult> {
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0)?.take(160) else null
    } ?: "所选文件"
    val bytes = requireNotNull(resolver.openInputStream(uri)) { "无法打开所选文件" }.use { readBounded(it, MAX_ARCHIVE_BYTES) }
    return if (isZip(bytes) || name.endsWith(".zip", ignoreCase = true)) readZip(bytes) else listOf(decode(name, bytes))
  }

  private fun decode(name: String, bytes: ByteArray): TransferFileResult = try {
    val document = if (isImage(bytes)) SurveyQrCodec.decode(SurveyQrImages.readBytes(bytes))
      else SurveyTransferCodec.decode(bytes)
    TransferFileResult(name, document)
  } catch (error: Exception) {
    if (error is kotlinx.coroutines.CancellationException) throw error
    TransferFileResult(name, error = (if (error is IllegalArgumentException) error.message else "文件损坏或格式不支持，请重新导出新版方案")?.take(240))
  }

  private fun readZip(bytes: ByteArray): List<TransferFileResult> {
    val directory = directory(bytes)
    var count = 0
    var expanded = 0L
    val results = mutableListOf<TransferFileResult>()
    val identities = mutableSetOf<Pair<String, Int>>()
    try {
      ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          require(++count <= MAX_ENTRIES) { "ZIP 最多包含 128 个文件条目" }
          val declared = directory.getOrNull(count - 1)
          require(declared != null && entry.name == declared.name) { "ZIP 文件目录与内容不一致" }
          val name = entry.name.replace('\\', '/')
          val segments = name.removeSuffix("/").split('/')
          require(name.length <= 240 && !name.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(name) &&
            segments.size <= 8 && segments.all { it.isNotEmpty() && it != "." && it != ".." }) { "ZIP 包含不支持的路径" }
          val data = readBounded(zip, MAX_IMAGE_BYTES) { amount ->
            expanded += amount
            require(expanded <= MAX_EXPANDED_BYTES) { "ZIP 展开内容超过 64 MB" }
          }
          require(data.size.toLong() == declared.size && entry.crc == declared.crc) { "ZIP 文件完整性校验失败" }
          require(!isZip(data) && !name.endsWith(".zip", ignoreCase = true)) { "不支持嵌套 ZIP" }
          if (!entry.isDirectory) {
            require(isImage(data) || data.size <= SurveyTransferCodec.MAX_BYTES) { "ZIP 中的方案文件超过 2 MB" }
            val result = decode(name, data)
            result.document?.let {
              identities += it.packageId to it.revision
              require(identities.size <= MAX_PLANS) { "一个 ZIP 最多包含 32 个独立方案" }
            }
            results += result
          }
          zip.closeEntry()
        }
      }
    } catch (error: java.io.IOException) { throw IllegalArgumentException("ZIP 不完整或损坏，未导入任何方案", error) }
    require(count == directory.size) { "ZIP 不完整，未导入任何方案" }
    require(results.isNotEmpty()) { "ZIP 中没有方案文件或二维码图片" }
    return results
  }

  private data class ArchiveEntry(val name: String, val size: Long, val crc: Long, val localOffset: Int)

  /** ZipInputStream alone accepts archives truncated before the central directory. */
  private fun directory(bytes: ByteArray): List<ArchiveEntry> {
    fun u16(offset: Int): Int {
      require(offset >= 0 && offset <= bytes.size - 2) { "ZIP 文件目录不完整" }
      return (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    }
    fun u32(offset: Int): Long = u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)
    val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 22 - 65535)).firstOrNull {
      u32(it) == 0x06054b50L && it + 22 + u16(it + 20) == bytes.size
    } ?: throw IllegalArgumentException("ZIP 不完整，未导入任何方案")
    val count = u16(end + 10)
    require(u16(end + 4) == 0 && u16(end + 6) == 0 && u16(end + 8) == count && count in 1..MAX_ENTRIES) {
      "ZIP 须为完整的单卷文件，最多 128 个条目"
    }
    val start = u32(end + 16)
    require(start + u32(end + 12) == end.toLong()) { "ZIP 文件目录不完整" }
    var cursor = start.toInt()
    var expanded = 0L
    val entries = (0 until count).map {
      require(cursor >= 0 && cursor <= end - 46 && u32(cursor) == 0x02014b50L) { "ZIP 文件目录损坏" }
      val flags = u16(cursor + 8)
      val method = u16(cursor + 10)
      val crc = u32(cursor + 16)
      val compressed = u32(cursor + 20)
      val size = u32(cursor + 24)
      val nameLength = u16(cursor + 28)
      val next = cursor + 46 + nameLength + u16(cursor + 30) + u16(cursor + 32)
      val local = u32(cursor + 42)
      require(next <= end && nameLength > 0 && u16(cursor + 34) == 0 && (flags and 1) == 0 && method in listOf(0, 8) &&
        compressed <= MAX_ARCHIVE_BYTES && local <= start - 30) { "ZIP 目录、压缩方式或分卷不受支持" }
      require(size <= MAX_IMAGE_BYTES) { "ZIP 单个文件超过 12 MB" }
      expanded += size
      require(expanded <= MAX_EXPANDED_BYTES) { "ZIP 展开内容超过 64 MB" }
      val offset = local.toInt()
      require(u32(offset) == 0x04034b50L && u16(offset + 6) == flags && u16(offset + 8) == method) { "ZIP 文件头不一致" }
      val localNameLength = u16(offset + 26)
      val localData = offset.toLong() + 30 + localNameLength + u16(offset + 28)
      require(localNameLength == nameLength && localData + compressed <= start &&
        bytes.copyOfRange(offset + 30, offset + 30 + localNameLength)
          .contentEquals(bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength))) { "ZIP 文件名或内容范围不一致" }
      val name = bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength).toString(Charsets.UTF_8)
      cursor = next
      ArchiveEntry(name, size, crc, offset)
    }
    require(cursor == end && entries.map { it.localOffset }.distinct().size == entries.size) { "ZIP 文件目录不一致" }
    return entries.sortedBy { it.localOffset }
  }

  fun writeZip(output: OutputStream, documents: List<SurveyTransferDocument>) {
    require(documents.size in 1..MAX_PLANS) { "请选择 1–32 个方案" }
    require(documents.map { it.packageId to it.revision }.distinct().size == documents.size) { "方案重复" }
    var written = 0L
    val bounded = object : OutputStream() {
      override fun write(value: Int) {
        require(++written <= MAX_ARCHIVE_BYTES) { "ZIP 超过 32 MB 导出上限" }; output.write(value)
      }
      override fun write(buffer: ByteArray, offset: Int, length: Int) {
        written += length
        require(written <= MAX_ARCHIVE_BYTES) { "ZIP 超过 32 MB 导出上限" }; output.write(buffer, offset, length)
      }
      override fun flush() = output.flush()
    }
    ZipOutputStream(bounded).use { zip ->
      documents.forEach {
        val data = SurveyTransferCodec.encode(it)
        zip.putNextEntry(ZipEntry("surveys/${it.packageId}-v${it.revision}.pb"))
        zip.write(data); zip.closeEntry()
      }
    }
  }

  internal fun readBounded(input: InputStream, maximum: Int, onBytes: (Int) -> Unit = {}): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      onBytes(count)
      require(output.size() + count <= maximum) { "文件内容超过允许上限" }
      output.write(buffer, 0, count)
    }
    return output.toByteArray()
  }

  private fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
    ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) || (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()))

  private fun isImage(bytes: ByteArray): Boolean = bytes.size >= 12 && (
    (bytes[0] == 0x89.toByte() && bytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray())) ||
    (bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) ||
    bytes.copyOfRange(0, 3).contentEquals("GIF".toByteArray()) ||
    (bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())))
}
