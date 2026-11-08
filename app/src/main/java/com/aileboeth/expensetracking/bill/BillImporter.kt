package com.aileboeth.expensetracking.bill

import com.aileboeth.expensetracking.data.Categorizer
import com.aileboeth.expensetracking.parse.TxType
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

enum class BillSource(val channel: String, val kind: String, val label: String) {
    WECHAT("微信", "wechat_bill", "微信账单"),
    ALIPAY("支付宝", "alipay_bill", "支付宝账单"),
}

data class BillRow(
    val time: Long,
    val type: TxType,
    val amountCents: Long,
    val counterpart: String,
    val goods: String,
    val billCategory: String,
    val method: String,
    val status: String,
    val orderId: String,
    val note: String,
)

data class ParsedBill(val source: BillSource, val rows: List<BillRow>, val skipped: Map<String, Int>)

class BillException(message: String) : Exception(message)

/**
 * Reads the bill files WeChat Pay (xlsx) and Alipay (csv, often GBK) export "for personal reconciliation".
 * Columns are found by their header names, so small format changes and the older Alipay layout still work.
 */
object BillImporter {
    private const val MAX_BYTES = 20 * 1024 * 1024

    fun parse(fileName: String, bytes: ByteArray, zone: ZoneId = ZoneId.systemDefault()): ParsedBill {
        if (bytes.size > MAX_BYTES) throw BillException("文件太大（超过 20MB）")
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val table = when {
            isZip && fileName.lowercase().endsWith(".zip") ->
                throw BillException("这是压缩包，请先解压（密码在微信/支付宝发给你的消息里），再选里面的 .xlsx 或 .csv 文件")
            isZip -> readXlsx(bytes)
            else -> readCsv(bytes)
        }
        return fromTable(table, fileName, zone)
    }

    // ---- table -> rows ------------------------------------------------------------------

    fun fromTable(table: List<List<String>>, fileName: String = "", zone: ZoneId = ZoneId.systemDefault()): ParsedBill {
        val headerIdx = table.indexOfFirst { row -> row.any { it == "收/支" } && row.any { it.contains("金额") } }
        if (headerIdx < 0) throw BillException("没找到账单表头（收/支、金额），这个文件可能不是微信或支付宝导出的账单")
        val header = table[headerIdx]
        val preamble = table.take(headerIdx).flatten().joinToString(" ")
        val source = when {
            preamble.contains("微信") || fileName.contains("微信") -> BillSource.WECHAT
            preamble.contains("支付宝") || fileName.contains("支付宝") || fileName.lowercase().contains("alipay") -> BillSource.ALIPAY
            header.any { it == "交易分类" || it == "商品说明" } -> BillSource.ALIPAY
            else -> BillSource.WECHAT
        }

        fun col(vararg names: String): Int {
            for (n in names) header.indexOfFirst { it == n }.let { if (it >= 0) return it }
            for (n in names) header.indexOfFirst { it.contains(n) }.let { if (it >= 0) return it }
            return -1
        }
        val cTime = col("交易时间", "付款时间", "交易创建时间")
        val cDir = col("收/支")
        val cAmount = col("金额(元)", "金额（元）", "金额")
        val cParty = col("交易对方")
        val cGoods = col("商品说明", "商品名称", "商品")
        val cCat = col("交易分类", "交易类型", "类型")
        val cMethod = col("收/付款方式", "支付方式")
        val cStatus = col("交易状态", "当前状态")
        val cId = col("交易订单号", "交易单号", "交易号")
        val cNote = col("备注")
        if (cTime < 0 || cAmount < 0) throw BillException("账单缺少交易时间或金额列")

        val rows = mutableListOf<BillRow>()
        val skipped = linkedMapOf<String, Int>()
        fun skip(reason: String) { skipped[reason] = (skipped[reason] ?: 0) + 1 }

        for (r in table.drop(headerIdx + 1)) {
            fun cell(i: Int) = if (i in r.indices) clean(r[i]) else ""
            if (r.all { it.isBlank() }) continue
            val timeText = cell(cTime)
            val time = parseTime(timeText, zone)
            if (time == null) {
                // Alipay puts a footer ("----") after the table.
                if (timeText.isNotEmpty() && !timeText.startsWith("-")) skip("时间看不懂")
                continue
            }
            val type = when (cell(cDir)) {
                "支出" -> TxType.EXPENSE
                "收入" -> TxType.INCOME
                else -> { skip("不计收支（转账到自己、理财等）"); continue }
            }
            val status = cell(cStatus)
            if (status.contains("关闭") || status.contains("失败")) { skip("交易关闭/失败"); continue }
            val amount = parseAmount(cell(cAmount))
            if (amount == null || amount <= 0) { skip("金额为 0 或看不懂"); continue }
            rows += BillRow(
                time = time, type = type, amountCents = amount,
                counterpart = cell(cParty).takeUnless { it == "/" }.orEmpty(),
                goods = cell(cGoods).takeUnless { it == "/" }.orEmpty(),
                billCategory = cell(cCat).takeUnless { it == "/" }.orEmpty(),
                method = cell(cMethod).takeUnless { it == "/" }.orEmpty(),
                status = status,
                orderId = cell(cId).takeUnless { it == "/" }.orEmpty(),
                note = cell(cNote).takeUnless { it == "/" }.orEmpty(),
            )
        }
        return ParsedBill(source, rows, skipped)
    }

