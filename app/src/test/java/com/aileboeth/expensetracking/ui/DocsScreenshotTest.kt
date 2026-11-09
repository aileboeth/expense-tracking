package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * README screenshots (docs/), one set per README language, with notes written in that language.
 * Run: gradlew :app:testDebugUnitTest -Pscreenshots=true --tests "*DocsScreenshotTest*"
 * Output: app/build/docs-screens/ (copy to docs/ by hand).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DocsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val en = mapOf(
        "午饭" to "lunch", "奶茶" to "bubble tea", "耳机" to "earbuds", "晚饭" to "dinner", "电费" to "electricity",
        "电影" to "movie", "教材" to "textbook", "洗衣液" to "detergent", "地铁" to "metro", "聚餐" to "team dinner",
        "话费" to "phone bill", "感冒药" to "cold medicine", "家教" to "tutoring",
    )

    private fun seed(locale: Locale, currency: String) {
        assumeTrue(System.getProperty("screenshots") == "true")
        Locale.setDefault(locale)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Repo.init(ctx)
        val prefs = Prefs(ctx)
        prefs.currency = currency
        Money.setCurrency(currency)
        prefs.budgetCents = 200000
        Repo.all().forEach { Repo.delete(it.id) }
        val english = locale.language == "en"
        fun note(s: String) = if (english) en[s] ?: s else s
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
        rows.forEachIndexed { i, (r, n) ->
            val (ago, cat, cents) = r
            Repo.insert(Tx(type = TxType.EXPENSE, amountCents = cents, category = cat, note = note(n),
                channel = if (i % 2 == 0) "微信" else "支付宝", time = at(ago, 9 + i % 12, (i * 7) % 60), source = "auto", kind = "wechat_paid"))
        }
        Repo.insert(Tx(type = TxType.INCOME, amountCents = 120000, category = "兼职", note = note("家教"), channel = "支付宝", time = at(3, 20, 0)))
        Repo.insert(Tx(type = TxType.INCOME, amountCents = 400000, category = "工资", channel = "手动", time = at(45, 10, 0)))
    }

    /** The store and the default locale outlive one test: leave them as ScreenshotTest expects. */
    @After fun cleanUp() {
        Repo.all().forEach { Repo.delete(it.id) }
        Locale.setDefault(Locale.US)
    }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { AppTheme { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/docs-screens/$name.png")
    }

    @Config(qualifiers = "zh-rCN-w393dp-h873dp-xxhdpi")
    @Test fun zh() {
        seed(Locale.SIMPLIFIED_CHINESE, "CNY")
        shoot("screen-home") { HomeScreen(PaddingValues(0.dp), {}, {}, false, {}) }
    }

    @Config(qualifiers = "zh-rCN-w393dp-h873dp-xxhdpi")
    @Test fun zhStats() {
        seed(Locale.SIMPLIFIED_CHINESE, "CNY")
        shoot("screen-stats") { StatsScreen(PaddingValues(0.dp)) }
    }

    @Config(qualifiers = "zh-rCN-w393dp-h873dp-xxhdpi")
    @Test fun zhSettings() {
        seed(Locale.SIMPLIFIED_CHINESE, "CNY")
        shoot("screen-settings") { SettingsScreen(PaddingValues(0.dp), onImport = {}) }
    }

    @Config(qualifiers = "en-rUS-w393dp-h873dp-xxhdpi")
    @Test fun en() {
        seed(Locale.US, "USD")
        shoot("screen-home-en") { HomeScreen(PaddingValues(0.dp), {}, {}, false, {}) }
    }

    @Config(qualifiers = "en-rUS-w393dp-h873dp-xxhdpi")
    @Test fun enStats() {
        seed(Locale.US, "USD")
        shoot("screen-stats-en") { StatsScreen(PaddingValues(0.dp)) }
    }

    @Config(qualifiers = "en-rUS-w393dp-h873dp-xxhdpi")
    @Test fun enSettings() {
        seed(Locale.US, "USD")
        shoot("screen-settings-en") { SettingsScreen(PaddingValues(0.dp), onImport = {}) }
    }
}
