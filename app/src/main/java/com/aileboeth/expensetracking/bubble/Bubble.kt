package com.aileboeth.expensetracking.bubble

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.Kinds
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * The small round bubble at the screen edge. It never takes focus or opens the app, so whatever
 * the user is doing keeps working. Tapping it opens a small card to fix the amount, pick a
 * category and note what the money was for.
 */
@SuppressLint("StaticFieldLeak") // only the application context is kept
object Bubble {
    private val main = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var wm: WindowManager? = null

    private var ball: TextView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var card: View? = null

    /** Entries shown by the bubble in this session, oldest first. */
    private val queue = ArrayDeque<Long>()
    private val autoHide = Runnable { hideIfOnlyDone() }

    private const val EXPENSE_COLOR = 0xFFE5533D.toInt()
    private const val INCOME_COLOR = 0xFF2E9E6A.toInt()
    private const val PENDING_COLOR = 0xFFF2A007.toInt()
    private const val INK = 0xFF1F2421.toInt()
    private const val MUTED = 0xFF6B736E.toInt()
    private const val ACCENT = 0xFF2E7D5B.toInt()

    fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun show(ctx: Context, txId: Long) {
        val app = ctx.applicationContext
        if (!Prefs(app).bubbleEnabled || !canDraw(app)) return
        appCtx = app
        wm = app.getSystemService(WindowManager::class.java)
        queue.remove(txId)
        queue.addLast(txId)
        if (card != null) return // the open card picks it up with "下一笔"
        try {
            showBall()
        } catch (_: Throwable) {
            return
        }
        scheduleHide()
    }

    /** Call after an entry was edited or deleted elsewhere (in the app), so the bubble does not show stale data. */
    fun refresh() {
        main.post {
            if (ball == null || card != null) return@post
            queue.removeAll { Repo.get(it) == null }
            if (queue.isEmpty()) { hideAll(); return@post }
            refreshBall()
            scheduleHide()
        }
    }

    /** Re-show the bubble for entries still waiting for an amount (e.g. from the app). */
    fun showPending(ctx: Context) {
        Repo.pending().asReversed().forEach { show(ctx, it.id) }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, appCtx!!.resources.displayMetrics).toInt()

