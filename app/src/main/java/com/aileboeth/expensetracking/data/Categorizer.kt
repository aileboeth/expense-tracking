package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType

/**
 * First guess of a category. WeChat notifications carry no merchant, so most entries start
 * without one and the user picks it in the bubble; only clear cases are guessed here.
 */
object Categorizer {
    private val EXPENSE_RULES = listOf(
        "生活缴费" to listOf("慧生活", "水费", "电费", "燃气", "物业", "宿舍", "校园卡", "热水", "洗衣"),
        "餐饮" to listOf("美团", "饿了么", "麦当劳", "肯德基", "食堂", "餐厅", "外卖", "瑞幸", "星巴克", "蜜雪"),
        "交通" to listOf("滴滴", "地铁", "公交", "12306", "铁路", "高德打车", "哈啰", "加油"),
        "购物" to listOf("淘宝", "天猫", "京东", "拼多多", "抖音商城"),
        "娱乐" to listOf("网易", "腾讯游戏", "Steam", "爱奇艺", "哔哩哔哩", "优酷"),
        "通讯" to listOf("移动", "联通", "电信", "话费", "流量"),
    )

    fun guess(type: TxType, counterpart: String, kind: String): String {
        if (type == TxType.INCOME) {
            return when {
                kind.contains("transfer") -> "转账"
                kind.contains("refund") -> "退款"
                else -> ""
            }
        }
        if (counterpart.isBlank()) return ""
        return EXPENSE_RULES.firstOrNull { (_, words) -> words.any { counterpart.contains(it) } }?.first ?: ""
    }
}
