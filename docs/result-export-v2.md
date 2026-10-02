# 本地录制成果包 v2/v3

当前 `SessionExport.exportSchemaVersion = 3`。对外字目／词汇方案为 Protobuf v2，文段为v3；本地新方案为JSON v3，并兼容历史v1/v2。成果包版本独立，录音预设仍为48000 Hz / PCM16 / 单声道 WAV。

完整导出继续包含 `task.json`、`session.json`、`metadata.csv`、`sha256.json`、`README.txt` 和 `audio/*.wav`。v3以可读名称导出，v2的旧包仍保留UUID名称，已有结果包不会原地改写。既有CSV列及顺序保持，录音字节不变。

新录音保存前按整段峰值施加统一线性增益，最多4倍，目标峰值为29000/32768，高峰值录音及静音保持1倍。JSON `takes[].appliedGain` 与末尾追加的 CSV `applied_gain` 记录倍率，旧录音为1；peak/rms为保存后数值，clippedFraction与削波提示反映采集信号。增益不能改善信噪比或修复已有削波，导出时不再改变音频字节。

- WAV：`题号_内容_发音人_方言_第N次_版本N.wav`；文段内容包含方案标题和正文开头，缺少发音人或方言时用“未填写”。
- 先按完整保留历史的创建时间和takeId排序编号，再选择导出题目／版本；同一版本在完整导出和选录中保持同一名称。
- 文件名限制180个UTF-8字节，过滤非法字符、控制字符与路径分隔符，清理尾部空格和点，按忽略大小写的名称消歧。
- JSON `planType` 保存会话分类，`takes[].audioFile` 保存实际相对路径；CSV音频路径和SHA-256键引用同一路径。历史任务的内嵌JSON按其原本地版本输出。
- 单会话ZIP名称与批量会话目录包含录制名称、发音人、方言、录制日期、短会话编号。批量ZIP标明组数与导出时间；保留根目录`index.json`，使用其中的`directory`字段定位会话。

| JSON 会话字段 | CSV 列 | 含义与上限 |
|---|---|---|
| title | recording_title | 本条录制名称，120 字符 |
| researchCode | research_code | 选填研究编号，120 字符 |
| collectionLocation | collection_location | 选填采集地点，160 字符 |
| collector | collector | 选填采集者，80 字符 |
| takes[].reviewStatus | review_status | unreviewed / accepted / needs_rerecord |
| takes[].appliedGain | applied_gain | 保存时施加的整段线性增益，1–4倍；旧录音为1 |
| task.items[].phonology | sheng, hu, deng, yun, diao, she, zu, source_id | 录制快照中的原标注，不从当前字库重新查询 |

研究字段为空时导出空字符串。`metadata.csv` 的复核列属于当前采用版本；待录和跳过行没有采用版本，留空。新录音为 unreviewed，切换采用旧版保留旧版的状态。复核由使用者手动登记，录音质量提示不会自动标为通过。

JSON 的 `contentRevision` 记录生成这份成果时的会话内容版本。录音、采用版本、复核、备注、跳过原因及研究信息变化会递增版本；位置导航不会递增。只有完整输出流成功关闭且本机版本仍一致时才登记已导出，记录的 `exportedContentRevision` 用于“待导出”筛选。

按题目导出保留原题号，添加 `selection.json`（scope=selected_steps，positionBase=0）。按版本导出使用 `recordings.json`、`takes.csv` 和 `selection.json`（scope=selected_takes），`takes.csv` 的相同追加列属于各自版本；采用标记不变。两种选择导出均保留完整 `task.json`，不登记整条会话已导出。

CSV 为 UTF-8 BOM，正确转义引号和换行；潜在公式文本加单引号，原文保留在 JSON。SHA-256 记录所含 WAV 的实际内容，失败或中断时不更新导出状态，原始本地录音保留。此格式用于成果交接与分析，不用于应用备份或自动恢复。