    private fun overlayParams(w: Int, h: Int, focusable: Boolean) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // ---- ball ---------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun showBall() {
        val ctx = appCtx!!
        val size = dp(54f)
        val v = ball ?: TextView(ctx).also { tv ->
            tv.gravity = Gravity.CENTER
            tv.setTextColor(Color.WHITE)
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.elevation = dp(6f).toFloat()
            tv.setOnTouchListener(BallTouch())
            val dm = ctx.resources.displayMetrics
            val p = overlayParams(size, size, focusable = false).apply {
                x = dm.widthPixels - size - dp(6f)
                y = (dm.heightPixels * 0.32f).toInt()
            }
            ballParams = p
            wm!!.addView(tv, p)
            ball = tv
        }
        refreshBall(v)
    }

    private fun refreshBall(target: TextView? = ball) {
        val v = target ?: return
        val items = queue.mapNotNull { Repo.get(it) }
        val head = items.lastOrNull { it.status == Status.PENDING } ?: items.lastOrNull()
        if (head == null) {
            hideAll(); return
        }
        val waiting = items.count { it.status == Status.PENDING }
        val color = when {
            head.status == Status.PENDING -> PENDING_COLOR
            head.type == TxType.EXPENSE -> EXPENSE_COLOR
            else -> INCOME_COLOR
        }
        val ring = budgetRing()
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(if (ring == Color.WHITE) 2f else 3.5f), ring)
        }
        val label = if (head.amountCents == null) "?" else shortAmount(head)
        v.text = if (items.size > 1) "$label\n${items.size}笔" else label
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 5) 11f else 13f)
        v.contentDescription = "记账：${Money.signed(head)}，$waiting 笔待补"
    }

    /** White normally; amber from 80% of the monthly budget, red once it is used up. */
    private fun budgetRing(): Int {
        val (spent, budget) = budgetState() ?: return Color.WHITE
        return when {
            spent >= budget -> 0xFFFF3B30.toInt()
            spent * 5 >= budget * 4 -> 0xFFFFC107.toInt()
            else -> Color.WHITE
        }
    }

    private fun budgetState(): Pair<Long, Long>? {
        val budget = Prefs(appCtx!!).budgetCents
        if (budget <= 0) return null
        val zone = ZoneId.systemDefault()
        val m = YearMonth.now()
        val start = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return Repo.sum(TxType.EXPENSE, start, end) to budget
    }

    private fun shortAmount(tx: Tx): String {
        val a = tx.amountCents ?: return "?"
        val sign = if (tx.type == TxType.EXPENSE) "-" else "+"
        val yuan = if (a % 100 == 0L) (a / 100).toString() else Money.format(a)
        return sign + yuan
    }

    private fun scheduleHide() {
        main.removeCallbacks(autoHide)
        val anyPending = queue.any { Repo.get(it)?.status == Status.PENDING }
        if (!anyPending) main.postDelayed(autoHide, Prefs(appCtx!!).bubbleSeconds * 1000L)
    }

    private fun hideIfOnlyDone() {
        if (card != null) return
        val anyPending = queue.any { Repo.get(it)?.status == Status.PENDING }
        if (!anyPending) hideAll()
    }

    private fun hideAll() {
        main.removeCallbacks(autoHide)
        closeCard()
        ball?.let { runCatching { wm?.removeView(it) } }
        ball = null
        queue.clear()
    }

    private class BallTouch : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var downAt = 0L

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = ballParams ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false
                    downAt = System.currentTimeMillis()
                    main.removeCallbacks(autoHide)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) {
                        p.x = startX + dx.toInt(); p.y = startY + dy.toInt()
                        runCatching { wm?.updateViewLayout(v, p) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        snapToEdge(v, p)
                        scheduleHide()
                    } else if (System.currentTimeMillis() - downAt > 600) {
                        // Long press: put it away. Entries without amount stay in the app's list.
                        hideAll()
                    } else {
                        openCard()
                    }
                }
                MotionEvent.ACTION_CANCEL -> scheduleHide()
            }
            return true
        }

        private fun snapToEdge(v: View, p: WindowManager.LayoutParams) {
            val dm = v.resources.displayMetrics
            p.x = if (p.x + v.width / 2 < dm.widthPixels / 2) dp(6f) else dm.widthPixels - v.width - dp(6f)
            p.y = p.y.coerceIn(dp(40f), dm.heightPixels - v.height - dp(60f))
            runCatching { wm?.updateViewLayout(v, p) }
        }
    }

    // ---- card ---------------------------------------------------------------------------

    private fun currentEntry(): Tx? {
        val items = queue.mapNotNull { Repo.get(it) }
        return items.firstOrNull { it.status == Status.PENDING } ?: items.firstOrNull()
    }

    private fun openCard() {
        val tx = currentEntry() ?: run { hideAll(); return }
        main.removeCallbacks(autoHide)
        closeCard()
        ball?.visibility = View.GONE

        val ctx = ContextThemeWrapper(appCtx!!, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val dm = ctx.resources.displayMetrics
        val width = minOf(dm.widthPixels - dp(24f), dp(380f))

        var type = tx.type
        var category = tx.category

        val root = object : LinearLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    collapse(); return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(10f))
            background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(Color.WHITE) }
            elevation = dp(10f).toFloat()
        }

        val time = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(tx.time))
        root.addView(TextView(ctx).apply {
            text = listOf(tx.channel, Kinds.label(tx.kind), time).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            setTextColor(MUTED); textSize = 12f
        })
        val detail = listOf(tx.counterpart, tx.raw).filter { it.isNotBlank() }.distinct().joinToString("  ")
        if (detail.isNotBlank()) root.addView(TextView(ctx).apply {
            text = detail; setTextColor(MUTED); textSize = 12f; maxLines = 2
        })

        budgetState()?.let { (spent, budget) ->
            val left = budget - spent
            root.addView(TextView(ctx).apply {
                text = if (left >= 0) "本月预算还剩 ¥${Money.format(left)}" else "本月已超预算 ¥${Money.format(-left)}"
                setTextColor(if (left >= 0) MUTED else EXPENSE_COLOR); textSize = 12f
            })
        }

        // amount + type
        val amount = EditText(ctx).apply {
            hint = if (tx.amountCents == null) "这笔多少钱？" else "金额"
            setText(tx.amountCents?.let { Money.format(it) } ?: "")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 24f
            setTextColor(INK)
            typeface = Typeface.DEFAULT_BOLD
        }
        val expenseBtn = pill(ctx, "支出")
        val incomeBtn = pill(ctx, "收入")
        val chips = FlowLayout(ctx, dp(6f))

        fun renderChips() {
            chips.removeAllViews()
            for (name in Repo.categories(type)) {
                val chip = pill(ctx, CategoryStyle.label(name))
                styleSelected(chip, name == category)
                chip.setOnClickListener {
                    category = if (category == name) "" else name
                    renderChips()
                }
                chips.addView(chip)
            }
        }
        fun renderType() {
            styleSelected(expenseBtn, type == TxType.EXPENSE, EXPENSE_COLOR)
            styleSelected(incomeBtn, type == TxType.INCOME, INCOME_COLOR)
        }
        expenseBtn.setOnClickListener { if (type != TxType.EXPENSE) { type = TxType.EXPENSE; category = ""; renderType(); renderChips() } }
        incomeBtn.setOnClickListener { if (type != TxType.INCOME) { type = TxType.INCOME; category = ""; renderType(); renderChips() } }

        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply { text = "¥"; textSize = 22f; setTextColor(INK) })
            addView(amount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(expenseBtn)
            addView(View(ctx), LinearLayout.LayoutParams(dp(6f), 1))
            addView(incomeBtn)
        })

        root.addView(TextView(ctx).apply { text = "分类"; setTextColor(MUTED); textSize = 12f; setPadding(0, dp(6f), 0, dp(4f)) })
        root.addView(chips)

        val note = EditText(ctx).apply {
            hint = "用途 / 备注（可不填）"
            setText(tx.note)
            textSize = 15f
            isSingleLine = true
            setTextColor(INK)
        }
        root.addView(note)

        val left = queue.mapNotNull { Repo.get(it) }.count { it.id != tx.id }
        if (left > 0) root.addView(TextView(ctx).apply {
            text = "还有 $left 笔，保存后显示下一笔"; setTextColor(MUTED); textSize = 12f
        })

        val delete = textButton(ctx, "不是这笔/删除", EXPENSE_COLOR)
        val later = textButton(ctx, "收起", MUTED)
        val save = textButton(ctx, "保存", ACCENT)
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6f), 0, 0)
            addView(delete)
            addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            addView(later)
            addView(save)
        })

        renderType()
        renderChips()

        save.setOnClickListener {
            val text = amount.text.toString()
            val cents = if (text.isBlank()) null else Money.parse(text)
            if (text.isNotBlank() && cents == null) {
                Toast.makeText(appCtx, "金额不对，例：12.5", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val updated = tx.copy(
                type = type, amountCents = cents, category = category, note = note.text.toString().trim(),
                status = if (cents == null) Status.PENDING else Status.OK,
            )
            Thread { Repo.update(updated) }.start()
            if (cents == null) Toast.makeText(appCtx, "先放进待补列表，之后在 App 里补金额", Toast.LENGTH_SHORT).show()
            queue.remove(tx.id)
            next()
        }
        delete.setOnClickListener {
            Thread { Repo.delete(tx.id) }.start()
            queue.remove(tx.id)
            next()
        }
        later.setOnClickListener { collapse() }

        val frame = FrameLayout(ctx).apply { addView(root, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        val p = overlayParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, focusable = true).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72f)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        wm!!.addView(frame, p)
        card = frame
        if (tx.amountCents == null) amount.post {
            amount.requestFocus()
            ctx.getSystemService(InputMethodManager::class.java)?.showSoftInput(amount, 0)
        }
    }

    /** After save/delete: next waiting entry, or close everything. */
    private fun next() {
        closeCard()
        // Let the background write land before we read the queue again.
        main.postDelayed({
            val rest = queue.mapNotNull { Repo.get(it) }
            if (rest.isEmpty()) hideAll() else openCard()
        }, 150)
    }

    private fun collapse() {
        closeCard()
        ball?.visibility = View.VISIBLE
        refreshBall()
        scheduleHide()
    }

    private fun closeCard() {
        card?.let { runCatching { wm?.removeView(it) } }
        card = null
    }

    // ---- little view helpers ------------------------------------------------------------

    private fun pill(ctx: Context, label: String) = TextView(ctx).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        isClickable = true
    }

    private fun styleSelected(v: TextView, selected: Boolean, color: Int = ACCENT) {
        v.background = GradientDrawable().apply {
            cornerRadius = dp(16f).toFloat()
            if (selected) setColor(color) else { setColor(0xFFF1F3F1.toInt()); setStroke(dp(1f), 0xFFDDE2DE.toInt()) }
        }
        v.setTextColor(if (selected) Color.WHITE else INK)
    }

    private fun textButton(ctx: Context, label: String, color: Int) = Button(ctx, null, android.R.attr.borderlessButtonStyle).apply {
        text = label
        isAllCaps = false
        setTextColor(color)
        textSize = 15f
    }
}

/** Wraps children onto new lines, like chips. */
private class FlowLayout(ctx: Context, private val gap: Int) : ViewGroup(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += rowH + gap; rowH = 0 }
            x += c.measuredWidth + gap
            rowH = maxOf(rowH, c.measuredHeight)
        }
        setMeasuredDimension(maxW, y + rowH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += rowH + gap; rowH = 0 }
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            x += c.measuredWidth + gap
            rowH = maxOf(rowH, c.measuredHeight)
        }
    }
}
