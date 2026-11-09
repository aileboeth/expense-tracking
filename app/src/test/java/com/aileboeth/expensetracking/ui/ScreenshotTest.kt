package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
        Repo.insert(Tx(type = TxType.INCOME, amountCents = 120000, category = "兼职", note = "家教", channel = "支付宝", time = at(3, 20, 0)))
        Repo.insert(Tx(type = TxType.INCOME, amountCents = 400000, category = "工资", channel = "手动", time = at(45, 10, 0)))
        Repo.insert(Tx(type = TxType.EXPENSE, amountCents = null, channel = "微信", time = at(0, 18, 58),
            status = Status.PENDING, source = "auto", kind = "wechat_voucher", raw = "微信支付凭证"))
    }

    @Test fun home() {
        compose.setContent { AppTheme { HomeScreen(PaddingValues(0.dp), {}, {}, false, {}) } }
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
        compose.setContent { AppTheme { SettingsScreen(PaddingValues(0.dp), onImport = {}) } }
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
}
