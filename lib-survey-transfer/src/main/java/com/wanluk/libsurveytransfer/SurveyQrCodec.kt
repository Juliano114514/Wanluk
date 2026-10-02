package com.wanluk.libsurveytransfer

import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.wanluk.foundation.survey.SurveyTransferCodec
import com.wanluk.foundation.survey.SurveyTransferDocument
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** A QR always contains one complete plan. null means it must be shared in a ZIP. */
object SurveyQrCodec {
  private val prefixes = listOf("WANLUK2:", "WANLUK3:")
  const val MAX_TEXT_LENGTH = 2331
  private val payloadPattern = Regex("[A-Za-z0-9_-]+")

  fun encode(document: SurveyTransferDocument): String? {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { it.write(SurveyTransferCodec.encode(document)) }
    val text = "WANLUK${SurveyTransferCodec.version(document)}:" + Base64.encodeToString(output.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    if (text.length > MAX_TEXT_LENGTH) return null
    return try {
      QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
      text
    } catch (_: WriterException) { null }
  }

  fun check(text: String) {
    val prefix = requireNotNull(prefixes.firstOrNull(text::startsWith)) { "仅支持新版方案二维码，请更新韵录或重新导出" }
    require(text.length <= MAX_TEXT_LENGTH && payloadPattern.matches(text.removePrefix(prefix))) { "方案二维码格式不正确" }
  }

  fun decode(text: String): SurveyTransferDocument {
    check(text)
    val prefix = prefixes.first(text::startsWith)
    val bytes = try { Base64.decode(text.removePrefix(prefix), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
      catch (error: IllegalArgumentException) { throw IllegalArgumentException("方案二维码编码不正确", error) }
    val expanded = try {
      GZIPInputStream(bytes.inputStream()).use { SurveyTransferFiles.readBounded(it, SurveyTransferCodec.MAX_BYTES) }
    } catch (error: java.io.IOException) { throw IllegalArgumentException("方案二维码损坏，请重新导出", error) }
    return SurveyTransferCodec.decode(expanded, if (prefix == "WANLUK3:") 3 else 2)
  }
}
