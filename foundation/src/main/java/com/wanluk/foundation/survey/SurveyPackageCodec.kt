package com.wanluk.foundation.survey

import com.google.gson.GsonBuilder
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Explicit bounded decoding prevents Gson reflection from bypassing Kotlin null guarantees. */
object SurveyPackageCodec {
  const val MAX_BYTES = 16 * 1024 * 1024
  private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
  private val legacyGson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
    .addSerializationExclusionStrategy(object : ExclusionStrategy {
      override fun shouldSkipField(field: FieldAttributes) = field.declaringClass == SurveyPackage::class.java && field.name == "planType"
      override fun shouldSkipClass(type: Class<*>) = false
    }).create()
  private val identifier = Regex("[A-Za-z0-9_-]{1,80}")
  private val packageKeys = setOf("schemaVersion", "packageId", "revision", "title", "description",
    "languageTag", "dialect", "recordingProfile", "items", "defaults", "planType")
  private val itemKeys = setOf("itemId", "type", "text", "instruction", "repetitions", "allowSkip", "phonology", "wordId", "extInfo")
  private val phonologyKeys = setOf("sheng", "hu", "deng", "yun", "diao", "she", "zu", "sourceId")
  private val defaultKeys = setOf("displayType", "instruction", "repetitions", "allowSkip")
  private val extKeys = defaultKeys + "note"

