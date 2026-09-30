package com.wanluk.librecord

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Used on the main thread; never overlaps a microphone capture. */
class RecordingPlayer {
  private var player: MediaPlayer? = null
  private val mutablePlaying = MutableStateFlow(false)
  val playing = mutablePlaying.asStateFlow()

  fun play(file: File, onError: () -> Unit) {
    stop()
    check(file.isFile) { "录音文件不存在" }
    val media = MediaPlayer()
    player = media
    try {
      media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
      media.setDataSource(file.path)
      media.setOnPreparedListener { if (player === it) { it.start(); mutablePlaying.value = true } }
      media.setOnCompletionListener { stop() }
      media.setOnErrorListener { _, _, _ -> stop(); onError(); true }
      media.prepareAsync()
      mutablePlaying.value = true
    } catch (error: Exception) {
      stop()
      throw error
    }
  }

  fun stop() {
    player?.release()
    player = null
    mutablePlaying.value = false
  }
}
