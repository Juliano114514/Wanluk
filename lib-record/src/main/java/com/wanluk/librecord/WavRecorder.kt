package com.wanluk.librecord

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class RecordingMeter(val durationMs: Long = 0, val peak: Float = 0f)
data class WavResult(
  val file: File,
  val durationMs: Long,
  val sampleRate: Int,
  val audioSource: String,
  val inputDevice: String,
  val peak: Double,
  val rms: Double,
  val clippedFraction: Double,
  val warning: String,
  val appliedGain: Double = 1.0,
)

/** Foreground, single-owner recorder. Final files appear only after header and data are flushed. */
class WavRecorder(private val context: Context) {
  private val lock = Mutex()
  private val mutableMeter = MutableStateFlow(RecordingMeter())
  val meter = mutableMeter.asStateFlow()

  suspend fun record(file: File, stop: AtomicBoolean, maxSeconds: Int = 120): WavResult = withContext(Dispatchers.IO) {
    check(lock.tryLock()) { "已有录音正在进行" }
    var recorder: AudioRecord? = null
    try {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        throw SecurityException("请先允许麦克风权限")
      }
      require(maxSeconds in 1..600)
      check(!file.exists()) { "录音文件已存在，不能覆盖" }
      val requiredSpace = maxOf(32L * 1024 * 1024, SAMPLE_RATE.toLong() * 2 * maxSeconds + 44 + 8L * 1024 * 1024)
      check(requireNotNull(file.parentFile).usableSpace >= requiredSpace) { "可用空间不足，需至少 ${(requiredSpace + 1024 * 1024 - 1) / (1024 * 1024)} MB，请先导出并整理文件" }
      val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
      check(minimum > 0) { "此设备不支持 48 kHz 单声道 PCM 录制" }
      val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
      val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
      val sourceName = if (unprocessed) "UNPROCESSED" else "VOICE_RECOGNITION"
      val capture = AudioRecord.Builder().setAudioSource(source).setAudioFormat(
        AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
      ).setBufferSizeInBytes(maxOf(minimum * 2, 8192)).build()
      recorder = capture
      check(capture.state == AudioRecord.STATE_INITIALIZED && capture.sampleRate == SAMPLE_RATE) {
        "设备无法按调查包要求初始化录音"
      }
      val partial = File(file.parentFile, "${file.name}.part")
      check(partial.createNewFile()) { "临时录音已存在" }
      var samples = 0L
      var peak = 0
      var squaredSum = 0.0
      var clipped = 0L
      var inputPeak = 0
      var appliedGain = 1.0
      var routedDevice = "unknown"
      var routeChanged = false
      val shorts = ShortArray(2048)
      val bytes = ByteArray(shorts.size * 2)
      var lastMeterUpdate = 0L
      var lastDataAt = SystemClock.elapsedRealtime()
      val limit = SAMPLE_RATE.toLong() * maxSeconds
      mutableMeter.value = RecordingMeter()
      RandomAccessFile(partial, "rw").use { output ->
        output.write(ByteArray(44))
        capture.startRecording()
        check(capture.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未开始采集，请检查是否被其他应用占用" }
        while (!stop.get() && samples < limit) {
          currentCoroutineContext().ensureActive()
          val count = capture.read(shorts, 0, minOf(shorts.size.toLong(), limit - samples).toInt(), AudioRecord.READ_NON_BLOCKING)
          check(count >= 0) { "录音输入中断（$count），请重新录制" }
          val now = SystemClock.elapsedRealtime()
          if (count == 0) {
            check(now - lastDataAt < 5000) { "麦克风连续 5 秒没有返回数据" }
            delay(10)
            continue
          }
          lastDataAt = now
          val device = capture.routedDevice?.let { "type=${it.type}; ${it.productName}" }?.take(160) ?: "unknown"
          if (routedDevice == "unknown") routedDevice = device else if (device != routedDevice) routeChanged = true
          var framePeak = 0
          for (index in 0 until count) {
            val sample = shorts[index].toInt()
            val amplitude = abs(sample)
            framePeak = maxOf(framePeak, amplitude)
            squaredSum += sample.toDouble() * sample
            if (amplitude >= 32760) clipped++
            bytes[index * 2] = (sample and 255).toByte()
            bytes[index * 2 + 1] = ((sample shr 8) and 255).toByte()
          }
          output.write(bytes, 0, count * 2)
          samples += count
          peak = maxOf(peak, framePeak)
          if (now - lastMeterUpdate >= 100) {
            mutableMeter.value = RecordingMeter(samples * 1000 / SAMPLE_RATE, framePeak / 32768f)
            lastMeterUpdate = now
          }
        }
        check(samples > 0) { "没有录到声音数据，请重试" }
        capture.stop()
        inputPeak = peak
        // One gain for the whole take preserves relative amplitudes and leaves peak headroom.
        appliedGain = if (peak == 0) 1.0 else (TARGET_PEAK.toDouble() / peak).coerceIn(1.0, MAX_GAIN)
        if (appliedGain > 1.0) {
          peak = 0
          squaredSum = 0.0
          var processed = 0L
          while (processed < samples) {
            currentCoroutineContext().ensureActive()
            val count = minOf(shorts.size.toLong(), samples - processed).toInt()
            val offset = 44 + processed * 2
            output.seek(offset)
            output.readFully(bytes, 0, count * 2)
            for (index in 0 until count) {
              val sample = ((bytes[index * 2].toInt() and 255) or
                ((bytes[index * 2 + 1].toInt() and 255) shl 8)).toShort().toInt()
              val amplified = (sample * appliedGain).roundToInt()
              peak = maxOf(peak, abs(amplified))
              squaredSum += amplified.toDouble() * amplified
              bytes[index * 2] = (amplified and 255).toByte()
              bytes[index * 2 + 1] = ((amplified shr 8) and 255).toByte()
            }
            output.seek(offset)
            output.write(bytes, 0, count * 2)
            processed += count
          }
        }
        output.seek(0)
        output.write(header(samples * 2))
        output.fd.sync()
      }
      check(partial.renameTo(file)) { "录音写入完成，但文件保存失败，请检查存储空间" }
      val duration = samples * 1000 / SAMPLE_RATE
      val rms = sqrt(squaredSum / samples) / 32768.0
      val warnings = buildList {
        if (duration < 500) add("录音短于 0.5 秒，请回听确认")
        if (inputPeak / 32768.0 < 0.01) add("采集声音很小或没有明显声音，建议检查麦克风后重录")
        if (clipped.toDouble() / samples > 0.001) add("检测到可能的削波，建议稍远离麦克风后重录")
        if (routeChanged) add("录制中输入设备发生变化，请回听确认")
        if (samples >= limit) add("达到单条 ${maxSeconds} 秒上限，已自动保存")
      }.joinToString("；")
      WavResult(file, duration, capture.sampleRate, sourceName, routedDevice, peak / 32768.0,
        rms, clipped.toDouble() / samples, warnings, appliedGain)
    } finally {
      try {
        recorder?.let {
          try { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
          finally { it.release() }
        }
      } finally {
        mutableMeter.value = RecordingMeter()
        lock.unlock()
      }
    }
  }

  private fun header(dataBytes: Long): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray(Charsets.US_ASCII)); putInt((dataBytes + 36).toInt())
    put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(1)
    putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
    put("data".toByteArray(Charsets.US_ASCII)); putInt(dataBytes.toInt())
  }.array()

  companion object {
    const val SAMPLE_RATE = 48000
    private const val TARGET_PEAK = 29000
    private const val MAX_GAIN = 4.0
  }
}
