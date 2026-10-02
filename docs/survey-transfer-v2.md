# 方案交换协议 v2/v3

源结构为 [survey_transfer.proto](../foundation/src/main/proto/survey_transfer.proto)。只用于调查方案交换；录音成果 ZIP、本地方案和历史录制快照继续保留完整可读内容。

## 字目身份与字表一致性

- 内置身份来自固定 `WordCaseList.csv` 的 `id` 列，持久来源为 `builtin:wordcase:<id>`。不使用设备 Room 自增主键，也不重排或复用 CSV ID。
- `catalog_sha256` 是整个 CSV 原始字节的 SHA-256，包含 BOM 与换行。严格相等才允许导入；字表任何字节变化均形成不同版本，即使方案全部为自定义题目也检查指纹。
- 当前资产 SHA-256：`dbdf19b57ff0b82998ba44c6ab02d5d318dd1eb15fd87a8dc07b39f574f75594`。10292 行中的 ID 6357 无字目，实际可引用 10291 条；不存在的 ID 拒绝。
- 自定义身份为独立 `ex_` ID，新建或修改内置文字时生成 UUID。自定义内容仅保存在方案与录制快照，不写入 `word_cases`。
- 本地 `itemId` 表示题目实例，`wordId` 表示字目来源。同一内置来源可多次出现，次序和重复录制次数保持不变。内置题目实例 ID 不传输，接收方按方案编号、版本与位置生成；自定义实例使用其 `ex_` ID。

## 二进制结构

`.pb` 是 `Envelope` 的原始 Protobuf 字节：

| 字段 | 含义 |
|---|---|
| version | 字目／词汇为2，文段为3 |
| payload | 序列化 `Plan` |
| sha256 | payload 的 32 字节 SHA-256 |

负载包含方案编号、版本、名称、说明、语言、方言、固定录音预设、字表指纹和下述字目结构：

- `defaults`：方案公用录制规则。
- `builtin_id_deltas`：内置 CSV ID 的有序差值列表，`packed sint32`。从 0 开始累加；自定义位置不消耗差值。
- `custom_items`：位置、`ex_` ID、文字、题型和可选汉语音韵标注。
- `extensions`：位置及 `ext_info`，仅携带该题的例外或补充说明。
- `plan_type`（字段13）：可选字符串，character／word／passage。v3必须填写，v2禁止声明passage；未知取值拒绝。旧v2缺省按defaults.display_type或已知词汇预设推断，未确定时使用character。

例如字目次序 `[12, ex_A, 7, 7]`：内置差值为 `[12, -5, 0]`，自定义条目位于 1。位置从 0 开始，重复内置 ID 和负差值均允许；自定义 ID、自定义位置、例外位置各自唯一且不越界。

哈希用于完整性和字表一致性校验，不作来源身份认证。解析仅接受明确数据类型，不执行表达式、脚本或网络请求。

## 公用设置与 ext_info

| 设置 | defaults | ext_info |
|---|---|---|
| display_type | 内置呈现方式：character / word | 存在时覆盖；仅用于内置引用 |
| instruction | 缺省使用协议定义的题型提示，空字符串隐藏提示 | 缺省继承，空字符串明确隐藏 |
| repetitions | 1–5 | 缺省继承；存在时覆盖为 1–5 |
| allow_skip | 公用是否允许跳过 | 缺省继承；显式 false 与缺省不同 |
| note | 无 | 字目补充说明，编辑预览与录制页均显示 |

`instruction` 和例外字段使用 Protobuf `optional` 保留字段存在性。内置单字默认提示包含当前字表的组词；词语与自定义内容使用各自的系统提示。自定义题型由其自身 `type` 指定，可为 character、word 或 sentence。

本地v3 JSON增加必填planType，并保留 `defaults`、`extInfo`、`wordId` 及已解析的 `text`、`instruction`、`repetitions`、`allowSkip`、`phonology`。继续读取历史v1/v2，按原版本编码历史快照。开始录制时冻结完整内容；后续编辑方案或更新字表不会重新解释旧会话。

## 一个二维码

编码顺序：`Envelope → GZIP → URL-safe Base64（无 padding）`；字目／词汇前缀为`WANLUK2:`，文段为`WANLUK3:`。前缀与Envelope版本必须一致。