  fun read(input: InputStream): SurveyPackage {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      require(output.size() + count <= MAX_BYTES) { "本地调查包超过 16 MB 上限" }
      output.write(buffer, 0, count)
    }
    val decoder = Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    return decode(decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString())
  }

  fun decode(json: String): SurveyPackage {
    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "本地调查包超过 16 MB 上限" }
    // Streaming preflight also rejects duplicate keys and excessive nesting before building a tree.
    JsonReader(StringReader(json.removePrefix("\uFEFF"))).use { reader ->
      reader.strictness = Strictness.STRICT
      scan(reader, 0)
      require(reader.peek() == JsonToken.END_DOCUMENT) { "JSON 后存在多余内容" }
    }
    val root = JsonParser.parseString(json.removePrefix("\uFEFF")).obj("调查包")
    root.keys(packageKeys)
    val version = root.int("schemaVersion")
    require(version in 1..3) { "不支持本地调查包版本" }
    if (version < 3) require(!root.has("planType")) { "旧调查包不能包含方案分类" }
    if (version == 1) require(!root.has("defaults")) { "旧调查包不能包含 defaults" }
    val defaults = if (version == 1) null else requireNotNull(root.get("defaults")?.takeUnless { it.isJsonNull }) { "缺少 defaults" }.obj("defaults").let {
      it.keys(defaultKeys)
      SurveyDefaults(it.text("displayType"), it.optionalText("instruction"), it.int("repetitions"), it.boolean("allowSkip", true))
    }
    val planType = if (version == 3) RecordingPlanType.fromValue(root.text("planType"))
      else RecordingPlanType.legacy(root.text("packageId"), defaults?.displayType)
    val array = root.get("items")
    require(array != null && array.isJsonArray) { "items 必须是数组" }
    require(array.asJsonArray.size() in 1..SurveyPackage.MAX_ITEMS) { "题目数量须为 1–20000" }
    val items = array.asJsonArray.mapIndexed { index, value ->
      val item = value.obj("第 ${index + 1} 题")
      item.keys(itemKeys)
      if (version == 1) require(!item.has("wordId") && !item.has("extInfo")) { "旧调查包包含新版字目字段" }
      val annotation = item.get("phonology")?.takeUnless { it.isJsonNull }?.obj("phonology")
      annotation?.keys(phonologyKeys)
      SurveyItem(
        itemId = item.text("itemId"), type = item.text("type"), text = item.text("text"),
        instruction = item.text("instruction", ""), repetitions = item.int("repetitions", 1),
        allowSkip = item.boolean("allowSkip", true),
        phonology = annotation?.let {
          ChinesePhonology(it.text("sheng", ""), it.text("hu", ""),
            it.get("deng")?.takeUnless { field -> field.isJsonNull }?.let { _ -> it.int("deng") },
            it.text("yun", ""), it.text("diao", ""), it.text("she", ""),
            it.text("zu", ""), it.text("sourceId", ""))
        },
        wordId = item.optionalText("wordId"),
        extInfo = item.get("extInfo")?.takeUnless { it.isJsonNull }?.obj("extInfo")?.let {
          it.keys(extKeys)
          ItemExtInfo(it.optionalText("displayType"), it.optionalText("instruction"),
            it.optionalInt("repetitions"), it.optionalBoolean("allowSkip"), it.text("note", "")).compact()
        },
      )
    }
    return SurveyPackage(version, root.text("packageId"), root.int("revision"), root.text("title"),
      root.text("description", ""), root.text("languageTag", "zh"), root.text("dialect", ""),
      root.text("recordingProfile"), items, defaults, planType).also(::validate)
  }

  fun encode(task: SurveyPackage): String {
    validate(task)
    return encodeDraft(task)
  }

  /** Allows unfinished drafts in the local editor; validation still runs on apply/save. */
  fun encodeDraft(task: SurveyPackage): String {
    val output = object : ByteArrayOutputStream() {
      override fun write(value: Int) {
        require(size() < MAX_BYTES) { "本地调查包超过 16 MB 上限" }
        super.write(value)
      }
      override fun write(buffer: ByteArray, offset: Int, length: Int) {
        require(size().toLong() + length <= MAX_BYTES) { "本地调查包超过 16 MB 上限" }
        super.write(buffer, offset, length)
      }
    }
    OutputStreamWriter(output, Charsets.UTF_8).use { (if (task.schemaVersion < 3) legacyGson else gson).toJson(task, it) }
    return output.toString(Charsets.UTF_8.name())
  }

  fun validate(task: SurveyPackage) {
    require(task.schemaVersion in 1..3) { "不支持本地调查包版本" }
    require((task.schemaVersion == 1) == (task.defaults == null)) { "本地方案默认设置与版本不一致" }
    if (task.schemaVersion < 3) require(task.planType == RecordingPlanType.legacy(task.packageId, task.defaults?.displayType)) {
      "旧方案不能变更分类，请先升级为新版方案"
    }
    require(identifier.matches(task.packageId)) { "packageId 只能包含字母、数字、下划线和短横线，最多 80 位" }
    require(task.revision in 1..1_000_000) { "revision 须为 1–1000000" }
    require(task.recordingProfile == SurveyPackage.RECORDING_PROFILE) { "不支持的录音预设" }
    checkText(task.title, "调查包名称", 120, required = true)
    checkText(task.description, "调查说明", 4000)
    checkText(task.languageTag, "语言标记", 80, required = true)
    checkText(task.dialect, "方言说明", 160)
    require(task.items.size in 1..SurveyPackage.MAX_ITEMS) { "题目数量须为 1–20000" }
    task.defaults?.let {
      require(it.displayType in listOf("character", "word")) { "内置字目呈现方式不受支持" }
      it.instruction?.let { value -> checkText(value, "方案录制提示", 2000) }
      require(it.repetitions in 1..5) { "每题须录制 1–5 次" }
    }
    require(task.items.map { it.itemId }.distinct().size == task.items.size) { "题目 ID 不能重复" }
    val customIds = task.items.mapNotNull { it.wordId?.takeIf { id -> id.startsWith("ex_") } }
    require(customIds.distinct().size == customIds.size) { "自定义字目须各有独立 ex_ ID" }
    task.items.forEachIndexed { index, item ->
      require(identifier.matches(item.itemId)) { "第 ${index + 1} 题 ID 不合法" }
      require(SurveyItemType.entries.any { it.value == item.type }) { "第 ${index + 1} 题的题型不受支持" }
      checkText(item.text, "第 ${index + 1} 题内容", task.planType.maxTextLength, required = true)
      checkText(item.instruction, "第 ${index + 1} 题提示", 2000)
      require(item.repetitions in 1..5) { "每题须录制 1–5 次" }
      item.wordId?.let {
        require((it.startsWith("ex_") && it.length > 3 && identifier.matches(it)) || (it.toIntOrNull()?.let { id -> id > 0 && id.toString() == it } == true)) { "字目来源 ID 不合法" }
      }
      item.extInfo?.let {
        require(it.displayType == null || it.displayType in listOf("character", "word")) { "字目呈现方式不受支持" }
        require(it.repetitions == null || it.repetitions in 1..5) { "每题须录制 1–5 次" }
        it.instruction?.let { value -> checkText(value, "字目录制提示", 2000) }
        checkText(it.note, "字目补充说明", 2000)
      }
      item.phonology?.let { p ->
        require(p.deng == null || p.deng in 1..4) { "等只能为 1–4 或 null（未定／不适用）" }
        listOf(p.sheng, p.hu, p.yun, p.diao, p.she, p.zu, p.sourceId).forEach {
          checkText(it, "音韵标注", 120)
        }
      }
    }
    require(task.totalSteps <= SurveyPackage.MAX_STEPS) { "含重复次数的录制步骤最多 100000 条" }
  }

  /** Stream list metadata without retaining the full task tree or item strings. */
  fun summary(json: String): SurveyPackageSummary {
    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
    return summary(StringReader(json))
  }

  fun summary(parts: List<String>): SurveyPackageSummary = summary(object : Reader() {
    private var part = 0
    private var offset = 0
    override fun read(buffer: CharArray, start: Int, length: Int): Int {
      if (length == 0) return 0
      while (part < parts.size && offset == parts[part].length) { part++; offset = 0 }
      if (part == parts.size) return -1
      val count = minOf(length, parts[part].length - offset)
      parts[part].toCharArray(buffer, start, offset, offset + count)
      offset += count
      return count
    }
    override fun close() = Unit
  })

  private fun summary(input: Reader): SurveyPackageSummary {
    var id = ""; var revision = 0; var title = ""; var description = ""; var dialect = ""
    var count = 0; var defaultRepetitions: Int? = null
    val repetitionsByItem = mutableListOf<Pair<Int, Int?>>()
    JsonReader(input).use { reader ->
      reader.strictness = Strictness.STRICT
      reader.beginObject()
      while (reader.hasNext()) when (reader.nextName()) {
        "packageId" -> id = reader.nextString()
        "revision" -> revision = reader.nextInt()
        "title" -> title = reader.nextString()
        "description" -> description = reader.nextString()
        "dialect" -> dialect = reader.nextString()
        "defaults" -> {
          if (reader.peek() == JsonToken.NULL) reader.nextNull() else {
            reader.beginObject()
            while (reader.hasNext()) if (reader.nextName() == "repetitions") defaultRepetitions = reader.nextInt() else reader.skipValue()
            reader.endObject()
          }
        }
        "items" -> {
          reader.beginArray()
          while (reader.hasNext()) {
            require(++count <= SurveyPackage.MAX_ITEMS)
            var repetitions = 1
            var override: Int? = null
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
              "repetitions" -> repetitions = reader.nextInt()
              "extInfo" -> {
                if (reader.peek() == JsonToken.NULL) reader.nextNull() else {
                  reader.beginObject()
                  while (reader.hasNext()) {
                    if (reader.nextName() == "repetitions" && reader.peek() != JsonToken.NULL) override = reader.nextInt() else reader.skipValue()
                  }
                  reader.endObject()
                }
              }
              else -> reader.skipValue()
            }
            reader.endObject()
            require(repetitions in 1..5)
            repetitionsByItem += repetitions to override
          }
          reader.endArray()
        }
        else -> reader.skipValue()
      }
      reader.endObject()
      require(reader.peek() == JsonToken.END_DOCUMENT)
    }
    val steps = repetitionsByItem.sumOf { (base, override) -> (override ?: defaultRepetitions ?: base).also { require(it in 1..5) } }
    require(steps <= SurveyPackage.MAX_STEPS)
    return SurveyPackageSummary(id, revision, title, count, steps, description, dialect)
  }

  private fun checkText(value: String, label: String, max: Int, required: Boolean = false) {
    require(value.length <= max && (!required || value.isNotBlank())) { "$label 不能为空或超过 $max 字符" }
    require(value.none { it.code < 32 && it !in "\n\r\t" }) { "$label 包含不支持的控制字符" }
  }

  private fun JsonElement.obj(label: String): JsonObject {
    require(isJsonObject) { "$label 必须是对象" }
    return asJsonObject
  }

  private fun JsonObject.keys(allowed: Set<String>) {
    require(keySet().all { it in allowed }) { "存在不支持的字段：${keySet().minus(allowed).joinToString().take(120)}" }
  }

  private fun JsonObject.text(name: String, default: String? = null): String {
    val value = get(name) ?: return requireNotNull(default) { "缺少 $name" }
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$name 必须是字符串" }
    return value.asString
  }

  private fun JsonObject.optionalText(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.let { text(name) }

  private fun JsonObject.optionalInt(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.let { int(name) }

  private fun JsonObject.optionalBoolean(name: String): Boolean? =
    get(name)?.takeUnless { it.isJsonNull }?.let { boolean(name, false) }

  private fun JsonObject.int(name: String, default: Int? = null): Int {
    val value = get(name) ?: return requireNotNull(default) { "缺少 $name" }
    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$name 必须是整数" }
    return try { value.asBigDecimal.intValueExact() } catch (_: ArithmeticException) {
      throw IllegalArgumentException("$name 必须是有效整数")
    }
  }

  private fun JsonObject.boolean(name: String, default: Boolean): Boolean {
    val value = get(name) ?: return default
    require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$name 必须是布尔值" }
    return value.asBoolean
  }

  private fun scan(reader: JsonReader, depth: Int) {
    require(depth <= 12) { "JSON 嵌套过深" }
    when (reader.peek()) {
      JsonToken.BEGIN_OBJECT -> {
        reader.beginObject()
        val names = mutableSetOf<String>()
        while (reader.hasNext()) {
          require(names.add(reader.nextName())) { "JSON 包含重复字段" }
          scan(reader, depth + 1)
        }
        reader.endObject()
      }
      JsonToken.BEGIN_ARRAY -> {
        reader.beginArray()
        var size = 0
        while (reader.hasNext()) {
          require(++size <= SurveyPackage.MAX_ITEMS) { "JSON 数组超过 20000 项" }
          scan(reader, depth + 1)
        }
        reader.endArray()
      }
      JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
      JsonToken.BOOLEAN -> reader.nextBoolean()
      JsonToken.NULL -> reader.nextNull()
      else -> throw IllegalArgumentException("JSON 格式不正确")
    }
  }
}
