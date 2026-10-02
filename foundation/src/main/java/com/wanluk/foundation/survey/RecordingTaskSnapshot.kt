package com.wanluk.foundation.survey

/** Full readable snapshots are chunked by items, independently of repetition counts and package identity. */
object RecordingTaskSnapshot {
  const val LIBRARY_PACKAGE_ID = "wanluk-full-word-library"
  const val MAX_LIBRARY_ITEMS = 20_000
  const val CHUNK_SIZE = 100
  private const val MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024

  fun chunks(task: SurveyPackage): List<String> {
    SurveyPackageCodec.validate(task)
    require(task.items.size in 1..MAX_LIBRARY_ITEMS) { "字库条目超过本地录制上限" }
    require(task.items.map { it.itemId }.distinct().size == task.items.size) { "字库条目 ID 重复" }
    var bytes = 0L
    return task.items.chunked(CHUNK_SIZE).map {
      SurveyPackageCodec.encode(task.copy(items = it)).also { json ->
        bytes += json.toByteArray(Charsets.UTF_8).size
        require(bytes <= MAX_SNAPSHOT_BYTES) { "录制快照超过 16 MB" }
      }
    }
  }

  fun restore(first: String, remaining: List<String>): SurveyPackage {
    val header = SurveyPackageCodec.decode(first)
    if (remaining.isEmpty()) return header
    require(remaining.size < MAX_LIBRARY_ITEMS / CHUNK_SIZE)
    require(first.toByteArray(Charsets.UTF_8).size + remaining.sumOf { it.toByteArray(Charsets.UTF_8).size } <= MAX_SNAPSHOT_BYTES)
    val items = header.items + remaining.flatMap { json ->
      val chunk = SurveyPackageCodec.decode(json)
      require(chunk.copy(items = header.items) == header) { "字库快照信息不一致" }
      chunk.items
    }
    require(items.size <= MAX_LIBRARY_ITEMS)
    require(items.map { it.itemId }.distinct().size == items.size)
    return header.copy(items = items).also(SurveyPackageCodec::validate)
  }

  /** Result exports contain the complete snapshot, not an importable custom survey package. */
  fun encodeResult(task: SurveyPackage): String {
    chunks(task)
    return SurveyPackageCodec.encode(task)
  }
}
