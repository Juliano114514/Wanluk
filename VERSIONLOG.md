# 版本日志

按版本倒序维护；同版本仅保留一个条目。每条记录包含日期、升级原因、变更摘要与实际验证状态。

## 1.1.0 — 2026-10-03

- versionCode：`2`。
- 升级原因：用户明确指定当前版本为 `1.1.0`，作为版本管理规则启用基线。
- 变更摘要：新增覆盖全仓库的 LLM 版本管理规则；建立本版本日志；应用版本由 `1.0` / versionCode `1` 调整为 `1.1.0` / versionCode `2`。此前功能历史不在本条补录。
- 验证状态：版本配置与日志一致性、六类默认规则场景及 diff 已静态核对；未新增测试代码，未执行构建、打包、提交或推送。

### 2026-10-09 CI 发布配置

- 用户明确要求保持 `1.1.0` / versionCode `2`，本次配置、提交和首次云端打包不升级版本。
- 增加 GitHub Actions：主分支版本变化自动构建签名 APK 并发布到 GitHub Releases，支持手动触发；补充密钥忽略规则、官方 Gradle 下载源及操作说明。
- 验证状态：YAML、Bash、内嵌 Python 语法、版本变化／不变／手动触发分支及 diff 静态检查通过。首次自动触发成功，但 packageRelease 因密钥库密码不匹配失败；增加构建前密钥库及 alias 校验。用户更正 Secret 后，运行 [37907789338](https://github.com/Juliano114514/Wanluk/actions/runs/37907789338) 完成 assembleRelease、lintVital、APK 签名校验、产物上传及发布；[v1.1.0-code2](https://github.com/Juliano114514/Wanluk/releases/tag/v1.1.0-code2) 已包含 `Wanluk-1.1.0-2.apk` 和 `SHA256SUMS.txt`。未新增测试代码，未执行设备安装或运行验证。
