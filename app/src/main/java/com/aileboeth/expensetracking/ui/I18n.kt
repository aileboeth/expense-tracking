package com.aileboeth.expensetracking.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.aileboeth.expensetracking.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * App language. "" follows the phone. On Android 13+ the system keeps the choice (it also shows up
 * in system settings → apps → language); older versions wrap the context with our saved locale.
 */
object AppLocale {
    const val PREF = "settings"
    private const val KEY = "language"
    val OPTIONS = listOf("", "zh-Hans", "zh-Hant", "en")

    private fun toLocale(tag: String): Locale? = when (tag) {
        "zh-Hans" -> Locale.forLanguageTag("zh-Hans-CN")
        "zh-Hant" -> Locale.forLanguageTag("zh-Hant-TW")
        "en" -> Locale.ENGLISH
        else -> null
    }

    fun saved(ctx: Context): String = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    /** For attachBaseContext before Android 13. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val loc = toLocale(saved(base)) ?: return base
        val cfg = Configuration(base.resources.configuration).apply { setLocales(LocaleList(loc)) }
        return base.createConfigurationContext(cfg)
    }

    /** Switches the language; the caller recreates the activity on old versions (13+ restarts it itself). */
    fun set(ctx: Context, tag: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.getSystemService(LocaleManager::class.java).applicationLocales =
                toLocale(tag)?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            val loc = toLocale(tag) ?: Locale.getDefault()
            @Suppress("DEPRECATION")
            ctx.applicationContext.resources.let { r ->
                r.updateConfiguration(Configuration(r.configuration).apply { setLocales(LocaleList(loc)) }, r.displayMetrics)
            }
        }
    }

    /** What is in effect now (Android 13+ may have been changed in system settings). */
    fun current(ctx: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val l = ctx.getSystemService(LocaleManager::class.java).applicationLocales
            if (l.isEmpty) return ""
            val loc = l[0]
            return when {
                loc.language == "zh" && (loc.script == "Hant" || loc.country in setOf("TW", "HK", "MO")) -> "zh-Hant"
                loc.language == "zh" -> "zh-Hans"
                else -> "en"
            }
        }
        return saved(ctx)
    }

    fun label(ctx: Context, tag: String): String = when (tag) {
        "zh-Hans" -> "简体中文"
        "zh-Hant" -> "繁體中文"
        "en" -> "English"
        else -> ctx.getString(R.string.lang_system)
    }

    /** The locale the UI is shown in right now. */
    fun locale(ctx: Context): Locale = ctx.resources.configuration.locales[0]
}

/** Display names for things stored as fixed Chinese keys in the database. */
object Labels {
    private val CATEGORIES = mapOf(
        "餐饮" to R.string.cat_food, "零食饮料" to R.string.cat_snacks, "交通" to R.string.cat_transport,
        "购物" to R.string.cat_shopping, "日用" to R.string.cat_daily, "娱乐" to R.string.cat_fun,
        "生活缴费" to R.string.cat_bills, "通讯" to R.string.cat_phone, "学习" to R.string.cat_study,
        "医疗" to R.string.cat_medical, "人情" to R.string.cat_gifts, "其他" to R.string.cat_other,
        "工资" to R.string.cat_salary, "兼职" to R.string.cat_sidejob, "转账" to R.string.cat_transfer,
        "红包" to R.string.cat_redpacket, "退款" to R.string.cat_refund, "未分类" to R.string.cat_none,
    )

    /** "餐饮" -> "Food" in English; categories the user created are shown as typed. */
    fun category(ctx: Context, key: String): String = CATEGORIES[key.ifBlank { "未分类" }]?.let { ctx.getString(it) } ?: key

    fun channel(ctx: Context, ch: String): String = when (ch) {
        "微信" -> ctx.getString(R.string.ch_wechat)
        "支付宝" -> ctx.getString(R.string.ch_alipay)
        "手动" -> ctx.getString(R.string.ch_manual)
        else -> ch
    }

    fun kind(ctx: Context, kind: String): String = ctx.getString(
        when (kind) {
            "wechat_voucher" -> R.string.kind_wechat_voucher
            "wechat_paid" -> R.string.kind_wechat_paid
            "wechat_income", "wechat_income_no_amount" -> R.string.kind_wechat_income
            "alipay_expense" -> R.string.kind_alipay_expense
            "alipay_autopay" -> R.string.kind_alipay_autopay
            "alipay_transfer_in" -> R.string.kind_alipay_transfer
            "alipay_received", "alipay_received_no_amount", "alipay_income" -> R.string.kind_alipay_income
            "generic" -> R.string.kind_generic
            "wechat_bill" -> R.string.kind_wechat_bill
            "alipay_bill" -> R.string.kind_alipay_bill
            "widget" -> R.string.kind_widget
            "" -> R.string.ch_manual
            else -> return kind
        }
    )

    fun yearMonth(ctx: Context, ym: YearMonth): String =
        DateTimeFormatter.ofPattern(ctx.getString(R.string.fmt_year_month), AppLocale.locale(ctx)).format(ym)

    fun monthDay(ctx: Context, d: LocalDate): String {
        val pattern = if (d.year == LocalDate.now().year) R.string.fmt_month_day else R.string.fmt_year_month_day
        return DateTimeFormatter.ofPattern(ctx.getString(pattern), AppLocale.locale(ctx)).format(d)
    }

    fun monthShort(ctx: Context, ym: YearMonth): String =
        DateTimeFormatter.ofPattern(ctx.getString(R.string.fmt_month_short), AppLocale.locale(ctx)).format(ym)

    fun weekday(ctx: Context, d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, AppLocale.locale(ctx))

    /** 周一 / Mon for index 0 = Monday. */
    fun weekdayShort(ctx: Context, i: Int): String = java.time.DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, AppLocale.locale(ctx))

    /** 星期一 / Monday. */
    fun weekdayFull(ctx: Context, i: Int): String = java.time.DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, AppLocale.locale(ctx))

    /** 1月 / Jan for month 1..12. */
    fun monthNarrow(ctx: Context, m: Int): String = monthShort(ctx, YearMonth.of(2000, m))

    /** 今天 / 昨天 / 11月7日. */
    fun day(ctx: Context, d: LocalDate): String = when (d) {
        LocalDate.now() -> ctx.getString(R.string.today)
        LocalDate.now().minusDays(1) -> ctx.getString(R.string.yesterday)
        else -> monthDay(ctx, d)
    }
}

/** Reasons the parser stores in the log (Chinese keys) shown in the UI language. */
object Reasons {
    private val MAP = mapOf(
        "微信支付的其他消息" to R.string.reason_wechat_other,
        "0元的免密/自动扣款提示" to R.string.reason_zero_autopay,
        "0元的支出提示" to R.string.reason_zero,
        "0 元提示" to R.string.reason_zero,
        "没认出的交易提醒" to R.string.reason_unknown_alipay,
    )

    @Composable fun show(s: String): String = MAP[s]?.let { androidx.compose.ui.res.stringResource(it) } ?: s
}

@Composable fun catName(key: String): String = Labels.category(LocalContext.current, key)
/** Emoji + name, for chips. */
@Composable fun catLabel(key: String): String = com.aileboeth.expensetracking.data.CategoryStyle.of(key).emoji + " " + catName(key)
@Composable fun channelName(ch: String): String = Labels.channel(LocalContext.current, ch)
