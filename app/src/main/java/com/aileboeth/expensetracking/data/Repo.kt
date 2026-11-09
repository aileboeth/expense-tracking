package com.aileboeth.expensetracking.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Search filter; null fields mean "any". */
data class TxFilter(
    val query: String = "",
    val type: TxType? = null,
    val channel: String? = null,
    val category: String? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
    val start: Long? = null,
    val end: Long? = null,
)

/** Local SQLite store. Calls are blocking: run them off the main thread (except tiny lookups). */
object Repo {
    private lateinit var helper: Helper

    private val _version = MutableStateFlow(0L)
    /** Bumped after every write; screens reload when it changes. */
    val version: StateFlow<Long> = _version

    /** Called after every write (the widget listens). */
    @Volatile var onChanged: (() -> Unit)? = null

    fun init(ctx: Context) {
        if (!::helper.isInitialized) helper = Helper(ctx.applicationContext)
    }

    private val db: SQLiteDatabase get() = helper.writableDatabase
    private fun changed() {
        _version.value = _version.value + 1
        onChanged?.invoke()
    }

    // ---- transactions -------------------------------------------------------------------

    fun insert(tx: Tx): Long = db.insert("tx", null, values(tx)).also { changed() }

    /** Several inserts/updates in one transaction, one change notification. */
    fun batch(block: Batch.() -> Unit) {
        db.beginTransaction()
        try {
            Batch().block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    class Batch {
        fun insert(tx: Tx): Long = db.insert("tx", null, values(tx))
        fun update(tx: Tx) { db.update("tx", values(tx), "id=?", arrayOf(tx.id.toString())) }
    }

    fun update(tx: Tx) {
        db.update("tx", values(tx), "id=?", arrayOf(tx.id.toString()))
        changed()
    }

    fun delete(id: Long) {
        db.delete("tx", "id=?", arrayOf(id.toString()))
        changed()
    }

    fun get(id: Long): Tx? = db.query("tx", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
        if (c.moveToFirst()) read(c) else null
    }

    private fun list(where: String?, args: Array<String>?, order: String = "time DESC, id DESC"): List<Tx> =
        db.query("tx", null, where, args, null, null, order).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    /** Confirmed entries with start <= time < end, newest first. */
    fun listRange(start: Long, end: Long): List<Tx> =
        list("status='OK' AND time>=? AND time<?", arrayOf(start.toString(), end.toString()))

    /** Everything (confirmed and pending) in a time range, for reconciling an imported bill. */
    fun listAll(start: Long, end: Long): List<Tx> =
        list("time>=? AND time<?", arrayOf(start.toString(), end.toString()), "time ASC, id ASC")

    fun pending(): List<Tx> = list("status='PENDING'", null)

    fun all(): List<Tx> = list(null, null, "time ASC, id ASC")

    fun sum(type: TxType, start: Long, end: Long): Long =
        db.rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM tx WHERE status='OK' AND type=? AND time>=? AND time<?",
            arrayOf(type.name, start.toString(), end.toString()),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    fun refs(refs: Collection<String>): Set<String> {
        if (refs.isEmpty()) return emptySet()
        val found = HashSet<String>()
        refs.chunked(400).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.rawQuery("SELECT ref FROM tx WHERE ref IN ($marks)", chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) found.add(c.getString(0))
            }
        }
        return found
    }

    fun search(f: TxFilter, limit: Int = 500): List<Tx> {
        val where = mutableListOf("status='OK'")
        val args = mutableListOf<String>()
        if (f.query.isNotBlank()) {
            where += "(note LIKE ? OR counterpart LIKE ? OR category LIKE ? OR raw LIKE ? OR channel LIKE ?)"
            val like = "%" + f.query.trim().replace("%", "").replace("_", "") + "%"
            repeat(5) { args += like }
        }
        f.type?.let { where += "type=?"; args += it.name }
        f.channel?.let { where += "channel=?"; args += it }
        f.category?.let { where += "category=?"; args += it }
        f.minCents?.let { where += "amount>=?"; args += it.toString() }
        f.maxCents?.let { where += "amount<=?"; args += it.toString() }
        f.start?.let { where += "time>=?"; args += it.toString() }
        f.end?.let { where += "time<?"; args += it.toString() }
        return list(where.joinToString(" AND "), args.toTypedArray(), "time DESC, id DESC LIMIT $limit")
    }

