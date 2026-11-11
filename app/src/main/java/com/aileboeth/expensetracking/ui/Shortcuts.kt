package com.aileboeth.expensetracking.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.widget.Widgets

/**
 * Long-press the launcher icon: 记一笔支出 / 记一笔收入 / 搜索. Published from code (not shortcuts.xml) so the
 * labels follow the in-app language and the debug build (other package name) gets working ones too.
 */
object Shortcuts {
    const val ACTION_SEARCH = "com.aileboeth.expensetracking.SEARCH"

    fun publish(ctx: Context) {
        runCatching {
            val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
            fun intent(action: String) = Intent(ctx, MainActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            fun make(id: String, label: Int, icon: Int, i: Intent) = ShortcutInfo.Builder(ctx, id)
                .setShortLabel(ctx.getString(label)).setLongLabel(ctx.getString(label))
                .setIcon(Icon.createWithResource(ctx, icon)).setIntent(i).build()
            sm.dynamicShortcuts = listOf(
                make("add_expense", R.string.sc_expense, R.drawable.sc_expense, intent(Widgets.ACTION_ADD)),
                make("add_income", R.string.sc_income, R.drawable.sc_income, intent(Widgets.ACTION_ADD).putExtra("type", "INCOME")),
                make("search", R.string.sc_search, R.drawable.sc_search, intent(ACTION_SEARCH)),
            )
        }
    }
}
