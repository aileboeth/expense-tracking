package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import com.aileboeth.expensetracking.widget.Widgets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Renders the main screens to PNG on the PC (no emulator here).
 * Run: gradlew :app:testDebugUnitTest -Pscreenshots=true --tests "*ScreenshotTest*"
 * Output: app/build/screens/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h873dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun seed() {
        assumeTrue(System.getProperty("screenshots") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Repo.init(ctx)
        Prefs(ctx).budgetCents = 200000
        if (Repo.all().isNotEmpty()) return // the store outlives one test
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        fun at(daysAgo: Long, h: Int, m: Int) = today.minusDays(daysAgo).atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
        val rows = listOf(
            Triple(0L, "餐饮", 2350L) to "午饭", Triple(0L, "零食饮料", 1600L) to "奶茶", Triple(0L, "交通", 200L) to "",
            Triple(1L, "餐饮", 1500L) to "午饭", Triple(2L, "零食饮料", 1300L) to "奶茶", Triple(3L, "餐饮", 1800L) to "午饭",
            Triple(31L, "餐饮", 2000L) to "午饭", Triple(32L, "购物", 4900L) to "",
            Triple(1L, "购物", 19800L) to "耳机", Triple(1L, "餐饮", 3000L) to "晚饭", Triple(2L, "生活缴费", 5000L) to "电费",
            Triple(2L, "餐饮", 1800L) to "", Triple(3L, "娱乐", 4500L) to "电影", Triple(4L, "学习", 8900L) to "教材",
            Triple(5L, "餐饮", 2600L) to "", Triple(6L, "日用", 3990L) to "洗衣液", Triple(8L, "交通", 1200L) to "地铁",
            Triple(10L, "餐饮", 4200L) to "聚餐", Triple(12L, "通讯", 5000L) to "话费", Triple(15L, "购物", 12900L) to "",
            Triple(20L, "餐饮", 3100L) to "", Triple(25L, "医疗", 2800L) to "感冒药", Triple(33L, "餐饮", 9000L) to "",
            Triple(40L, "购物", 25000L) to "", Triple(70L, "娱乐", 15000L) to "", Triple(100L, "餐饮", 30000L) to "",
        )
        rows.forEachIndexed { i, (r, note) ->
            val (ago, cat, cents) = r
            Repo.insert(Tx(type = TxType.EXPENSE, amountCents = cents, category = cat, note = note,
                channel = if (i % 2 == 0) "微信" else "支付宝", time = at(ago, 9 + i % 12, (i * 7) % 60), source = "auto", kind = "wechat_paid"))
        }
        // a few earlier months so the trends, year view and forecast history have something to show
        for (k in 2L..6L) for ((cat, cents, note) in listOf(Triple("餐饮", 2600L, "午饭"), Triple("餐饮", 1800L, "午饭"), Triple("零食饮料", 1500L, "奶茶"),
            Triple("交通", 600L, "地铁"), Triple("购物", 9900L, ""), Triple("娱乐", 3500L, "电影"))) {
            Repo.insert(Tx(type = TxType.EXPENSE, amountCents = cents + k * 100, category = cat, note = note, channel = "微信",
                time = at(k * 30 + (cents % 17), 12 + (cents % 9).toInt(), 0)))
        }
        for (k in 1L..6L) Repo.insert(Tx(type = TxType.INCOME, amountCents = 400000, category = "工资", channel = "手动", time = at(k * 30 + 15, 10, 0)))
        Repo.insert(Tx(type = TxType.INCOME, amountCents = 120000, category = "兼职", note = "家教", channel = "支付宝", time = at(3, 20, 0)))
        Repo.insert(Tx(type = TxType.INCOME, amountCents = 400000, category = "工资", channel = "手动", time = at(45, 10, 0)))
        Repo.insert(Tx(type = TxType.EXPENSE, amountCents = null, channel = "微信", time = at(0, 18, 58),
            status = Status.PENDING, source = "auto", kind = "wechat_voucher", raw = "微信支付凭证"))
    }

    @Test fun home() {
        compose.setContent { AppTheme { HomeScreen(PaddingValues(0.dp), {}, {}, null, {}) } }
        compose.onRoot().captureRoboImage("build/screens/1-home.png")
    }

    @Test fun stats() {
        compose.setContent { AppTheme { StatsScreen(PaddingValues(0.dp)) } }
        compose.onRoot().captureRoboImage("build/screens/2-stats.png")
    }

    @Config(qualifiers = "w393dp-h2300dp-xxhdpi")
    @Test fun statsFull() {
        compose.setContent { AppTheme { StatsScreen(PaddingValues(0.dp)) } }
        compose.onRoot().captureRoboImage("build/screens/2b-stats-full.png")
    }

    @Test fun search() {
        compose.setContent { AppTheme { SearchScreen {} } }
        compose.onRoot().captureRoboImage("build/screens/3-search.png")
    }

    @Test fun settings() {
        compose.setContent { AppTheme { SettingsScreen(PaddingValues(0.dp)) } }
        compose.onRoot().captureRoboImage("build/screens/4-settings.png")
    }

    @Test fun blacklistApps() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadowPm = org.robolectric.Shadows.shadowOf(ctx.packageManager)
        val apps = listOf(
            "com.tencent.mm" to "微信", "com.eg.android.AlipayGphone" to "支付宝", "com.example.rent" to "租号玩",
            "com.android.mms" to "信息", "com.taobao.taobao" to "淘宝", "com.sankuai.meituan" to "美团",
            "com.ss.android.ugc.aweme" to "抖音", "com.netease.cloudmusic" to "网易云音乐", "com.android.bankabc" to "中国农业银行",
        )
        for ((pkg, label) in apps) {
            val info = android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    packageName = pkg; name = "$pkg.Main"; nonLocalizedLabel = label
                    applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
                }
                nonLocalizedLabel = label
            }
            shadowPm.addResolveInfoForIntent(
                android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), info,
            )
        }
        Prefs(ctx).blacklistApps = setOf("com.example.rent", "com.android.mms")
        Repo.log("com.example.rent", "租号玩", "收益¥4已入账", "黑名单 App，不记")
        Repo.log("com.example.rent", "租号玩", "收益¥6已入账", "黑名单 App，不记")
        Repo.log("com.android.bankabc", "农业银行", "您尾号1234账户支出50.00元", "需要确认")
        compose.setContent { AppTheme { BlacklistAppsScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/5-blacklist-apps.png")
    }

    @Test fun blacklistWords() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs(ctx).blacklistWords = setOf("多点科技", "余额宝")
        Repo.log("com.eg.android.AlipayGphone", "交易提醒", "多点科技 收款 20.00", "黑名单关键词「多点科技」，不记")
        if (Repo.recentCounterparts(1).isEmpty()) {
            for (cp in listOf("美团", "瑞幸咖啡", "张三", "滴滴出行")) Repo.insert(
                Tx(type = TxType.EXPENSE, amountCents = 1000, category = "其他", counterpart = cp, channel = "支付宝", time = System.currentTimeMillis()),
            )
        }
        compose.setContent { AppTheme { BlacklistWordsScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/6-blacklist-words.png")
    }

    @Test fun recognitionLog() {
        Repo.log("com.tencent.mm", "微信支付", "已支付¥30.00", "已记账 #12")
        Repo.log("com.tencent.mm", "微信支付", "微信支付凭证", "待补金额/待确认 #13")
        Repo.log("com.eg.android.AlipayGphone", "交易提醒", "你在某商户有一笔0.00元的免密支付", "不记：0 元免密提示")
        Repo.log("com.tencent.mm", "微信支付", "收款到账通知", "没认出")
        compose.setContent { AppTheme { RecognitionLogScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/7-log.png")
    }

    @Config(qualifiers = "w393dp-h1100dp-xxhdpi")
    @Test fun categories() {
        compose.setContent { AppTheme { CategoriesScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/8-categories.png")
    }

    @Test fun usualAmounts() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs(ctx).setUsualAmounts(TxType.EXPENSE, listOf(3000L, 1500L))
        compose.setContent { AppTheme { UsualAmountsScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/9-usual-amounts.png")
    }

    @Config(qualifiers = "w393dp-h1300dp-xxhdpi")
    @Test fun rules() {
        Repo.putRule(com.aileboeth.expensetracking.data.Rule(type = TxType.EXPENSE, keyword = "瑞幸", category = "零食饮料"))
        repeat(3) { Repo.insert(Tx(type = TxType.EXPENSE, amountCents = 1600, category = "零食饮料", note = "奶茶", time = System.currentTimeMillis() - it * 3600_000L)) }
        compose.setContent { AppTheme { CategoryRulesScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/10-rules.png")
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Config(qualifiers = "w393dp-h1200dp-xxhdpi")
    @Test fun editor() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        repeat(2) { Repo.insert(Tx(type = TxType.EXPENSE, amountCents = 1600, category = "零食饮料", note = "奶茶", time = System.currentTimeMillis() - it * 3600_000L)) }
        val id = Repo.insert(Tx(type = TxType.EXPENSE, amountCents = 8900, category = "学习", note = "教材", channel = "支付宝", time = System.currentTimeMillis()))
        // A fake receipt photo.
        val bmp = android.graphics.Bitmap.createBitmap(300, 400, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFF4E7C8.toInt()) }
        val f = java.io.File(Repo.attachDir, "receipt-test.jpg")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        Repo.addAttachment(com.aileboeth.expensetracking.data.Attachment(txId = id, name = "收据.jpg", mime = "image/jpeg", file = f.name, size = f.length()))
        Repo.addAttachment(com.aileboeth.expensetracking.data.Attachment(txId = id, name = "发票.pdf", mime = "application/pdf", file = "x.pdf", size = 1))
        compose.setContent { AppTheme { TxEditor(Repo.get(id)) {} } }
        compose.waitForIdle()
        Thread.sleep(300); compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/11-editor.png")
        ctx.hashCode()
    }

    @Config(qualifiers = "w393dp-h2600dp-xxhdpi")
    @Test fun statsInsights() {
        compose.setContent { AppTheme { StatsScreen(PaddingValues(0.dp)) } }
        compose.waitForIdle()
        // Expand 餐饮 in the ranking to show the per-note breakdown.
        compose.onAllNodes(androidx.compose.ui.test.hasText("Food", substring = false))[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/2c-stats-insights.png")
    }

    @Config(qualifiers = "w393dp-h1700dp-xxhdpi")
    @Test fun stability() {
        org.robolectric.shadows.ShadowBuild.setManufacturer("Xiaomi")
        org.robolectric.shadows.ShadowBuild.setModel("Redmi K70")
        compose.setContent { AppTheme { StabilityScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/12-stability.png")
    }

    @Config(qualifiers = "zh-rCN-w393dp-h873dp-xxhdpi")
    @Test fun homeZh() {
        java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE)
        compose.setContent { AppTheme { HomeScreen(PaddingValues(0.dp), {}, {}, null, {}) } }
        compose.onRoot().captureRoboImage("build/screens/20-home-zh.png")
    }

    @Config(qualifiers = "b+zh+Hant+TW-w393dp-h1900dp-xxhdpi")
    @Test fun settingsHant() {
        java.util.Locale.setDefault(java.util.Locale.TRADITIONAL_CHINESE)
        compose.setContent { AppTheme { SettingsScreen(PaddingValues(0.dp)) } }
        compose.onRoot().captureRoboImage("build/screens/21-settings-hant.png")
    }

    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun payApps() {
        java.util.Locale.setDefault(java.util.Locale.US)
        compose.setContent { AppTheme { PaymentAppsScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/13-pay-apps.png")
    }

    private fun checkIns() {
        for (i in 0L..9L) if (i != 4L) Repo.markActive(LocalDate.now().minusDays(i).toEpochDay())
    }

    @Config(qualifiers = "w393dp-h1300dp-xxhdpi")
    @Test fun achievements() {
        checkIns()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        com.aileboeth.expensetracking.data.Achievements.check(ctx)
        compose.setContent { AppTheme { AchievementsScreen {} } }
        compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/14-achievements.png")
    }

    @Config(qualifiers = "w393dp-h1100dp-xxhdpi")
    @Test fun wishJar() {
        if (Repo.wishes().isEmpty()) {
            val id = Repo.putWish(com.aileboeth.expensetracking.data.Wish(name = "新耳机", emoji = "🎧", target = 129900))
            Repo.deposit(id, 50000, "10月结余")
            Repo.deposit(id, 30000)
        }
        compose.setContent { AppTheme { WishJarScreen {} } }
        compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/15-wish.png")
    }

    @Test fun recap() {
        checkIns()
        compose.setContent { AppTheme { RecapScreen(YearMonth.now().minusMonths(1)) {} } }
        compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/16-recap-1.png")
        for (page in 2..6) {
            compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.85f, height * 0.5f)) }
            compose.mainClock.advanceTimeBy(2500)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screens/16-recap-$page.png")
        }
    }

    @Test fun badgeCelebration() {
        compose.setContent { AppTheme { BadgeCelebration(listOf("first", "streak3", "streak7", "notes50")) {} } }
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/17-celebration.png")
    }

    @Test fun mascots() {
        compose.setContent {
            AppTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Row(Modifier.background(HeaderBrush).padding(12.dp)) {
                        for (m in com.aileboeth.expensetracking.data.Mood.entries) Mascot(m, emptyList(), size = 72.dp)
                    }
                    androidx.compose.foundation.layout.Row(Modifier.background(androidx.compose.ui.graphics.Color.White).padding(12.dp)) {
                        for (m in com.aileboeth.expensetracking.data.Mood.entries) Mascot(m, emptyList(), size = 72.dp, light = false)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/18-mascots.png")
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun widgets() {
        checkIns()
        compose.setContent {
            AppTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.background(androidx.compose.ui.graphics.Color(0xFF3A4A5A)).padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                ) {
                    @androidx.compose.runtime.Composable
                    fun one(k: com.aileboeth.expensetracking.widget.Widgets.Kind, w: Int, h: Int) = androidx.compose.ui.viewinterop.AndroidView(
                        { c -> Widgets.preview(c, k, Widgets.SizeDp(w, h)).apply(c, android.widget.FrameLayout(c)) },
                        Modifier.size(w.dp, h.dp),
                    )
                    // the sizes a launcher may give: narrow-and-tall like the user's vivo, wide, short
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                        one(Widgets.Kind.SUMMARY, 180, 210)
                        one(Widgets.Kind.TODAY, 150, 150)
                    }
                    one(Widgets.Kind.SUMMARY, 320, 250)
                    one(Widgets.Kind.SUMMARY, 250, 170)
                    one(Widgets.Kind.SUMMARY, 320, 110)
                    one(Widgets.Kind.QUICK, 360, 56)
                    one(Widgets.Kind.QUICK, 220, 56)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/19-widgets.png")
    }

    @Config(qualifiers = "w393dp-h1000dp-xxhdpi")
    @Test fun settingsSections() {
        var sec by androidx.compose.runtime.mutableStateOf(SettingsSection.AUTO)
        compose.setContent { AppTheme { SettingsSectionScreen(sec, {}) } }
        for (s in SettingsSection.entries) {
            sec = s
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screens/4c-settings-${s.ordinal}-${s.name.lowercase()}.png")
        }
    }

    @Config(qualifiers = "w393dp-h2000dp-xxhdpi")
    @Test fun analysis() {
        checkIns()
        var tab by androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent { AppTheme { androidx.compose.runtime.key(tab) { AnalysisScreen(YearMonth.now(), tab, onBack = {}) } } }
        for (t in 0..3) {
            tab = t
            compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screens/20-analysis-$t.png")
        }
    }

    @Config(qualifiers = "zh-rCN-w393dp-h2000dp-xxhdpi")
    @Test fun analysisZh() {
        checkIns()
        var tab by androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent { AppTheme { androidx.compose.runtime.key(tab) { AnalysisScreen(YearMonth.now(), tab, onBack = {}) } } }
        for (t in 0..3) {
            tab = t
            compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screens/20-analysis-zh-$t.png")
        }
    }

    @Config(qualifiers = "zh-rCN-w393dp-h1000dp-xxhdpi")
    @Test fun settingsZh() {
        compose.setContent { AppTheme { SettingsScreen(PaddingValues(0.dp)) } }
        compose.onRoot().captureRoboImage("build/screens/4d-settings-zh.png")
    }

    @Config(qualifiers = "zh-rCN-w393dp-h1600dp-xxhdpi")
    @Test fun statsZh() {
        compose.setContent { AppTheme { StatsScreen(PaddingValues(0.dp)) } }
        compose.waitForIdle(); Thread.sleep(300); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/2c-stats-zh.png")
    }

    @Config(qualifiers = "zh-rCN-w393dp-h873dp-xxhdpi")
    @Test fun guideZh() {
        compose.setContent { AppTheme { GuideScreen {} } }
        for (p in 0 until 5) {
            compose.waitForIdle(); Thread.sleep(200); compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screens/21-guide-zh-$p.png")
            if (p < 4) compose.onNodeWithText("下一步").performClick()
        }
    }

    @Config(qualifiers = "w393dp-h873dp-xxhdpi")
    @Test fun guideEn() {
        compose.setContent { AppTheme { GuideScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/21-guide-en-0.png")
        compose.onNodeWithText("Next").performClick(); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/21-guide-en-1.png")
    }

    @Config(qualifiers = "zh-rCN-w393dp-h1500dp-xxhdpi")
    @Test fun fullAutoZh() {
        compose.setContent { AppTheme { FullAutoScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/22-full-auto-zh.png")
    }

    @Config(qualifiers = "zh-rCN-w393dp-h1000dp-xxhdpi")
    @Test fun autoSectionZh() {
        compose.setContent { AppTheme { SettingsSectionScreen(SettingsSection.AUTO, {}) } }
        compose.onRoot().captureRoboImage("build/screens/22-auto-section-zh.png")
    }

    @Config(qualifiers = "w393dp-h1900dp-xxhdpi")
    @Test fun settingsFull() {
        compose.setContent { AppTheme { SettingsScreen(PaddingValues(0.dp)) } }
        compose.onRoot().captureRoboImage("build/screens/4b-settings-full.png")
    }
}
