package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType

/**
 * Picks a category from what we know about an entry.
 *
 * WeChat / Alipay notifications carry no merchant, so for most automatic entries the only real
 * information is the note the user writes. Order of trust:
 *  1. the user's own rules (keyword -> category), matched on note and payer;
 *  2. what the user picked before for exactly this note (learned);
 *  3. built-in words, on the note first, then on the payer / merchant.
 * A guess is only returned when that category still exists.
 */
object Categorizer {
    /** Built-in words for notes and merchants. More specific groups first (奶茶 before 饭). */
    private val EXPENSE_WORDS = listOf(
        "零食饮料" to listOf("奶茶", "咖啡", "饮料", "零食", "水果", "雪糕", "冰淇淋", "瑞幸", "星巴克", "蜜雪", "喜茶", "霸王茶姬", "茶百道", "古茗", "库迪"),
        "生活缴费" to listOf("慧生活", "水费", "电费", "燃气", "物业", "房租", "宿舍", "校园卡", "热水", "洗衣", "网费"),
        "通讯" to listOf("话费", "流量", "宽带", "移动", "联通", "电信"),
        "交通" to listOf("打车", "滴滴", "地铁", "公交", "车票", "高铁", "火车", "机票", "12306", "铁路", "高德打车", "哈啰", "单车", "加油", "停车", "过路费"),
        "餐饮" to listOf("早饭", "早餐", "午饭", "午餐", "晚饭", "晚餐", "夜宵", "宵夜", "外卖", "食堂", "饭卡", "餐厅", "饭", "聚餐", "火锅", "烧烤", "美团", "饿了么", "麦当劳", "肯德基", "汉堡"),
        "医疗" to listOf("药", "医院", "挂号", "体检", "看病", "牙科", "看牙"),
        "学习" to listOf("教材", "书", "课程", "学费", "打印", "复印", "文具", "考试", "报名费"),
        "娱乐" to listOf("电影", "游戏", "KTV", "会员", "充值", "Steam", "网易", "腾讯游戏", "爱奇艺", "哔哩哔哩", "优酷", "演唱会", "门票"),
        "人情" to listOf("红包", "礼物", "份子", "请客", "生日"),
        "日用" to listOf("日用", "纸巾", "洗发", "沐浴", "牙膏", "洗衣液", "超市", "便利店"),
        "购物" to listOf("淘宝", "天猫", "京东", "拼多多", "抖音商城", "衣服", "鞋", "网购"),
    )

    private val INCOME_WORDS = listOf(
        "工资" to listOf("工资", "薪"),
        "兼职" to listOf("兼职", "家教", "稿费", "跑腿"),
        "退款" to listOf("退款", "退货", "退费"),
        "红包" to listOf("红包"),
        "转账" to listOf("转账", "还钱", "还款", "生活费"),
    )

    /**
     * Built-in words only; [text] is a note or a payer / merchant name. The longest matching word
     * wins, then the one that comes first ("打车回宿舍" -> 交通, "饭卡充值" -> 餐饮). Pure.
     */
    fun builtIn(type: TxType, text: String): String {
        if (text.isBlank()) return ""
        val table = if (type == TxType.EXPENSE) EXPENSE_WORDS else INCOME_WORDS
        data class Hit(val category: String, val length: Int, val at: Int)
        val hits = table.flatMap { (cat, words) ->
            words.mapNotNull { w -> text.indexOf(w, ignoreCase = true).takeIf { it >= 0 }?.let { Hit(cat, w.length, it) } }
        }
        return hits.sortedWith(compareByDescending<Hit> { it.length }.thenBy { it.at }).firstOrNull()?.category ?: ""
    }

    /** First guess for a captured or imported entry (no note yet): payer / merchant and the parser kind. */
    fun guess(type: TxType, counterpart: String, kind: String): String {
        if (type == TxType.INCOME) {
            when {
                kind.contains("transfer") -> return "转账"
                kind.contains("refund") -> return "退款"
            }
        }
        return builtIn(type, counterpart)
    }

    /** User rule whose keyword appears in any of [texts]; the longest keyword wins (more specific). */
    fun matchRules(rules: List<Rule>, type: TxType, vararg texts: String): String? =
        rules.filter { r -> r.type == type && r.keyword.isNotBlank() && texts.any { it.contains(r.keyword, ignoreCase = true) } }
            .maxByOrNull { it.keyword.length }?.category

    /**
     * Category for an entry the user just wrote a note for. Null when nothing fits (the caller
     * falls back to the category it already has, or 其他). Touches the database: not on the main
     * thread for big tables (fine for the capsule, which shows a handful of rows).
     */
    fun fromNote(type: TxType, note: String, counterpart: String = ""): String? {
        val n = note.trim()
        val cats = Repo.categories(type).toSet()
        fun ok(c: String?) = c?.takeIf { it.isNotBlank() && it in cats }
        ok(matchRules(Repo.rules(type), type, n, counterpart))?.let { return it }
        if (n.isNotEmpty()) {
            ok(Repo.learnedCategory(type, n))?.let { return it }
            ok(builtIn(type, n))?.let { return it }
        }
        return ok(builtIn(type, counterpart))
    }

    /** For a captured notification: the user's rules on the payer first, then the built-in guess. */
    fun auto(type: TxType, counterpart: String, kind: String): String {
        if (counterpart.isNotBlank()) {
            val cats = Repo.categories(type).toSet()
            matchRules(Repo.rules(type), type, counterpart)?.takeIf { it in cats }?.let { return it }
        }
        return guess(type, counterpart, kind)
    }

    /** A few of the built-in words, for the rules page. */
    fun examples(type: TxType): List<Pair<String, String>> =
        (if (type == TxType.EXPENSE) EXPENSE_WORDS else INCOME_WORDS).map { (cat, words) -> cat to words.take(4).joinToString("、") }
}
