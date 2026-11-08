<p align="center"><img src="docs/icon.png" width="112" alt="Expense Tracking"></p>

# Expense Tracking

一个为 vivo OriginOS 手机调过的 Android 自动记账 App：读取微信、支付宝的付款通知自动记账，
付款后在屏幕边上弹一个小圆球，点开就能补金额、选分类、写用途。账单只存在手机里。

*An Android expense tracker that records WeChat Pay / Alipay payments from their notifications.
Data stays on the phone. UI in Chinese.*

<p align="center">
  <img src="docs/screen-home.png" width="250" alt="首页">
  <img src="docs/screen-stats.png" width="250" alt="统计">
  <img src="docs/screen-settings.png" width="250" alt="设置">
</p>

<sub>截图里是示例数据。</sub>

## 功能

- **自动记账**：微信「已支付¥30.00」、支付宝「你有一笔10.00元的支出」等直接入账；
  同一条通知被刷新不会重复记，金额相同的两笔连续消费也不会被并成一笔。
- **没有金额的通知不丢**：手表付款（微信只显示「微信支付凭证」）、支付宝「某某给你转了1笔钱」
  会记成「待补金额」，悬浮球一直留着，填了金额才算进统计。
- **悬浮小圆球**：不抢焦点、不跳转，不挡正在用的 App；可以拖动，长按收起。快超预算时描黄边，超了描红边。
- **复盘图表**：本月每日走势（叠上月对比）、消费日历热力图、分类环形图和排行（比上月多/少）、近 6/12 个月收支趋势。
- **月度预算**：首页显示还剩多少、每天还能花多少。
- **搜索和筛选**：按备注、对方、分类、金额区间、渠道、时间找账。
- **导入账单对账**：导入微信（.xlsx）/ 支付宝（.csv）官方导出的账单，找出漏记的一键补上，
  还能给手表付款这类“待补金额”的账自动补上金额；同一笔不会导入两次。
- **桌面小组件**：本月支出、今天花了多少、预算剩多少，点「+ 记一笔」直接记。
- **黑名单**：拉黑的 App 不记；付款方/商户/内容含黑名单关键词的不记。
- 0 元免密/自动扣款提示、聊天消息、其他 App 的「收益已入账」等不记（其他 App 可在设置里打开识别，需确认才记）。
- 手动记账、导出 CSV、识别记录（看每条通知记了没有、为什么）。
- **自动更新**：从本仓库最新 Release 下载，校验 SHA-256 后安装；可以填下载加速前缀。

## 安装后要打开的权限

1. 通知使用权（必需）。如果提示「受限设置」：应用详情 → 右上角 ⋮ → 允许受限设置。
2. 悬浮窗。
3. 忽略电池优化；vivo 还要在应用详情里允许「后台高耗电」和「自启动」。

## 导出账单（用于对账）

- 微信：我 → 服务 → 钱包 → 账单 → 右上角 ⋯ → 下载账单 → 用于个人对账。
- 支付宝：我的 → 账单 → 右上角 ⋯ → 开具交易流水证明 → 用于个人对账。

文件会发到邮箱，是加密压缩包（密码在微信/支付宝的消息里）。解压后在「设置 → 导入微信/支付宝账单对账」里选 .xlsx / .csv。

## 编译

需要 JDK 17+、Android SDK（compileSdk 37、build-tools 36.1.0）。

```bash
./gradlew :app:testDebugUnitTest   # 通知解析、账单导入的单元测试
./gradlew :app:assembleRelease
# 界面截图（Robolectric，需要 JDK 17/21 跑测试）：
./gradlew :app:testDebugUnitTest -Pscreenshots=true -PtestJavaHome=/path/to/jdk17 --tests "*ScreenshotTest*"
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

Tag 写成 `v<版本>`，例如 `v0.1.1`。

## 识别规则从哪来

规则来自作者手机（vivo，Android 16，微信 8.0.76，支付宝 12.12.6）上真实收到的通知，
测试样例在 `app/src/test/.../ParserTest.kt`。遇到没认出的格式，在「设置 → 识别记录」里能看到原文。

## 许可证

[MIT](LICENSE)
