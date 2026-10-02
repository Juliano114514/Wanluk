package com.wanluk.foundation.survey

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.wanluk.foundation.survey.proto.SurveyTransferProto as Proto
import java.security.MessageDigest

data class TransferCustomItem(val position: Int, val id: String, val text: String,
  val type: String, val phonology: ChinesePhonology? = null)
data class TransferExtension(val position: Int, val extInfo: ItemExtInfo)
data class SurveyTransferDocument(
  val catalogSha256: String,
  val packageId: String,
  val revision: Int,
  val title: String,
  val description: String,
  val languageTag: String,
  val dialect: String,
  val recordingProfile: String,
  val defaults: SurveyDefaults,
  val builtinIds: List<Int>,
  val customItems: List<TransferCustomItem>,
  val extensions: List<TransferExtension>,
  val planType: RecordingPlanType = RecordingPlanType.legacy(packageId, defaults.displayType),
) {
  val itemCount: Int get() = builtinIds.size + customItems.size
}

/** Wire format only. Builtin lookup and persistence belong to the repository layer. */
object SurveyTransferCodec {
  const val VERSION = 2
  const val PASSAGE_VERSION = 3
  const val MAX_BYTES = 2 * 1024 * 1024
  private val digestPattern = Regex("[0-9a-f]{64}")
  private val customPattern = Regex("ex_[A-Za-z0-9_-]{1,77}")

  fun version(document: SurveyTransferDocument): Int = if (document.planType == RecordingPlanType.PASSAGE) PASSAGE_VERSION else VERSION

  fun encode(document: SurveyTransferDocument): ByteArray {
    validate(document)
    val plan = Proto.Plan.newBuilder().setCatalogSha256(ByteString.copyFrom(hexBytes(document.catalogSha256)))
      .setPackageId(document.packageId).setRevision(document.revision).setTitle(document.title)
      .setDescription(document.description).setLanguageTag(document.languageTag).setDialect(document.dialect)
      .setRecordingProfile(document.recordingProfile).setDefaults(defaults(document.defaults)).setPlanType(document.planType.value)
    var previous = 0
    document.builtinIds.forEach { id -> plan.addBuiltinIdDeltas(id - previous); previous = id }
    document.customItems.sortedBy { it.position }.forEach { item ->
      val custom = Proto.CustomItem.newBuilder().setPosition(item.position).setId(item.id).setText(item.text).setType(item.type)
      item.phonology?.let { custom.setPhonology(phonology(it)) }
      plan.addCustomItems(custom)
    }
    document.extensions.sortedBy { it.position }.forEach { item ->
      plan.addExtensions(Proto.ItemExtension.newBuilder().setPosition(item.position).setExtInfo(extension(item.extInfo)))
    }
    val message = plan.build()
    require(message.serializedSize <= MAX_BYTES) { "方案超过 2 MB 交换上限" }
    val payload = message.toByteArray()
    val envelope = Proto.Envelope.newBuilder().setVersion(version(document)).setPayload(ByteString.copyFrom(payload))
      .setSha256(ByteString.copyFrom(sha256(payload))).build()
    require(envelope.serializedSize <= MAX_BYTES) { "方案超过 2 MB 交换上限" }
    return envelope.toByteArray()
  }

  fun decode(bytes: ByteArray, expectedVersion: Int? = null): SurveyTransferDocument {
    require(bytes.size in 1..MAX_BYTES) { "方案文件为空或超过 2 MB" }
    try {
      val envelope = Proto.Envelope.parseFrom(input(bytes))
      require(envelope.version in VERSION..PASSAGE_VERSION) { "方案版本不受支持，请更新韵录" }
      require(expectedVersion == null || envelope.version == expectedVersion) { "二维码标记与方案版本不一致" }
      val payload = envelope.payload.toByteArray()
      require(envelope.sha256.size() == 32 && MessageDigest.isEqual(envelope.sha256.toByteArray(), sha256(payload))) {
        "方案完整性校验失败"
      }
      val plan = Proto.Plan.parseFrom(input(payload))
      require(plan.catalogSha256.size() == 32 && plan.hasDefaults()) { "方案缺少字表指纹或默认设置" }
      require(envelope.version != PASSAGE_VERSION || plan.hasPlanType()) { "文段方案缺少分类" }
      val planType = if (plan.hasPlanType()) RecordingPlanType.fromValue(plan.planType)
        else RecordingPlanType.legacy(plan.packageId, plan.defaults.displayType)
      require(envelope.version != VERSION || planType != RecordingPlanType.PASSAGE) { "文段须使用 v3 方案，请重新导出" }
      require(plan.builtinIdDeltasCount + plan.customItemsCount in 1..SurveyPackage.MAX_ITEMS &&
        plan.extensionsCount <= SurveyPackage.MAX_ITEMS) { "方案字目数量超过上限" }
      var previous = 0L
      val ids = plan.builtinIdDeltasList.map { delta ->
        previous += delta.toLong()
        require(previous in 1..Int.MAX_VALUE.toLong()) { "内置字目 ID 不合法" }
        previous.toInt()
      }
      return SurveyTransferDocument(hex(plan.catalogSha256.toByteArray()), plan.packageId, plan.revision,
        plan.title, plan.description, plan.languageTag, plan.dialect, plan.recordingProfile,
        SurveyDefaults(plan.defaults.displayType, if (plan.defaults.hasInstruction()) plan.defaults.instruction else null,
          plan.defaults.repetitions, plan.defaults.allowSkip), ids,
        plan.customItemsList.map { TransferCustomItem(it.position, it.id, it.text, it.type,
          if (it.hasPhonology()) readPhonology(it.phonology) else null) },
        plan.extensionsList.map {
          require(it.hasExtInfo()) { "字目例外为空" }
          val ext = it.extInfo
          TransferExtension(it.position, ItemExtInfo(if (ext.hasDisplayType()) ext.displayType else null,
            if (ext.hasInstruction()) ext.instruction else null, if (ext.hasRepetitions()) ext.repetitions else null,
            if (ext.hasAllowSkip()) ext.allowSkip else null, ext.note))
        }, planType).also(::validate)
    } catch (error: com.google.protobuf.InvalidProtocolBufferException) {
      throw IllegalArgumentException("不是完整的新版 Protobuf 方案，请重新导出", error)
    }
  }

