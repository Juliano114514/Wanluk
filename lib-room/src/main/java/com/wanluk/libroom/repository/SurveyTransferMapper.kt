package com.wanluk.libroom.repository

import com.wanluk.foundation.survey.*
import com.wanluk.libroom.entity.WordCaseEntity
import java.util.UUID

data class BuiltinWordCatalog(val sha256: String, val words: Map<Int, WordCaseEntity>)

/** Fixed asset lookup; device-local row IDs never enter the portable format. */
class SurveyTransferMapper(private val catalog: BuiltinWordCatalog) {
  fun editable(task: SurveyPackage): SurveyPackage {
    if (task.defaults != null) return resolve(task)
    val display = task.items.map { it.type }.filter { it in listOf("character", "word") }
      .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "character"
    val commonInstruction = task.items.map { it.instruction }.distinct().singleOrNull()?.takeUnless {
      task.items.all { item -> item.instruction == (matchingBuiltin(item)?.prompt(item.type) ?: CUSTOM_PROMPT) }
    }
    val default = SurveyDefaults(display, commonInstruction,
      task.items.groupingBy { it.repetitions }.eachCount().maxByOrNull { it.value }?.key ?: 1,
      task.items.count { it.allowSkip } >= task.items.count { !it.allowSkip })
    val items = task.items.map { item ->
      val builtin = matchingBuiltin(item)
      val inherited = default.instruction ?: builtin?.prompt(item.type) ?: CUSTOM_PROMPT
      item.copy(wordId = builtin?.sourceNumber()?.toString() ?: customId(task, item), extInfo = ItemExtInfo(
        displayType = item.type.takeIf { builtin != null && it != default.displayType },
        instruction = item.instruction.takeIf { it != inherited },
        repetitions = item.repetitions.takeIf { it != default.repetitions },
        allowSkip = item.allowSkip.takeIf { it != default.allowSkip },
        note = item.extInfo?.note.orEmpty()).compact())
    }
    return resolve(task.copy(schemaVersion = 3, defaults = default, items = items))
  }

  /** Materialize inheritance for readable storage. Frozen recording snapshots never call this. */
  fun resolve(task: SurveyPackage): SurveyPackage {
    val default = requireNotNull(task.defaults)
    return task.copy(schemaVersion = 3, items = task.items.map { original ->
      val builtin = matchingBuiltin(original)
      var item = original
      if (original.wordId?.toIntOrNull() != null && builtin == null) {
        item = original.copy(wordId = customId(task, original), extInfo = (original.extInfo ?: ItemExtInfo()).copy(
          displayType = null, instruction = original.instruction,
          repetitions = original.repetitions, allowSkip = original.allowSkip))
      } else if (original.wordId == null) {
        item = original.copy(wordId = builtin?.sourceNumber()?.toString() ?: customId(task, original))
      }
      val ext = item.extInfo?.let { if (builtin == null) it.copy(displayType = null) else it }?.compact()
      val type = if (builtin != null) ext?.displayType ?: default.displayType else item.type
      item.copy(type = type, text = builtin?.text(type) ?: item.text,
        instruction = ext?.instruction ?: default.instruction ?: builtin?.prompt(type) ?: CUSTOM_PROMPT,
        repetitions = ext?.repetitions ?: default.repetitions, allowSkip = ext?.allowSkip ?: default.allowSkip,
        phonology = builtin?.annotation() ?: item.phonology, extInfo = ext?.compact())
    })
  }

  fun document(task: SurveyPackage): SurveyTransferDocument {
    val resolved = editable(task)
    SurveyPackageCodec.validate(resolved)
    RecordingTaskSnapshot.chunks(resolved)
    val builtin = mutableListOf<Int>()
    val customs = mutableListOf<TransferCustomItem>()
    val extensions = mutableListOf<TransferExtension>()
    resolved.items.forEachIndexed { position, item ->
      val id = requireNotNull(item.wordId)
      val source = id.toIntOrNull()
      if (source != null) builtin += source
      else customs += TransferCustomItem(position, id, item.text, item.type, item.phonology)
      item.extInfo?.compact()?.let { extensions += TransferExtension(position, it) }
    }
    return SurveyTransferDocument(catalog.sha256, resolved.packageId, resolved.revision, resolved.title,
      resolved.description, resolved.languageTag, resolved.dialect, resolved.recordingProfile,
      requireNotNull(resolved.defaults), builtin, customs, extensions, resolved.planType).also(SurveyTransferCodec::validate)
  }

