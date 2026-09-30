package com.wanluk.foundation.survey

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Explicit bounded decoding prevents Gson reflection from bypassing Kotlin null guarantees. */
object SurveyPackageCodec {
  const val MAX_BYTES = 2 * 1024 * 1024
  private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
  private val identifier = Regex("[A-Za-z0-9_-]{1,80}")
  private val packageKeys = setOf("schemaVersion", "packageId", "revision", "title", "description",
    "languageTag", "dialect", "recordingProfile", "items")
  private val itemKeys = setOf("itemId", "type", "text", "instruction", "repetitions", "allowSkip", "phonology")
  private val phonologyKeys = setOf("sheng", "hu", "deng", "yun", "diao", "she", "zu", "sourceId")

  fun read(input: InputStream): SurveyPackage {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      require(output.size() + count <= MAX_BYTES) { "调查包超过 2 MB 上限" }
      output.write(buffer, 0, count)
    }
    val decoder = Charsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    return decode(decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString())
  }

  fun decode(json: String): SurveyPackage {
    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "调查包超过 2 MB 上限" }
    // Streaming preflight also rejects duplicate keys and excessive nesting before building a tree.
    JsonReader(StringReader(json.removePrefix("\uFEFF"))).use { reader ->
      reader.strictness = Strictness.STRICT
      scan(reader, 0)
      require(reader.peek() == JsonToken.END_DOCUMENT) { "JSON 后存在多余内容" }
    }
    val root = JsonParser.parseString(json.removePrefix("\uFEFF")).obj("调查包")
    root.keys(packageKeys)
    val version = root.int("schemaVersion")
    require(version == 1) { "不支持 schemaVersion=$version，请使用协议 v1" }
    val array = root.get("items")
    require(array != null && array.isJsonArray) { "items 必须是数组" }
    require(array.asJsonArray.size() in 1..SurveyPackage.MAX_ITEMS) { "题目数量须为 1–500" }
    val items = array.asJsonArray.mapIndexed { index, value ->
      val item = value.obj("第 ${index + 1} 题")
      item.keys(itemKeys)
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
      )
    }
    return SurveyPackage(version, root.text("packageId"), root.int("revision"), root.text("title"),
      root.text("description", ""), root.text("languageTag", "zh"), root.text("dialect", ""),
      root.text("recordingProfile"), items).also(::validate)
  }

  fun encode(task: SurveyPackage): String {
    validate(task)
    return gson.toJson(task).also {
      require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "调查包超过 2 MB 上限" }
    }
  }

  fun validate(task: SurveyPackage) {
    require(task.schemaVersion == 1) { "仅支持调查包协议 v1" }
    require(identifier.matches(task.packageId)) { "packageId 只能包含字母、数字、下划线和短横线，最多 80 位" }
    require(task.revision in 1..1_000_000) { "revision 须为 1–1000000" }
    require(task.recordingProfile == SurveyPackage.RECORDING_PROFILE) { "不支持的录音预设" }
    checkText(task.title, "调查包名称", 120, required = true)
    checkText(task.description, "调查说明", 4000)
    checkText(task.languageTag, "语言标记", 80, required = true)
    checkText(task.dialect, "方言说明", 160)
    require(task.items.size in 1..SurveyPackage.MAX_ITEMS) { "题目数量须为 1–500" }
    require(task.items.map { it.itemId }.distinct().size == task.items.size) { "题目 ID 不能重复" }
    task.items.forEachIndexed { index, item ->
      require(identifier.matches(item.itemId)) { "第 ${index + 1} 题 ID 不合法" }
      require(SurveyItemType.entries.any { it.value == item.type }) { "第 ${index + 1} 题的题型不受支持" }
      checkText(item.text, "第 ${index + 1} 题内容", 500, required = true)
      checkText(item.instruction, "第 ${index + 1} 题提示", 2000)
      require(item.repetitions in 1..5) { "每题须录制 1–5 次" }
      item.phonology?.let { p ->
        require(p.deng == null || p.deng in 1..4) { "等只能为 1–4 或 null（未定／不适用）" }
        listOf(p.sheng, p.hu, p.yun, p.diao, p.she, p.zu, p.sourceId).forEach {
          checkText(it, "音韵标注", 120)
        }
      }
    }
    require(task.totalSteps <= SurveyPackage.MAX_STEPS) { "含重复次数的录制步骤最多 1500 条" }
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
    require(keySet().all { it in allowed }) { "存在协议 v1 不支持的字段：${keySet().minus(allowed).joinToString().take(120)}" }
  }

  private fun JsonObject.text(name: String, default: String? = null): String {
    val value = get(name) ?: return requireNotNull(default) { "缺少 $name" }
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$name 必须是字符串" }
    return value.asString
  }

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
          require(++size <= SurveyPackage.MAX_ITEMS) { "JSON 数组超过 500 项" }
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