    /** Categories of [type] ordered by how often they were used since [since]; unused ones follow in their usual order. */
    fun categoriesByUse(type: TxType, since: Long): List<String> {
        val used = db.rawQuery(
            "SELECT category, COUNT(*) c FROM tx WHERE status='OK' AND type=? AND time>=? AND category<>'' GROUP BY category ORDER BY c DESC",
            arrayOf(type.name, since.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        val all = categories(type)
        // 其他 is the fallback for skipped categories; it stays last no matter how often it is used.
        val ranked = used.filter { it in all && it != "其他" } + all.filter { it !in used && it != "其他" }
        return if ("其他" in all) ranked + "其他" else ranked
    }

    /** The amounts paid most often since [since] (most frequent first, then most recent). */
    fun frequentAmounts(type: TxType, since: Long, limit: Int): List<Long> =
        db.rawQuery(
            "SELECT amount, COUNT(*) c, MAX(time) t FROM tx WHERE status='OK' AND type=? AND time>=? AND amount IS NOT NULL " +
                "GROUP BY amount ORDER BY c DESC, t DESC LIMIT ?",
            arrayOf(type.name, since.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    /** Payers / merchants seen in recent entries, most frequent first. */
    fun recentCounterparts(limit: Int): List<String> =
        db.rawQuery(
            "SELECT counterpart, COUNT(*) c, MAX(time) t FROM tx WHERE counterpart<>'' GROUP BY counterpart ORDER BY c DESC, t DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun channels(): List<String> =
        db.rawQuery("SELECT DISTINCT channel FROM tx WHERE channel<>'' ORDER BY channel", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun values(tx: Tx) = ContentValues().apply {
        put("type", tx.type.name)
        if (tx.amountCents == null) putNull("amount") else put("amount", tx.amountCents)
        put("category", tx.category)
        put("note", tx.note)
        put("channel", tx.channel)
        put("counterpart", tx.counterpart)
        put("time", tx.time)
        put("status", tx.status.name)
        put("source", tx.source)
        put("kind", tx.kind)
        put("raw", tx.raw)
        put("ref", tx.ref)
    }

    private fun read(c: Cursor): Tx {
        fun s(n: String) = c.getString(c.getColumnIndexOrThrow(n)) ?: ""
        val amountIdx = c.getColumnIndexOrThrow("amount")
        return Tx(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            type = runCatching { TxType.valueOf(s("type")) }.getOrDefault(TxType.EXPENSE),
            amountCents = if (c.isNull(amountIdx)) null else c.getLong(amountIdx),
            category = s("category"),
            note = s("note"),
            channel = s("channel"),
            counterpart = s("counterpart"),
            time = c.getLong(c.getColumnIndexOrThrow("time")),
            status = runCatching { Status.valueOf(s("status")) }.getOrDefault(Status.OK),
            source = s("source"),
            kind = s("kind"),
            raw = s("raw"),
            ref = s("ref"),
        )
    }

    // ---- duplicate guard ----------------------------------------------------------------

    /** True the first time a notification signature is seen; false for a re-post of the same message. */
    fun markSeen(signature: String, now: Long = System.currentTimeMillis()): Boolean {
        val v = ContentValues().apply { put("sig", signature); put("time", now) }
        val fresh = db.insertWithOnConflict("seen", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        if (fresh) db.delete("seen", "time<?", arrayOf((now - 7L * 24 * 3600 * 1000).toString()))
        return fresh
    }

    // ---- recognition log (what the listener saw and decided) ----------------------------

    fun log(pkg: String, title: String, text: String, result: String) {
        val v = ContentValues().apply {
            put("time", System.currentTimeMillis()); put("pkg", pkg); put("title", title)
            put("text", text.take(500)); put("result", result)
        }
        db.insert("log", null, v)
        db.execSQL("DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 300)")
    }

    fun logs(): List<LogEntry> = db.query("log", null, null, null, null, null, "id DESC").use { c ->
        buildList {
            while (c.moveToNext()) add(LogEntry(
                c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "",
                c.getString(4) ?: "", c.getString(5) ?: "",
            ))
        }
    }

    // ---- categories ---------------------------------------------------------------------

    fun categories(type: TxType): List<String> =
        db.query("category", arrayOf("name"), "type=?", arrayOf(type.name), null, null, "sort ASC, id ASC")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** How many entries of [type] use each category. */
    fun categoryCounts(type: TxType): Map<String, Int> =
        db.rawQuery("SELECT category, COUNT(*) FROM tx WHERE type=? AND category<>'' GROUP BY category", arrayOf(type.name))
            .use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) } }

    fun addCategory(type: TxType, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val v = ContentValues().apply { put("name", n); put("type", type.name); put("sort", 1000) }
        db.insertWithOnConflict("category", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        changed()
    }

    fun deleteCategory(type: TxType, name: String) {
        db.delete("category", "type=? AND name=?", arrayOf(type.name, name))
        changed()
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "ledger.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE tx (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    amount INTEGER,
                    category TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT '',
                    channel TEXT NOT NULL DEFAULT '',
                    counterpart TEXT NOT NULL DEFAULT '',
                    time INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    source TEXT NOT NULL,
                    kind TEXT NOT NULL DEFAULT '',
                    raw TEXT NOT NULL DEFAULT '',
                    ref TEXT NOT NULL DEFAULT '')"""
            )
            db.execSQL("CREATE INDEX tx_time ON tx(time)")
            db.execSQL("CREATE INDEX tx_status ON tx(status)")
            db.execSQL("CREATE INDEX tx_ref ON tx(ref)")
            db.execSQL("CREATE TABLE seen (sig TEXT PRIMARY KEY, time INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE log (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, pkg TEXT, title TEXT, text TEXT, result TEXT)")
            db.execSQL("CREATE TABLE category (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL, sort INTEGER NOT NULL, UNIQUE(name, type))")
            DEFAULT_EXPENSE.forEachIndexed { i, n -> seed(db, TxType.EXPENSE, n, i) }
            DEFAULT_INCOME.forEachIndexed { i, n -> seed(db, TxType.INCOME, n, i) }
        }

        private fun seed(db: SQLiteDatabase, type: TxType, name: String, sort: Int) {
            db.insert("category", null, ContentValues().apply { put("name", name); put("type", type.name); put("sort", sort) })
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                // 0.1.1: order number of an imported bill line, so the same bill is not imported twice.
                db.execSQL("ALTER TABLE tx ADD COLUMN ref TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS tx_ref ON tx(ref)")
            }
        }
    }

    val DEFAULT_EXPENSE = listOf("餐饮", "零食饮料", "交通", "购物", "日用", "娱乐", "生活缴费", "通讯", "学习", "医疗", "人情", "其他")
    val DEFAULT_INCOME = listOf("工资", "兼职", "转账", "红包", "退款", "其他")
}