  fun restore(document: SurveyTransferDocument): SurveyPackage {
    SurveyTransferCodec.validate(document)
    require(document.catalogSha256 == catalog.sha256) { "字表版本不同，请使用相同字表的韵录重新导出" }
    val customs = document.customItems.associateBy { it.position }
    val extensions = document.extensions.associate { it.position to it.extInfo }
    val ids = document.builtinIds.iterator()
    val default = document.defaults
    val items = (0 until document.itemCount).map { position ->
      val custom = customs[position]
      val ext = extensions[position]
      val builtin = if (custom == null) {
        val id = ids.next()
        requireNotNull(catalog.words[id]) { "内置字目 ID $id 不存在" }
      } else null
      val type = custom?.type ?: ext?.displayType ?: default.displayType
      SurveyItem(itemId = custom?.id ?: "i_${UUID.nameUUIDFromBytes("${document.packageId}\u0000${document.revision}\u0000$position".toByteArray(Charsets.UTF_8))}",
        type = type, text = custom?.text ?: requireNotNull(builtin).text(type),
        instruction = ext?.instruction ?: default.instruction ?: builtin?.prompt(type) ?: CUSTOM_PROMPT,
        repetitions = ext?.repetitions ?: default.repetitions, allowSkip = ext?.allowSkip ?: default.allowSkip,
        phonology = custom?.phonology ?: builtin?.annotation(), wordId = custom?.id ?: builtin?.sourceNumber()?.toString(),
        extInfo = ext?.compact())
    }
    return SurveyPackage(packageId = document.packageId, revision = document.revision, title = document.title,
      description = document.description, languageTag = document.languageTag, dialect = document.dialect,
      recordingProfile = document.recordingProfile, items = items, defaults = default, planType = document.planType).also {
      SurveyPackageCodec.validate(it)
      RecordingTaskSnapshot.chunks(it)
    }
  }

  fun equivalent(left: SurveyPackage, right: SurveyPackage): Boolean = semantic(left) == semantic(right)

  private fun semantic(task: SurveyPackage): SurveyPackage = editable(task).let { resolved ->
    resolved.copy(items = resolved.items.mapIndexed { index, item ->
      item.copy(itemId = "position_$index", extInfo = item.extInfo?.compact())
    })
  }

  private fun matchingBuiltin(item: SurveyItem): WordCaseEntity? {
    if (item.wordId?.startsWith("ex_") == true) return null
    val id = item.wordId?.toIntOrNull() ?: item.phonology?.sourceId?.takeIf { it.startsWith("builtin:wordcase:") }
      ?.removePrefix("builtin:wordcase:")?.toIntOrNull() ?: return null
    val word = catalog.words[id] ?: return null
    return word.takeIf { item.type in listOf("character", "word") && item.text == it.text(item.type) && item.phonology == it.annotation() }
  }

  private fun customId(task: SurveyPackage, item: SurveyItem): String =
    item.wordId?.takeIf { it.startsWith("ex_") && it.length > 3 } ?: item.itemId.takeIf { it.startsWith("ex_") && it.length > 3 } ?:
      "ex_${UUID.nameUUIDFromBytes("${task.packageId}\u0000${item.itemId}".toByteArray(Charsets.UTF_8))}"

  companion object {
    const val CUSTOM_PROMPT = "请用你平时的家乡话说出屏幕上的内容。"
    fun WordCaseEntity.sourceNumber(): Int? = sourceId?.removePrefix("builtin:wordcase:")?.toIntOrNull()
    fun WordCaseEntity.text(type: String): String = if (type == "character") coreChar else phrases?.takeIf { it.isNotBlank() } ?: coreChar
    fun WordCaseEntity.prompt(type: String): String = if (type == "character")
      "请用家乡话读出这个字。语义提示：${phrases.orEmpty()}。不确定时可以跳过。" else
      "请用家乡话读整个词语。括号中的简体写法仅作提示，无需重复读。"
    fun WordCaseEntity.annotation() = ChinesePhonology(sheng, hu, deng.takeIf { it in 1..4 }, yun, diao, she, zu.orEmpty(), sourceId.orEmpty())
    fun WordEntry.sourceNumber(): Int? = sourceId?.removePrefix("builtin:wordcase:")?.toIntOrNull()
    fun WordEntry.text(type: String): String = if (type == "character") coreChar else phrases?.takeIf { it.isNotBlank() } ?: coreChar
    fun WordEntry.prompt(type: String): String = if (type == "character")
      "请用家乡话读出这个字。语义提示：${phrases.orEmpty()}。不确定时可以跳过。" else
      "请用家乡话读整个词语。括号中的简体写法仅作提示，无需重复读。"
    fun WordEntry.annotation() = ChinesePhonology(sheng, hu, deng.takeIf { it in 1..4 }, yun, diao, she, zu.orEmpty(), sourceId.orEmpty())
  }
}
