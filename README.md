# Expense Tracking（记账）

一个为 vivo OriginOS 手机调过的 Android 自动记账 App：读取微信、支付宝的付款通知自动记账，
付款后在屏幕边上弹一个小圆球，点开就能补金额、选分类、写用途。账单只存在手机里。

*An Android expense tracker that records WeChat Pay / Alipay payments from their notifications.
Data stays on the phone. UI in Chinese.*

## 功能

- **自动记账**：微信「已支付¥30.00」、支付宝「你有一笔10.00元的支出」等直接入账；
  同一条通知被刷新不会重复记，金额相同的两笔连续消费也不会被并成一笔。
- **没有金额的通知不丢**：手表付款（微信只显示「微信支付凭证」）、支付宝「某某给你转了1笔钱」
  会记成「待补金额」，悬浮球一直留着，填了金额才算进统计。
- **悬浮小圆球**：不抢焦点、不跳转，不挡正在用的 App；可以拖动，长按收起。
- **黑名单**：拉黑的 App 不记；付款方/商户/内容含黑名单关键词的不记。
- 0 元免密/自动扣款提示、聊天消息、其他 App 的「收益已入账」等不记（其他 App 可在设置里打开识别，需确认才记）。
- 月度账单、分类统计、手动记账、导出 CSV、识别记录（看每条通知记了没有、为什么）。
- **自动更新**：从本仓库最新 Release 下载，校验 SHA-256 后安装；可以填下载加速前缀。

## 安装后要打开的权限

1. 通知使用权（必需）。如果提示「受限设置」：应用详情 → 右上角 ⋮ → 允许受限设置。
2. 悬浮窗。
3. 忽略电池优化；vivo 还要在应用详情里允许「后台高耗电」和「自启动」。

## 编译

需要 JDK 17+、Android SDK（compileSdk 37、build-tools 36.1.0）。

```bash
./gradlew :app:testDebugUnitTest   # 通知解析的单元测试
./gradlew :app:assembleRelease
```

依赖优先走阿里云 Maven 镜像（见 `settings.gradle.kts`）。正式签名读取仓库外的
`../keys/keystore.properties`；没有这个文件时 release 用 debug 密钥签名。

## 发版

Release 需要三个固定名字的文件，App 的自动更新会读它们：

| 文件 | 内容 |
|---|---|
| `version.json` | `{"version","versionCode","apk","sha256","notes"}` |
| `ExpenseTracking-<版本>.apk` | 安装包 |
| `SHA256SUMS.txt` | 校验值 |

Tag 写成 `v<版本>`，例如 `v0.1.0`。

## 识别规则从哪来

规则来自作者手机（vivo，Android 16，微信 8.0.76，支付宝 12.12.6）上真实收到的通知，
测试样例在 `app/src/test/.../ParserTest.kt`。遇到没认出的格式，在「设置 → 识别记录」里能看到原文。

## 许可证

[MIT](LICENSE)
