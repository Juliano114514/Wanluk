package com.wanluk.libsurveytransfer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream

object SurveyQrImages {
  fun render(text: String): Bitmap {
    SurveyQrCodec.check(text)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 800, 800,
      mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
    val pixels = IntArray(matrix.width * matrix.height) { index ->
      if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
  }

  fun read(resolver: ContentResolver, uri: Uri): String {
    val bytes = ByteArrayOutputStream()
    requireNotNull(resolver.openInputStream(uri)) { "无法读取二维码图片" }.use { input ->
      val buffer = ByteArray(8192)
      while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(bytes.size() + count <= 12 * 1024 * 1024) { "请选择小于 12 MB 的二维码图片" }
        bytes.write(buffer, 0, count)
      }
    }
    return readBytes(bytes.toByteArray())
  }

  fun readBytes(data: ByteArray): String {
    require(data.size <= 12 * 1024 * 1024) { "请选择小于 12 MB 的二维码图片" }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    require(options.outWidth in 1..20000 && options.outHeight in 1..20000) { "无法识别图片尺寸" }
    options.inSampleSize = 1
    while (options.outWidth / options.inSampleSize > 1800 || options.outHeight / options.inSampleSize > 1800) {
      options.inSampleSize *= 2
    }
    options.inJustDecodeBounds = false
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size, options)) { "无法打开二维码图片" }
    try {
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
      return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
      } catch (_: com.google.zxing.ReaderException) {
        throw IllegalArgumentException("没有识别到清晰的二维码，请选择原图或使用相机扫描")
      }
    } finally { bitmap.recycle() }
  }
}
