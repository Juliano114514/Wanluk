package com.wanluk.libsurveytransfer

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Keeps scanner implementation and its intent protocol out of the app. */
class SurveyQrScanContract : ActivityResultContract<Unit, String?>() {
  private val delegate = ScanContract()

  override fun createIntent(context: Context, input: Unit): Intent = delegate.createIntent(context,
    ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
      .setPrompt("对准韵录方案二维码；多张方案请逐张扫描")
      .setBeepEnabled(false).setBarcodeImageEnabled(false).setOrientationLocked(false))

  override fun parseResult(resultCode: Int, intent: Intent?): String? = delegate.parseResult(resultCode, intent).contents
}