- 使用 M 纠错级别，完整文本最多 2331 ASCII 字节；同时调用 ZXing 编码确认能够生成 QR。
- 成功时显示并可保存一个完整 PNG；接收方一次扫描或一次选图即可进入确认预览。
- 超出容量时导出一个 ZIP，保留全部内容，不切成多张码、不删字目或例外。
- 不接受旧 `WANLUK1` 分片，也不接受 JSON 外部交换。已存本机的旧方案可重新导出。

二维码理论容量以[官方版本容量表](https://www.qrcode.com/en/about/versionPage/versionPage31_40.html)为依据。压缩收益取决于选择顺序、自定义内容与例外数量，不能保证任意方案均可放入二维码。

## ZIP 与确认导入

应用导出的方案 ZIP 使用 `surveys/<packageId>-v<revision>.pb`。批量导出最多选择 32 个独立方案，接收方一次选取 ZIP。

ZIP 输入逐项识别 Protobuf 或新版二维码 PNG/JPEG/GIF/WebP 原图，每张图片必须含一个完整新版方案。包内路径只作为显示名称与格式校验，不用于磁盘解压。

1. 读取并验证整个 ZIP 的目录、文件头、大小和 CRC。容器损坏、嵌套 ZIP、路径非法或资源超限时停止整批，不展示可确认的部分结果。
2. 独立方案文件的内容损坏、字表不匹配或不支持的格式作为该条错误；其他有效方案仍进入同一预览。
3. 同编号、同版本按规范化内容比较：忽略本地题目实例 ID，保留字目身份、顺序、公用设置、字段继承规则与补充说明。相同内容跳过重复；不同内容报告冲突，不覆盖本机方案。ZIP 内冲突的双方均不可导入。
4. 用户确认一次后逐方案事务保存；保存时重新检查重复与冲突。最终报告已添加、重复、未导入数量。

## 限制

| 内容 | 上限 |
|---|---|
| 一个方案 | 20000 字目、100000 录制步骤、每字 1–5 次 |
| 一个 Protobuf Envelope / 二维码解压结果 | 2 MiB |
| 本地完整可读 JSON / 会话全部快照分块 | 16 MiB |
| 一个 ZIP | 32 个独立方案、128 个条目、压缩 32 MiB、展开 64 MiB |
| 一张输入图片 | 12 MiB，边长不超过 20000，解码缩采样至约 1800 |
| 一批方案还原后的完整 JSON 总量 | 64 MiB，导出前也检查以保证可重新导入 |
| 会话快照分块 | 每块 100 字目，按题目实例 ID 查找，不按步骤位置计算块 |
| 数据库 IN 查询 | 每批最多 900 个 ID，保留选字次序 |

字目／词汇题目最多500字符，文段分类最多10000字符；提示和补充说明最多2000字符；方案名称120、说明4000、语言标记80、方言160。Protobuf解析限制递归深度16。本地JSON保留严格类型、重复字段、深度、ID唯一性与步骤总量校验。

大方案数据库表结构保持 Room v4：完整方案仍存 JSON，读取以最多 256 Ki 个 SQLite 字符分段，避免单个 CursorWindow 字段过大；列表按片段读取并流式解析摘要，完整模型只在用户操作时载入。

## 兼容与验收

- v1 本地方案和旧会话仍可读取，不批量改写已有数据。编辑／导出时将可完全匹配当前字表的旧字目转成固定引用，不匹配的作为自定义内容保留。
- 新协议会被旧版应用拒绝；新版外部入口拒绝旧 JSON 和分片码。科研成果 ZIP 的可读 task.json、session.json 和 CSV 保留。
- 本次未新增测试代码。Gradle lint 被本机回环连接异常阻塞，未取得类型／编译或设备验证结果。
- 待实际验收：不同数据库主键的同字表设备互扫；完整字表和随机子集的单码／ZIP 边界；自定义内容、空提示、false 覆盖、重复内置字目；32 方案预览与冲突；坏文件、截断 ZIP、大小限制；100000 步骤的录制查找、跳过和恢复；旧会话与成果导出不变。

代码生成使用 [Protobuf Gradle 插件的 Kotlin DSL](https://github.com/google/protobuf-gradle-plugin/blob/v0.9.6/examples/exampleKotlinDslProject/build.gradle.kts)，锁定插件 0.9.6、protoc / protobuf-javalite 4.36.2。
