package com.aileboeth.expensetracking.data

import android.content.Context
import android.content.SharedPreferences

/** User settings. */
class Prefs(ctx: Context) {
    private val app: Context = ctx.applicationContext

    /**
     * The phone's own locale from system settings, not the language picked inside the app (on Android 13+
     * the per-app language also changes Locale.getDefault() and the system Resources). Region defaults use this.
     */
    fun systemLocale(): java.util.Locale =
        if (android.os.Build.VERSION.SDK_INT >= 33) app.getSystemService(android.app.LocaleManager::class.java).systemLocales.get(0)
        else java.util.Locale.getDefault()

    private val p: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var bubbleEnabled: Boolean
        get() = p.getBoolean("bubbleEnabled", true)
        set(v) = p.edit().putBoolean("bubbleEnabled", v).apply()

    /** How long the bubble stays for an entry that already has its amount. */
    var bubbleSeconds: Int
        get() = p.getInt("bubbleSeconds", 6)
        set(v) = p.edit().putInt("bubbleSeconds", v.coerceIn(2, 30)).apply()

    /** Also guess from other apps' notifications (always needs confirming). */
    var otherApps: Boolean
        get() = p.getBoolean("otherApps", false)
        set(v) = p.edit().putBoolean("otherApps", v).apply()

    /** Package names whose notifications are never recorded. */
    var blacklistApps: Set<String>
        get() = p.getStringSet("blacklistApps", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("blacklistApps", v).apply()

    /** Payment apps (other than WeChat / Alipay) whose notifications are read. Unset = the region's defaults. */
    fun payApps(): Set<String> {
        if (p.contains("payApps")) return p.getStringSet("payApps", emptySet())!!.toSet()
        val loc = systemLocale()
        val country = com.aileboeth.expensetracking.parse.PaymentApps.country(loc.language, loc.country)
        return com.aileboeth.expensetracking.parse.PaymentApps.ALL
            .filter { it.pkg !in com.aileboeth.expensetracking.parse.Parser.KNOWN_APPS && com.aileboeth.expensetracking.parse.PaymentApps.defaultOn(it, country) }
            .map { it.pkg }.toSet()
    }

    fun setPayApps(v: Set<String>) = p.edit().putStringSet("payApps", v).apply()

    val hasCurrency: Boolean get() = p.contains("currency")

    /** Ledger currency (ISO 4217). Unset = the phone region's currency. Only changes how amounts are shown. */
    var currency: String
        get() = p.getString("currency", null) ?: Money.defaultCurrencyCode(systemLocale())
        set(v) = p.edit().putString("currency", v).apply()

    /** App language: "" = follow the phone, or "zh-Hans" / "zh-Hant" / "en". */
    var language: String
        get() = p.getString("language", "") ?: ""
        set(v) = p.edit().putString("language", v).commit().let { }

    /** Words: a notification whose text or payer/merchant contains one of them is not recorded. */
    var blacklistWords: Set<String>
        get() = p.getStringSet("blacklistWords", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("blacklistWords", v).apply()

    /** Monthly spending budget in cents; 0 = not set. */
    var budgetCents: Long
        get() = p.getLong("budgetCents", 0L)
        set(v) = p.edit().putLong("budgetCents", v.coerceAtLeast(0)).apply()

    /** Usual amounts the user set for the quick-pick chips (cents). Empty = pick them from the history. */
    fun usualAmounts(type: com.aileboeth.expensetracking.parse.TxType): List<Long> =
        (p.getString("usual_" + type.name, "") ?: "").split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }

    fun setUsualAmounts(type: com.aileboeth.expensetracking.parse.TxType, cents: List<Long>) =
        p.edit().putString("usual_" + type.name, cents.distinct().joinToString(",")).apply()

    /** How many monthly recaps were opened (for the badge). */
    var recapsViewed: Int
        get() = p.getInt("recapsViewed", 0)
        set(v) = p.edit().putInt("recapsViewed", v).apply()

    /** The last month ("2026-10") whose recap banner was opened or closed on the home screen. */
    var recapBannerDone: String
        get() = p.getString("recapBannerDone", "") ?: ""
        set(v) = p.edit().putString("recapBannerDone", v).apply()

    /** The last month whose surplus was put into (or declined for) the wish jar. */
    var surplusMonthDone: String
        get() = p.getString("surplusMonthDone", "") ?: ""
        set(v) = p.edit().putString("surplusMonthDone", v).apply()

    /** The wish shown on the home screen (0 = newest unfinished). */
    var focusWish: Long
        get() = p.getLong("focusWish", 0L)
        set(v) = p.edit().putLong("focusWish", v).apply()

    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()

    /** Optional prefix put in front of github.com links (a download mirror). Empty = direct. */
    var mirrorPrefix: String
        get() = p.getString("mirrorPrefix", "") ?: ""
        set(v) = p.edit().putString("mirrorPrefix", v.trim()).apply()

    var lastUpdateCheck: Long
        get() = p.getLong("lastUpdateCheck", 0L)
        set(v) = p.edit().putLong("lastUpdateCheck", v).apply()

    var listenerConnectedAt: Long
        get() = p.getLong("listenerConnectedAt", 0L)
        set(v) = p.edit().putLong("listenerConnectedAt", v).apply()

    fun blacklistHit(vararg texts: String): String? {
        val words = blacklistWords
        for (w in words) if (w.isNotBlank() && texts.any { it.contains(w) }) return w
        return null
    }
}
