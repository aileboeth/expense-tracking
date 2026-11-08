package com.aileboeth.expensetracking.bill

import com.aileboeth.expensetracking.parse.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Layouts follow the public WeChat Pay / Alipay "personal reconciliation" exports; names are made up. */
class BillImporterTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun ms(s: String) = LocalDateTime.parse(s.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli()

    private val alipayCsv = """
        ------------------------------------------------------------------------------------
        导出信息：
        姓名：某某
        支付宝账户：123****@qq.com
        起始时间：[2026-11-01 00:00:00]    终止时间：[2026-11-08 23:59:59]
        ------------------------支付宝（中国）网络技术有限公司  电子客户回单------------------------
        交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,
        2026-11-08 15:00:42,餐饮美食,某某食堂,/,午饭,支出,30.00,余额宝,交易成功,2026110822001400001	,T001	,,
        2026-11-08 15:01:08,餐饮美食,某某食堂,/,奶茶,支出,30.00,余额宝,交易成功,2026110822001400002	,T002	,,
        2026-11-07 17:36:46,生活服务,慧生活798校园版,/,热水,支出,0.00,花呗,交易成功,2026110722001400003	,,,
        2026-11-07 17:34:56,转账红包,某公司,/,收益提现,收入,12.00,,交易成功,2026110722001400004	,,,
        2026-11-06 10:00:00,投资理财,余额宝,/,转入,不计收支,100.00,,交易成功,2026110622001400005	,,,
        2026-11-06 09:00:00,日用百货,某超市,/,纸巾,支出,"1,005.50",花呗,交易关闭,2026110622001400006	,,,
        ------------------------------------------------------------------------------------
    """.trimIndent()

    @Test fun alipayGbkCsv() {
        val bill = BillImporter.parse("支付宝交易明细(20261101-20261108).csv", alipayCsv.toByteArray(Charset.forName("GBK")), zone)
        assertEquals(BillSource.ALIPAY, bill.source)
        assertEquals(3, bill.rows.size)
        val lunch = bill.rows[0]
        assertEquals(ms("2026-11-08 15:00:42"), lunch.time)
        assertEquals(TxType.EXPENSE, lunch.type)
        assertEquals(3000L, lunch.amountCents)
        assertEquals("某某食堂", lunch.counterpart)
        assertEquals("午饭", lunch.goods)
        assertEquals("2026110822001400001", lunch.orderId)
        assertEquals(TxType.INCOME, bill.rows[2].type)
        assertEquals(1, bill.skipped["不计收支（转账到自己、理财等）"])
        assertEquals(1, bill.skipped["交易关闭/失败"])
        assertEquals(1, bill.skipped["金额为 0 或看不懂"])
    }

    @Test fun utf8CsvWithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + alipayCsv.toByteArray(Charsets.UTF_8)
        assertEquals(3, BillImporter.parse("alipay.csv", bytes, zone).rows.size)
    }

    private fun xlsx(rows: List<List<String>>): ByteArray {
        val strings = rows.flatten().distinct()
        val ss = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            strings.forEach { append("<si><t>").append(it.replace("&", "&amp;").replace("<", "&lt;")).append("</t></si>") }
            append("</sst>")
        }
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { r, row ->
                append("<row r=\"${r + 1}\">")
                row.forEachIndexed { c, v ->
                    if (v.isNotEmpty()) append("<c r=\"${'A' + c}${r + 1}\" t=\"s\"><v>${strings.indexOf(v)}</v></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write(ss.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    private val wechatRows = listOf(
        listOf("微信支付账单明细"),
        listOf("微信昵称：[某某]"),
        listOf("起始时间：[2026-11-01 00:00:00] 终止时间：[2026-11-08 23:59:59]"),
        listOf("----------------------微信支付账单明细列表--------------------"),
        listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态", "交易单号", "商户单号", "备注"),
        listOf("2026-11-08 14:24:35", "商户消费", "某便利店", "饮料", "支出", "¥9.99", "零钱", "支付成功", "4200001", "M1", "/"),
        listOf("2026-11-07 17:30:37", "扫二维码付款", "某餐厅", "/", "支出", "¥23.50", "零钱", "支付成功", "4200002", "M2", "/"),
        listOf("2026-11-07 12:00:00", "微信红包", "某人", "/", "收入", "¥6.66", "/", "已存入零钱", "4200003", "/", "/"),
        listOf("2026-11-06 21:22:53", "零钱提现", "招商银行", "/", "/", "¥50.00", "零钱", "提现已到账", "4200004", "/", "/"),
    )

    @Test fun wechatXlsx() {
        val bill = BillImporter.parse("微信支付账单(20261101-20261108).xlsx", xlsx(wechatRows), zone)
        assertEquals(BillSource.WECHAT, bill.source)
        assertEquals(3, bill.rows.size)
        assertEquals(999L, bill.rows[0].amountCents)
        assertEquals("某便利店", bill.rows[0].counterpart)
        assertEquals("", bill.rows[1].goods)
        assertEquals(TxType.INCOME, bill.rows[2].type)
        assertEquals("红包", BillImporter.guessCategory(bill.rows[2], "wechat_bill"))
        assertEquals(1, bill.skipped["不计收支（转账到自己、理财等）"])
    }

    @Test fun zipIsRefusedWithHint() {
        try {
            BillImporter.parse("微信支付账单.zip", xlsx(wechatRows), zone)
            throw AssertionError("expected BillException")
        } catch (e: BillException) {
            assertTrue(e.message!!.contains("解压"))
        }
    }

    @Test fun notABill() {
        try {
            BillImporter.parse("x.csv", "a,b\n1,2\n".toByteArray(), zone)
            throw AssertionError("expected BillException")
        } catch (e: BillException) {
            assertTrue(e.message!!.contains("表头"))
        }
    }

    @Test fun reconcileOutcomes() {
        val bill = BillImporter.parse("wx.xlsx", xlsx(wechatRows), zone)
        val existing = listOf(
            // Recorded from the notification 3 seconds before the bill time.
            BillImporter.Existing(1, TxType.EXPENSE, 999, "微信", ms("2026-11-08 14:24:32"), ""),
            // Watch payment recorded without amount.
            BillImporter.Existing(2, TxType.EXPENSE, null, "微信", ms("2026-11-07 17:30:37"), ""),
        )
        val lines = BillImporter.reconcile(BillSource.WECHAT, bill.rows, existing, importedRefs = setOf("4200003"))
        val byId = lines.associateBy { it.row.orderId }
        assertEquals(BillImporter.Outcome.MATCHED, byId.getValue("4200001").outcome)
        assertEquals(1L, byId.getValue("4200001").existingId)
        assertEquals(BillImporter.Outcome.FILL, byId.getValue("4200002").outcome)
        assertEquals(2L, byId.getValue("4200002").existingId)
        assertEquals(BillImporter.Outcome.ALREADY_IMPORTED, byId.getValue("4200003").outcome)
    }

    @Test fun twoSameAmountPaymentsMatchTwoEntries() {
        val bill = BillImporter.parse("alipay.csv", alipayCsv.toByteArray(Charset.forName("GBK")), zone)
        val existing = listOf(
            BillImporter.Existing(1, TxType.EXPENSE, 3000, "支付宝", ms("2026-11-08 15:00:42"), ""),
        )
        val lines = BillImporter.reconcile(BillSource.ALIPAY, bill.rows, existing, emptySet())
        val thirty = lines.filter { it.row.amountCents == 3000L }
        assertEquals(listOf(BillImporter.Outcome.MATCHED, BillImporter.Outcome.MISSING), thirty.map { it.outcome })
    }

    @Test fun alipayCategoryMapping() {
        val bill = BillImporter.parse("alipay.csv", alipayCsv.toByteArray(Charset.forName("GBK")), zone)
        assertEquals("餐饮", BillImporter.guessCategory(bill.rows[0], "alipay_bill"))
        assertEquals("转账", BillImporter.guessCategory(bill.rows[2], "alipay_bill"))
    }

    @Test fun columnIndex() {
        assertEquals(0, BillImporter.columnIndex("A1"))
        assertEquals(2, BillImporter.columnIndex("C12"))
        assertEquals(26, BillImporter.columnIndex("AA3"))
    }
}
