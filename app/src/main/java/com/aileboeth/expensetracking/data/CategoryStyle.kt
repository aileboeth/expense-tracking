package com.aileboeth.expensetracking.data

/** Emoji + colour per category, used in lists, charts and the bubble. Unknown names get a stable colour. */
object CategoryStyle {
    data class Style(val emoji: String, val color: Long)

    private val MAP = mapOf(
        "餐饮" to Style("🍜", 0xFFFF8A65),
        "零食饮料" to Style("🧋", 0xFFFFB74D),
        "交通" to Style("🚌", 0xFF4FC3F7),
        "购物" to Style("🛍️", 0xFFF06292),
        "日用" to Style("🧴", 0xFF81C784),
        "娱乐" to Style("🎮", 0xFF9575CD),
        "生活缴费" to Style("💡", 0xFFFFCA28),
        "通讯" to Style("📱", 0xFF4DB6AC),
        "学习" to Style("📚", 0xFF7986CB),
        "医疗" to Style("💊", 0xFFE57373),
        "人情" to Style("🎁", 0xFFFF8A80),
        "工资" to Style("💼", 0xFF66BB6A),
        "兼职" to Style("🧑‍💻", 0xFF26A69A),
        "转账" to Style("💸", 0xFF42A5F5),
        "红包" to Style("🧧", 0xFFEF5350),
        "退款" to Style("↩️", 0xFF8D6E63),
        "其他" to Style("📦", 0xFF90A4AE),
        "未分类" to Style("❔", 0xFFB0BEC5),
    )

    private val PALETTE = longArrayOf(
        0xFF7E57C2, 0xFF26C6DA, 0xFFFFA726, 0xFFEC407A, 0xFF9CCC65, 0xFF5C6BC0, 0xFFFF7043, 0xFF29B6F6,
    )

    fun of(name: String): Style {
        val n = name.ifBlank { "未分类" }
        return MAP[n] ?: Style(n.take(1), PALETTE[Math.floorMod(n.hashCode(), PALETTE.size)])
    }

    fun label(name: String) = "${of(name).emoji} $name"
}