  fun validate(document: SurveyTransferDocument) {
    require(digestPattern.matches(document.catalogSha256)) { "字表指纹格式不正确" }
    require(document.itemCount in 1..SurveyPackage.MAX_ITEMS) { "方案须包含 1–20000 字目" }
    require(document.builtinIds.all { it > 0 }) { "内置字目 ID 不合法" }
    val customPositions = document.customItems.map { it.position }
    require(customPositions.distinct().size == customPositions.size && customPositions.all { it in 0 until document.itemCount }) { "自定义字目位置不正确" }
    require(document.customItems.all { customPattern.matches(it.id) } &&
      document.customItems.map { it.id }.distinct().size == document.customItems.size) { "自定义字目须使用独立 ex_ ID" }
    val extPositions = document.extensions.map { it.position }
    require(extPositions.distinct().size == extPositions.size && extPositions.all { it in 0 until document.itemCount }) { "字目例外位置不正确" }
    // Reuse the local semantic validator without resolving builtin content or allocating the full library.
    val customs = document.customItems.associateBy { it.position }
    val extensions = document.extensions.associate { it.position to it.extInfo }
    require(document.extensions.none { it.position in customs && it.extInfo.displayType != null }) {
      "自定义字目使用自身题型，不能覆盖内置呈现方式"
    }
    val sample = SurveyPackage(packageId = document.packageId, revision = document.revision, title = document.title,
      description = document.description, languageTag = document.languageTag, dialect = document.dialect,
      recordingProfile = document.recordingProfile, defaults = document.defaults,
      planType = document.planType,
      items = (0 until document.itemCount).map { position ->
        val custom = customs[position]
        SurveyItem(itemId = "position_$position", type = custom?.type ?: document.defaults.displayType,
          text = custom?.text ?: "内置字目", instruction = document.defaults.instruction.orEmpty(),
          repetitions = document.defaults.repetitions, allowSkip = document.defaults.allowSkip,
          phonology = custom?.phonology, extInfo = extensions[position])
      })
    SurveyPackageCodec.validate(sample)
  }

  fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
  fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
  private fun hexBytes(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
  private fun input(bytes: ByteArray): CodedInputStream = CodedInputStream.newInstance(bytes).apply {
    setSizeLimit(MAX_BYTES); setRecursionLimit(16)
  }
  private fun defaults(value: SurveyDefaults): Proto.Defaults = Proto.Defaults.newBuilder()
    .setDisplayType(value.displayType).setRepetitions(value.repetitions).setAllowSkip(value.allowSkip)
    .apply { value.instruction?.let { setInstruction(it) } }.build()
  private fun extension(value: ItemExtInfo): Proto.ExtInfo = Proto.ExtInfo.newBuilder().setNote(value.note).apply {
    value.displayType?.let { setDisplayType(it) }; value.instruction?.let { setInstruction(it) }
    value.repetitions?.let { setRepetitions(it) }; value.allowSkip?.let { setAllowSkip(it) }
  }.build()
  private fun phonology(value: ChinesePhonology): Proto.Phonology = Proto.Phonology.newBuilder()
    .setSheng(value.sheng).setHu(value.hu).setYun(value.yun).setDiao(value.diao).setShe(value.she)
    .setZu(value.zu).setSourceId(value.sourceId).apply { value.deng?.let { setDeng(it) } }.build()
  private fun readPhonology(value: Proto.Phonology) = ChinesePhonology(value.sheng, value.hu,
    if (value.hasDeng()) value.deng else null, value.yun, value.diao, value.she, value.zu, value.sourceId)
}
