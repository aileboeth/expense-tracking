package com.aileboeth.expensetracking.data

import android.content.Context
import android.content.SharedPreferences

/** User settings. */
class Prefs(ctx: Context) {
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
