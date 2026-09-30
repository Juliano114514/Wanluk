package com.wanluk.foundation.survey

import com.google.gson.GsonBuilder

/** Local full-library sessions use bounded chunks; portable survey imports keep their 500-item limit. */
object RecordingTaskSnapshot {
  const val LIBRARY_PACKAGE_ID = "wanluk-full-word-library"
  const val MAX_LIBRARY_ITEMS = 20_000
  const val CHUNK_SIZE = 100
  private const val MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024

  fun chunks(task: SurveyPackage): List<String> {
    require(task.packageId == LIBRARY_PACKAGE_ID)
    require(task.items.size in 1..MAX_LIBRARY_ITEMS) { "字库条目超过本地录制上限" }
    require(task.items.all { it.repetitions == 1 })
    require(task.items.map { it.itemId }.distinct().size == task.items.size) { "字库条目 ID 重复" }
    return task.items.chunked(CHUNK_SIZE).map { SurveyPackageCodec.encode(task.copy(items = it)) }
      .also { chunks ->
        require(chunks.sumOf { it.toByteArray(Charsets.UTF_8).size } <= MAX_SNAPSHOT_BYTES) { "字库快照过大" }
      }
  }

  fun restore(first: String, remaining: List<String>): SurveyPackage {
    val header = SurveyPackageCodec.decode(first)
    if (remaining.isEmpty()) return header
    require(header.packageId == LIBRARY_PACKAGE_ID && remaining.size < MAX_LIBRARY_ITEMS / CHUNK_SIZE)
    require(first.toByteArray(Charsets.UTF_8).size + remaining.sumOf { it.toByteArray(Charsets.UTF_8).size } <= MAX_SNAPSHOT_BYTES)
    val items = header.items + remaining.flatMap { json ->
      val chunk = SurveyPackageCodec.decode(json)
      require(chunk.copy(items = header.items) == header) { "字库快照信息不一致" }
      chunk.items
    }
    require(items.size <= MAX_LIBRARY_ITEMS && items.all { it.repetitions == 1 })
    require(items.map { it.itemId }.distinct().size == items.size)
    return header.copy(items = items)
  }

  /** Result exports contain the complete snapshot, not an importable custom survey package. */
  fun encodeResult(task: SurveyPackage): String {
    if (task.items.size <= SurveyPackage.MAX_ITEMS) return SurveyPackageCodec.encode(task)
    chunks(task)
    return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(task).also {
      require(it.toByteArray(Charsets.UTF_8).size <= MAX_SNAPSHOT_BYTES) { "字库快照过大" }
    }
  }
}
