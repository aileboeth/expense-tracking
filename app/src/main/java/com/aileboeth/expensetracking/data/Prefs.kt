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

    // ---- fun & motion switches (settings → 趣味与动效); all on by default ----

    /** The feather on the home header. */
    var fxMascot: Boolean
        get() = p.getBoolean("fxMascot", true)
        set(v) = p.edit().putBoolean("fxMascot", v).apply()

    /** Vibration on saves, swipes, drags and widget taps. */
    var fxHaptics: Boolean
        get() = p.getBoolean("fxHaptics", true)
        set(v) = p.edit().putBoolean("fxHaptics", v).apply()

    /** Confetti, the save check mark and rolling numbers. */
    var fxMotion: Boolean
        get() = p.getBoolean("fxMotion", true)
        set(v) = p.edit().putBoolean("fxMotion", v).apply()

    /** Streak + wish tiles under the home header. */
    var fxFunRow: Boolean
        get() = p.getBoolean("fxFunRow", true)
        set(v) = p.edit().putBoolean("fxFunRow", v).apply()

    /** "Your October recap" banner on the home screen in the first days of a month. */
    var fxRecapBanner: Boolean
        get() = p.getBoolean("fxRecapBanner", true)
        set(v) = p.edit().putBoolean("fxRecapBanner", v).apply()

    /** Pop-up with confetti when a badge is unlocked (badges are still collected when off). */
    var fxBadgePopup: Boolean
        get() = p.getBoolean("fxBadgePopup", true)
        set(v) = p.edit().putBoolean("fxBadgePopup", v).apply()

    // ---- full auto (accessibility) ----

    /** Open the chat behind a no-amount notification for a moment to read the amount (home screen only). */
    var autoFetch: Boolean
        get() = p.getBoolean("autoFetch", false)
        set(v) = p.edit().putBoolean("autoFetch", v).apply()

    /** Keep WeChat / Alipay screens that show money, so the reading rules can be fixed (off by default). */
    var screenSamples: Boolean
        get() = p.getBoolean("screenSamples", false)
        set(v) = p.edit().putBoolean("screenSamples", v).apply()

    /**
     * WeChat transfers recorded when they were sent ("id:time", oldest first, last 24 h). When the other side
     * accepts, "[转账] 已被接收" belongs to one of these — even if that entry was deleted meanwhile — so the
     * acceptance never adds a second entry for the same money.
     */
    fun addSentTransfer(id: Long, at: Long) {
        val list = sentTransfers(at) + (id to at)
        p.edit().putString("sentTransfers", list.joinToString(",") { "${it.first}:${it.second}" }).apply()
    }

    /** Takes the oldest transfer sent in the last 24 h off the list; null when there is none. */
    fun takeSentTransfer(now: Long): Long? {
        val list = sentTransfers(now)
        val first = list.firstOrNull()
        p.edit().putString("sentTransfers", list.drop(1).joinToString(",") { "${it.first}:${it.second}" }).apply()
        return first?.first
    }

    private fun sentTransfers(now: Long): List<Pair<Long, Long>> =
        p.getString("sentTransfers", "").orEmpty().split(',').mapNotNull { s ->
            val (id, t) = s.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (id.toLongOrNull() ?: return@mapNotNull null) to (t.toLongOrNull() ?: return@mapNotNull null)
        }.filter { now - it.second < 24 * 3600_000L }.sortedBy { it.second }

    /** The first-run guide was finished or skipped. */
    var guideDone: Boolean
        get() = p.getBoolean("guideDone", false)
        set(v) = p.edit().putBoolean("guideDone", v).apply()

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
