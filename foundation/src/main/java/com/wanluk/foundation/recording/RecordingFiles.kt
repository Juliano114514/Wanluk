package com.wanluk.foundation.recording

import android.content.Context
import java.io.File
import java.util.UUID

/** Paths are derived exclusively from application-generated UUIDs, never task text. */
class RecordingFiles(context: Context) {
  private val root = File(context.filesDir, "recordings").canonicalFile

  fun take(sessionId: String, takeId: String): File {
    require(UUID.fromString(sessionId).toString() == sessionId) { "录制会话 ID 无效" }
    require(UUID.fromString(takeId).toString() == takeId) { "录音 ID 无效" }
    val directory = File(root, sessionId)
    check(directory.canonicalFile == directory.absoluteFile && directory.parentFile == root) { "录音路径越界" }
    check(directory.isDirectory || directory.mkdirs()) { "无法创建录音目录" }
    val file = File(directory, "$takeId.wav").canonicalFile
    require(directory.canonicalFile == directory.absoluteFile && file == File(directory, "$takeId.wav").absoluteFile && file.parentFile == directory) { "录音路径越界" }
    return file
  }

  /** Remove only the named take and its unfinished fragment within the session directory. */
  fun deleteTake(sessionId: String, takeId: String) {
    require(UUID.fromString(sessionId).toString() == sessionId && UUID.fromString(takeId).toString() == takeId) { "录音 ID 无效" }
    val directory = File(root, sessionId)
    check(directory.canonicalFile == directory.absoluteFile && directory.parentFile == root) { "录音路径越界" }
    listOf("$takeId.wav", "$takeId.wav.part").forEach { name ->
      val file = File(directory, name)
      check(file.canonicalFile == file.absoluteFile && file.parentFile == directory) { "录音路径越界" }
      check(!file.exists() || file.isFile && file.delete()) { "部分音频未能清理，请重试" }
    }
  }

  /** Only remove a session's flat private directory; never traverse links or nested directories. */
  fun deleteSession(sessionId: String) {
    require(UUID.fromString(sessionId).toString() == sessionId) { "录制会话 ID 无效" }
    val directory = File(root, sessionId)
    check(directory.canonicalFile == directory.absoluteFile && directory.parentFile == root) { "录音路径越界" }
    if (!directory.exists()) return
    val entries = checkNotNull(directory.listFiles()) { "无法读取录音目录" }
    check(entries.all { it.canonicalFile == it.absoluteFile && it.isFile }) { "录音目录包含异常文件" }
    entries.forEach { check(it.delete() || !it.exists()) { "部分录音文件未能删除，请重试" } }
    check(directory.delete() || !directory.exists()) { "录音目录未能删除，请重试" }
  }
}