    private fun clean(s: String) = s.replace(" ", " ").trim().trim('\t').trim()

    private val TIME_FORMATS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/M/d H:mm:ss", "yyyy/M/d H:mm", "yyyy/MM/dd HH:mm:ss",
    ).map { DateTimeFormatter.ofPattern(it) }

    fun parseTime(text: String, zone: ZoneId): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        for (f in TIME_FORMATS) {
            try {
                return LocalDateTime.parse(t, f).atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        // Excel serial date (days since 1899-12-30) when a sheet stores time as a number.
        t.toDoubleOrNull()?.let { serial ->
            if (serial > 30000 && serial < 80000) {
                val days = serial.toLong()
                val secs = ((serial - days) * 86400).toLong()
                return LocalDate.of(1899, 12, 30).plusDays(days).atStartOfDay(zone).plusSeconds(secs).toInstant().toEpochMilli()
            }
        }
        return null
    }

    fun parseAmount(text: String): Long? {
        val t = text.replace("¥", "").replace("￥", "").replace(",", "").replace("元", "").trim()
        return try {
            BigDecimal(t).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    // ---- CSV ----------------------------------------------------------------------------

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("GBK"))
        }
    }

    fun readCsv(bytes: ByteArray): List<List<String>> = parseCsv(decode(bytes))

    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row.add(cell.toString()); cell.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = mutableListOf() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.map { r -> r.map { it.trim() } }
    }

    // ---- XLSX (zip of XML; no library needed) ---------------------------------------------

    fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name == "xl/sharedStrings.xml" || (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))) {
                    files[name] = zip.readBytes()
                }
            }
        }
        val sheetName = files.keys.filter { it.startsWith("xl/worksheets/") }
            .minByOrNull { it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: 999 }
            ?: throw BillException("表格里没有工作表")
        val shared = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val doc = xml(files.getValue(sheetName))
        val rows = mutableListOf<List<String>>()
        val rowNodes = doc.getElementsByTagName("row")
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as Element
            val cells = rowEl.getElementsByTagName("c")
            val values = sortedMapOf<Int, String>()
            for (j in 0 until cells.length) {
                val c = cells.item(j) as Element
                val col = columnIndex(c.getAttribute("r")).takeIf { it >= 0 } ?: (values.keys.lastOrNull()?.plus(1) ?: 0)
                val type = c.getAttribute("t")
                val v = when (type) {
                    "s" -> firstText(c, "v").toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> allText(c, "t")
                    else -> firstText(c, "v")
                }
                values[col] = v
            }
            val width = (values.keys.maxOrNull() ?: -1) + 1
            rows.add(List(width) { values[it] ?: "" })
        }
        return rows
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val doc = xml(bytes)
        val si = doc.getElementsByTagName("si")
        return List(si.length) { allText(si.item(it) as Element, "t") }
    }

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun firstText(el: Element, tag: String): String {
        val n = el.getElementsByTagName(tag)
        return if (n.length > 0) n.item(0).textContent ?: "" else ""
    }

    private fun allText(el: Element, tag: String): String {
        val n = el.getElementsByTagName(tag)
        return buildString { for (i in 0 until n.length) append(n.item(i).textContent ?: "") }
    }

    /** "C12" -> 2 */
    fun columnIndex(ref: String): Int {
        var idx = 0
        var any = false
        for (ch in ref) {
            if (ch in 'A'..'Z') { idx = idx * 26 + (ch - 'A' + 1); any = true } else break
        }
        return if (any) idx - 1 else -1
    }

    // ---- reconcile ----------------------------------------------------------------------

    /** What the app already has, reduced to what matching needs. */
    data class Existing(val id: Long, val type: TxType, val amountCents: Long?, val channel: String, val time: Long, val ref: String)

    enum class Outcome { MATCHED, FILL, MISSING, ALREADY_IMPORTED }

    data class Line(val row: BillRow, val outcome: Outcome, val existingId: Long? = null)

    /**
     * MATCHED: the app already has this payment (same direction and amount within [windowMs]).
     * FILL: a pending entry without amount (watch pay, Alipay transfer) at the same time -> fill its amount.
     * MISSING: not recorded -> offer to add. ALREADY_IMPORTED: this order number was imported before.
     */
    fun reconcile(
        source: BillSource,
        rows: List<BillRow>,
        existing: List<Existing>,
        importedRefs: Set<String>,
        windowMs: Long = 10 * 60_000L,
    ): List<Line> {
        val used = HashSet<Long>()
        return rows.sortedBy { it.time }.map { row ->
            if (row.orderId.isNotEmpty() && row.orderId in importedRefs) return@map Line(row, Outcome.ALREADY_IMPORTED)
            val exact = existing
                .filter { it.id !in used && it.amountCents == row.amountCents && it.type == row.type }
                .filter { it.channel == source.channel || it.channel == "手动" || it.channel.isEmpty() }
                .filter { abs(it.time - row.time) <= windowMs }
                .minByOrNull { abs(it.time - row.time) }
            if (exact != null) { used += exact.id; return@map Line(row, Outcome.MATCHED, exact.id) }
            val pending = existing
                .filter { it.id !in used && it.amountCents == null && it.type == row.type && it.channel == source.channel }
                .filter { abs(it.time - row.time) <= windowMs }
                .minByOrNull { abs(it.time - row.time) }
            if (pending != null) { used += pending.id; return@map Line(row, Outcome.FILL, pending.id) }
            Line(row, Outcome.MISSING)
        }
    }

    // ---- category guess -----------------------------------------------------------------

    private val ALIPAY_CATEGORIES = mapOf(
        "餐饮美食" to "餐饮", "交通出行" to "交通", "日用百货" to "日用", "服饰装扮" to "购物", "数码电器" to "购物",
        "家居家装" to "购物", "美容美发" to "购物", "母婴亲子" to "购物", "充值缴费" to "生活缴费", "生活服务" to "生活缴费",
        "住房物业" to "生活缴费", "通讯物流" to "通讯", "文化休闲" to "娱乐", "酒店旅游" to "娱乐", "运动户外" to "娱乐",
        "爱车养车" to "交通", "教育培训" to "学习", "医疗健康" to "医疗", "退款" to "退款",
    )

    fun guessCategory(row: BillRow, kind: String): String {
        val c = row.billCategory
        if (row.type == TxType.INCOME) {
            return when {
                c.contains("退款") || row.status.contains("退款") -> "退款"
                // Alipay files transfers under "转账红包"; WeChat red packets are "微信红包".
                c.contains("转账") || c.contains("收款") -> "转账"
                c.contains("红包") -> "红包"
                else -> Categorizer.guess(TxType.INCOME, row.counterpart, kind)
            }
        }
        ALIPAY_CATEGORIES[c]?.let { return it }
        if (c.contains("红包")) return "人情"
        return Categorizer.guess(TxType.EXPENSE, row.counterpart + " " + row.goods, kind)
    }
}
