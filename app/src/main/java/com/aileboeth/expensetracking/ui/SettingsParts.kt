package com.aileboeth.expensetracking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Icon tints used on the settings rows. */
object Tint {
    val Purple = Brand
    val Blue = Color(0xFF3D7BF0)
    val Green = IncomeGreen
    val Orange = Color(0xFFEE8A2E)
    val Red = ExpenseRed
    val Teal = Color(0xFF16A2A0)
    val Pink = Color(0xFFDB4F93)
    val Grey = Color(0xFF7D7A8C)
}

/** A rounded square holding a tinted icon. */
@Composable
fun SettingIcon(icon: ImageVector, tint: Color, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32f).dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.56f).dp)) }
}

/** Settings row: icon, title + subtitle, and a chevron (or [trailing]). */
@Composable
fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    sub: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, tint)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing()
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingSwitch(icon: ImageVector, tint: Color, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, tint, title, sub, trailing = { Switch(checked, onChange) }) { onChange(!checked) }
}

/** Small rounded label: green "已开启" or red "去开启". */
@Composable
fun StatusPill(ok: Boolean, okText: String = "已开启", badText: String = "去开启") {
    val c = if (ok) IncomeGreen else ExpenseRed
    Text(
        if (ok) okText else badText, color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Thin divider between rows of one card, starting after the icon. */
@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** Rows of one card separated by [RowDivider]. */
@Composable
fun SettingGroup(vararg rows: (@Composable () -> Unit)?) {
    val list = rows.filterNotNull()
    SoftCard(padding = 0.dp) {
        list.forEachIndexed { i, row ->
            row()
            if (i != list.lastIndex) RowDivider()
        }
    }
}

/** 支出 / 收入 pill switch. */
@Composable
fun TypeToggle(type: com.aileboeth.expensetracking.parse.TxType, modifier: Modifier = Modifier, onChange: (com.aileboeth.expensetracking.parse.TxType) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
    ) {
        for ((t, label) in listOf(com.aileboeth.expensetracking.parse.TxType.EXPENSE to "支出", com.aileboeth.expensetracking.parse.TxType.INCOME to "收入")) {
            val on = t == type
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Brand else Color.Transparent)
                    .clickable { onChange(t) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Purple section label above a card. */
@Composable
fun GroupLabel(title: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Plain full-screen page with a back arrow and a title (used by the settings sub-pages). */
@Composable
fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}
