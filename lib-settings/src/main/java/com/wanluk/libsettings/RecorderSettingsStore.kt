package com.wanluk.libsettings

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RecordingMode { HOLD, CLICK }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class RecorderSettings(
  val alias: String = "",
  val dialect: String = "",
  val doNotAskAgain: Boolean = true,
  val hasSavedProfile: Boolean = false,
  val recordingMode: RecordingMode = RecordingMode.HOLD,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
  val shouldAsk: Boolean get() = !hasSavedProfile || !doNotAskAgain
}

/** Device-local preferences; a recording session keeps its own immutable profile snapshot. */
class RecorderSettingsStore(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences("recorder_settings", Context.MODE_PRIVATE)
  private val mutex = Mutex()
  private val mutableSettings = MutableStateFlow(RecorderSettings(
    alias = preferences.getString("alias", "").orEmpty(),
    dialect = preferences.getString("dialect", "").orEmpty(),
    doNotAskAgain = preferences.getBoolean("do_not_ask_again", true),
    hasSavedProfile = preferences.getBoolean("has_saved_profile", false),
    recordingMode = RecordingMode.entries.firstOrNull {
      it.name == preferences.getString("recording_mode", null)
    } ?: RecordingMode.HOLD,
    themeMode = ThemeMode.entries.firstOrNull {
      it.name == preferences.getString("theme_mode", null)
    } ?: ThemeMode.SYSTEM,
  ))
  val settings = mutableSettings.asStateFlow()

  suspend fun save(value: RecorderSettings) = withContext(Dispatchers.IO) {
    mutex.withLock {
      require(value.alias.length <= 80 && value.dialect.length <= 160) { "称呼最多 80 字符，方言最多 160 字符" }
      val saved = value.copy(alias = value.alias.trim(), dialect = value.dialect.trim(), hasSavedProfile = true,
        recordingMode = mutableSettings.value.recordingMode, themeMode = mutableSettings.value.themeMode)
      if (!preferences.edit().putString("alias", saved.alias).putString("dialect", saved.dialect)
          .putBoolean("do_not_ask_again", saved.doNotAskAgain).putBoolean("has_saved_profile", true).commit()) {
        throw IOException("录制者信息未能保存在本机，请重试")
      }
      mutableSettings.value = saved
    }
  }

  /** Changing an input preference must not mark the optional speaker profile as filled in. */
  suspend fun saveRecordingMode(mode: RecordingMode) = withContext(Dispatchers.IO) {
    mutex.withLock {
      if (!preferences.edit().putString("recording_mode", mode.name).commit()) {
        throw IOException("录音方式未能保存在本机，请重试")
      }
      mutableSettings.value = mutableSettings.value.copy(recordingMode = mode)
    }
  }

  /** Appearance is independent of the speaker profile and existing session snapshots. */
  suspend fun saveThemeMode(mode: ThemeMode) = withContext(Dispatchers.IO) {
    mutex.withLock {
      if (!preferences.edit().putString("theme_mode", mode.name).commit()) {
        throw IOException("外观设置未能保存在本机，请重试")
      }
      mutableSettings.value = mutableSettings.value.copy(themeMode = mode)
    }
  }
}
