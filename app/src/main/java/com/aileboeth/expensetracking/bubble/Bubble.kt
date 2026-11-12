package com.aileboeth.expensetracking.bubble

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Categorizer
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.Kinds
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import com.aileboeth.expensetracking.ui.MainActivity
import com.aileboeth.expensetracking.ui.Labels
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * After a payment: a purple capsule docked on the screen edge (lavender when the amount is missing).
 * It never takes focus, so the current app keeps working. Tapping it opens
 *  - a drawer at the TOP for entries that already have an amount: tap a category = saved;
 *  - a card at the BOTTOM for entries without amount: usual amounts to tap (or type another).
 * A category is optional: "保存" without one files the entry under 其他.
 */
@SuppressLint("StaticFieldLeak") // only the application context is kept
object Bubble {
    private val main = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var wm: WindowManager? = null

    private var capsule: LinearLayout? = null
    private var capsuleParams: WindowManager.LayoutParams? = null
    private var onLeft = false
    private var card: View? = null

    /** Entries shown by the capsule in this session, oldest first. */
    private val queue = ArrayDeque<Long>()
    private val autoHide = Runnable { hideIfOnlyDone() }

    const val OTHER = "其他"
    const val ACTION_USUAL_AMOUNTS = "com.aileboeth.expensetracking.USUAL_AMOUNTS"

    private const val BRAND = 0xFF5B45E0.toInt()
    private const val BRAND_2 = 0xFF8F7BFF.toInt()
    private const val BRAND_DEEP = 0xFF3F2CC4.toInt()
    private const val SOFT = 0xFFECE8FF.toInt()
    private const val INK = 0xFF1D1A2B.toInt()
    private const val MUTED = 0xFF6C6880.toInt()
    private const val CHIP = 0xFFF3F1FA.toInt()
    private const val LINE = 0xFFDCD8E8.toInt()
    private const val EXPENSE = 0xFFE5533D.toInt()
    private const val INCOME = 0xFF2E9E6A.toInt()
    private const val INCOME_2 = 0xFF5FD3A0.toInt()
    private const val PENDING_1 = 0xFFF4F1FF.toInt()
    private const val PENDING_2 = 0xFFDCD4FF.toInt()
    private const val SCRIM = 0x3A120C28

    /** True while this app's own screen is in front: the capsule hides there and comes back afterwards. */
    @Volatile private var appInFront = false

    fun setAppInFront(front: Boolean) {
        main.post {
            appInFront = front
            if (card == null) capsule?.visibility = if (front) View.GONE else View.VISIBLE
        }
    }

    fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun show(ctx: Context, txId: Long) {
        val app = ctx.applicationContext
        if (!Prefs(app).bubbleEnabled || !canDraw(app)) return
        appCtx = app
        wm = app.getSystemService(WindowManager::class.java)
        queue.remove(txId)
        queue.addLast(txId)
        if (card != null) return // the open card shows the next one when it closes
        try {
            showCapsule()
        } catch (_: Throwable) {
            return
        }
        scheduleHide()
    }

    /** Opens the card for one entry right away (the widget's "fill in"). False when overlays are not allowed. */
    fun openFor(ctx: Context, txId: Long): Boolean {
        val app = ctx.applicationContext
        if (!canDraw(app) || Repo.get(txId) == null) return false
        appCtx = app
        wm = app.getSystemService(WindowManager::class.java)
        queue.remove(txId)
        queue.addFirst(txId)
        return try {
            showCapsule()
            openCard()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Call after an entry was edited or deleted elsewhere (in the app). */
    fun refresh() {
        main.post {
            if (capsule == null || card != null) return@post
            queue.removeAll { Repo.get(it) == null }
            if (queue.isEmpty()) { hideAll(); return@post }
            refreshCapsule()
            scheduleHide()
        }
    }

    /** Show the capsule again for entries still waiting for an amount. */
    fun showPending(ctx: Context) {
        Repo.pending().asReversed().forEach { show(ctx, it.id) }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, appCtx!!.resources.displayMetrics).toInt()

    private fun items() = queue.mapNotNull { Repo.get(it) }
    private fun head(): Tx? = items().let { l -> l.firstOrNull { it.amountCents == null } ?: l.lastOrNull() }

    // ---- capsule ------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun showCapsule() {
        val ctx = appCtx!!
        if (capsule == null) {
            val icon = FrameLayout(ctx).apply {
                addView(ImageView(ctx).apply {
                    setImageResource(R.mipmap.ic_launcher_mono)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    scaleX = 1.7f; scaleY = 1.7f
                }, FrameLayout.LayoutParams(dp(30f), dp(30f)))
            }
            val text = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 1
            }
            val v = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                elevation = dp(6f).toFloat()
                addView(icon, LinearLayout.LayoutParams(dp(30f), dp(30f)))
                addView(text)
                setOnTouchListener(CapsuleTouch())
            }
            val dm = ctx.resources.displayMetrics
            val p = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44f),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = (dm.heightPixels * 0.34f).toInt()
            }
            capsuleParams = p
            capsule = v
            wm!!.addView(v, p)
        }
        capsule?.visibility = if (appInFront) View.GONE else View.VISIBLE
        refreshCapsule()
    }

    private fun refreshCapsule() {
        val v = capsule ?: return
        val p = capsuleParams ?: return
        val all = items()
        val t = head() ?: run { hideAll(); return }
        val pending = t.amountCents == null
        val icon = v.getChildAt(if (v.getChildAt(0) is FrameLayout) 0 else 1) as FrameLayout
        val text = v.getChildAt(if (v.getChildAt(0) is TextView) 0 else 1) as TextView

        // Icon on the inner side: [icon][amount] on the right edge, [amount][icon] on the left.
        v.removeAllViews()
        if (onLeft) { v.addView(text); v.addView(icon, LinearLayout.LayoutParams(dp(30f), dp(30f))) }
        else { v.addView(icon, LinearLayout.LayoutParams(dp(30f), dp(30f))); v.addView(text) }
        (text.layoutParams as LinearLayout.LayoutParams).apply {
            leftMargin = if (onLeft) 0 else dp(7f)
            rightMargin = if (onLeft) dp(7f) else 0
        }
        v.setPadding(if (onLeft) dp(14f) else dp(7f), 0, if (onLeft) dp(7f) else dp(14f), 0)

        val r = dp(22f).toFloat()
        val (c1, c2, c3) = when {
            pending -> Triple(PENDING_1, PENDING_2, PENDING_2)
            t.type == TxType.INCOME -> Triple(INCOME_2, INCOME, INCOME)
            else -> Triple(BRAND_2, BRAND, BRAND_DEEP)
        }
        v.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2, c3)).apply {
            cornerRadii = if (onLeft) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f) else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            if (overBudget(t)) setStroke(dp(2f), EXPENSE)
        }
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (pending) BRAND else 0x38FFFFFF)
        }
        val label = if (pending) s(R.string.bub_pending) else Money.signed(t).let { x -> if (x.endsWith(".00")) x.dropLast(3) else x }
        text.text = if (all.size > 1) s(R.string.bub_count, label, all.size) else label
        text.setTextColor(if (pending) BRAND_DEEP else Color.WHITE)
        v.contentDescription = s(R.string.bub_desc, label)

        p.gravity = Gravity.TOP or (if (onLeft) Gravity.START else Gravity.END)
        p.x = 0
        runCatching { wm?.updateViewLayout(v, p) }
    }

    private fun overBudget(t: Tx): Boolean {
        if (t.type != TxType.EXPENSE) return false
        val budget = Prefs(appCtx!!).budgetCents
        if (budget <= 0) return false
        val zone = ZoneId.systemDefault()
        val m = YearMonth.now()
        val spent = Repo.sum(TxType.EXPENSE, m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli())
        return spent >= budget
    }

    private fun scheduleHide() {
        main.removeCallbacks(autoHide)
        val anyPending = queue.any { Repo.get(it)?.status == Status.PENDING }
        if (!anyPending) main.postDelayed(autoHide, Prefs(appCtx!!).bubbleSeconds * 1000L)
    }

    private fun hideIfOnlyDone() {
        if (card != null) return
        if (queue.none { Repo.get(it)?.status == Status.PENDING }) hideAll()
    }

    private fun hideAll() {
        main.removeCallbacks(autoHide)
        closeCard()
        capsule?.let { runCatching { wm?.removeView(it) } }
        capsule = null
        queue.clear()
    }

    private class CapsuleTouch : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startY = 0
        private var moved = false
        private var downAt = 0L

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = capsuleParams ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startY = p.y; moved = false
                    downAt = System.currentTimeMillis()
                    main.removeCallbacks(autoHide)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - downX) > 12 || abs(e.rawY - downY) > 12) moved = true
                    if (moved) {
                        val dm = v.resources.displayMetrics
                        p.y = (startY + (e.rawY - downY).toInt()).coerceIn(dp(40f), dm.heightPixels - dp(120f))
                        runCatching { wm?.updateViewLayout(v, p) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        onLeft = e.rawX < v.resources.displayMetrics.widthPixels / 2f
                        refreshCapsule()
                        scheduleHide()
                    } else if (System.currentTimeMillis() - downAt > 600) {
                        hideAll() // long press: put it away; entries without amount stay in the app's list
                    } else {
                        openCard()
                    }
                }
                MotionEvent.ACTION_CANCEL -> scheduleHide()
            }
            return true
        }
    }

    // ---- card ---------------------------------------------------------------------------

    private fun openCard() {
        val t = head() ?: run { hideAll(); return }
        main.removeCallbacks(autoHide)
        closeCard()
        capsule?.visibility = View.GONE
        val ctx = ContextThemeWrapper(appCtx!!, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val pending = t.amountCents == null

        val root = object : FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss(t); return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setBackgroundColor(SCRIM)
        root.setOnClickListener { dismiss(t) }

        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // taps inside do not reach the scrim
            val r = dp(22f).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = if (pending) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
            elevation = dp(10f).toFloat()
        }
        root.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            if (pending) Gravity.BOTTOM else Gravity.TOP))

        // Clear the status bar (top drawer) or the gesture bar (bottom card), plus breathing room.
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            if (pending) sheet.setPadding(dp(16f), dp(14f), dp(16f), dp(30f) + insets.systemWindowInsetBottom)
            else sheet.setPadding(dp(16f), dp(12f) + insets.systemWindowInsetTop, dp(16f), dp(16f))
            insets
        }
        if (pending) sheet.setPadding(dp(16f), dp(14f), dp(16f), dp(30f)) else sheet.setPadding(dp(16f), dp(36f), dp(16f), dp(16f))

        if (pending) buildPending(ctx, sheet, t) else buildWithAmount(ctx, sheet, t)

        val p = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT,
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        wm!!.addView(root, p)
        card = root
    }

    /** Top drawer: amount, categories (tap = saved), note + 保存. */
    private fun buildWithAmount(ctx: Context, sheet: LinearLayout, t: Tx) {
        var type = t.type
        val amount = TextView(ctx).apply {
            textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        }
        fun paintAmount() {
            amount.text = (if (type == TxType.EXPENSE) "-" else "+") + Money.format(t.amountCents ?: 0)
            amount.setTextColor(if (type == TxType.INCOME) INCOME else INK)
        }
        paintAmount()
        val more = moreRow(ctx, t, canFlip = true) { type = if (type == TxType.EXPENSE) TxType.INCOME else TxType.EXPENSE; paintAmount() }
        sheet.addView(headRow(ctx, amount, meta(t)) { more.visibility = if (more.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
        sheet.addView(more)
        val note = noteField(ctx, t.note)
        val cats = categoryRow(ctx, type) { cat -> save(t.copy(type = type), cat, note.text.toString()) }
        sheet.addView(cats, spaced())
        noteHelpers(ctx, sheet, note, { type }, t.counterpart, defaultHint = null) { guess ->
            sheet.addView(noteRow(ctx, note, saveButton(ctx) {
                // A note the app recognises beats the payer-based first guess; otherwise keep what the entry has.
                val cat = guess() ?: t.category.takeUnless { c -> c.isBlank() || c == OTHER } ?: OTHER
                save(t.copy(type = type), cat, note.text.toString())
            }), spaced(8f))
        }
    }

    /**
     * Recent notes as chips (tap = fill the note), the note row added by [addNoteRow], and a hint
     * under it saying which category 保存 will use. Returns the note-based guess for the save button.
     */
    private fun noteHelpers(
        ctx: Context, sheet: LinearLayout, note: EditText, type: () -> TxType, counterpart: String,
        defaultHint: String?, addNoteRow: (() -> String?) -> Unit,
    ) {
        fun guess(): String? = note.text.toString().trim().takeIf { it.isNotEmpty() }?.let { Categorizer.fromNote(type(), it, counterpart) }
        val notes = Repo.recentNotes(type(), System.currentTimeMillis() - 90L * 24 * 3600 * 1000, 8)
        if (notes.isNotEmpty()) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (n in notes) {
                row.addView(chip(ctx, n) { note.setText(n); note.setSelection(n.length) })
                row.addView(View(ctx), LinearLayout.LayoutParams(dp(6f), 1))
            }
            sheet.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }, spaced())
        }
        addNoteRow(::guess)
        val hint = TextView(ctx).apply { textSize = 11f; setTextColor(MUTED) }
        fun paint() {
            val g = guess()
            hint.text = if (g != null) s(R.string.bub_note_guess, Labels.category(ctx, g)) else defaultHint.orEmpty()
            hint.setTextColor(if (g != null) BRAND else MUTED)
            hint.visibility = if (hint.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        note.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = paint()
        })
        paint()
        sheet.addView(hint, spaced(8f))
    }

    /** Bottom card: usual amounts (or type one), categories, note + 保存. */
    private fun buildPending(ctx: Context, sheet: LinearLayout, t: Tx) {
        val prefs = Prefs(appCtx!!)
        val usual = prefs.usualAmounts(t.type).ifEmpty {
            Repo.frequentAmounts(t.type, System.currentTimeMillis() - 60L * 24 * 3600 * 1000, if (t.type == TxType.EXPENSE) 4 else 3)
        }
        val amount = EditText(ctx).apply {
            hint = s(R.string.card_how_much)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK); setHintTextColor(0xFFB9B4CC.toInt())
            background = null
            setPadding(0, 0, 0, 0)
            isSingleLine = true
        }
        val save = saveButton(ctx) {}
        val chips = FlowLayout(ctx, dp(6f))
        val more = moreRow(ctx, t, canFlip = false) {}
        val yuan = TextView(ctx).apply { text = Money.sym; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK); visibility = View.GONE }
        val amountBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = "amountBox"
            addView(yuan)
            addView(amount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        sheet.addView(headRow(ctx, amountBox, meta(t)) { more.visibility = if (more.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
        sheet.addView(more)

        fun amountCents() = Money.parse(amount.text.toString())
        fun paintChips() {
            chips.removeAllViews()
            val current = amountCents()
            for (c in usual) {
                chips.addView(chip(ctx, Money.sym + Money.short(c), selected = current == c && !amount.hasFocus()) {
                    amount.setText(Money.short(c))
                    amount.clearFocus()
                    hideKeyboard(amount)
                    paintChips()
                })
            }
            chips.addView(chip(ctx, s(R.string.card_other_amount), selected = amount.hasFocus(), accent = true) {
                if (current != null && current in usual) amount.setText("")
                amount.requestFocus()
                showKeyboard(amount)
                paintChips()
            })
            chips.addView(chip(ctx, s(R.string.card_edit), dashed = true) {
                val i = Intent(appCtx, MainActivity::class.java).setAction(ACTION_USUAL_AMOUNTS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                collapse()
                runCatching { appCtx!!.startActivity(i) }
            })
        }
        amount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                save.isEnabled = amountCents() != null
                save.alpha = if (save.isEnabled) 1f else 0.4f
                yuan.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
        amount.setOnFocusChangeListener { _, _ -> paintChips() }
        sheet.addView(chips, spaced())

        val note = noteField(ctx, t.note)
        fun commit(cat: String) {
            val cents = amountCents()
            if (cents == null) { toast(s(R.string.bub_pick_amount)); return }
            save(t.copy(amountCents = cents), cat, note.text.toString())
        }
        sheet.addView(categoryRow(ctx, t.type) { cat -> commit(cat) }, spaced())
        noteHelpers(ctx, sheet, note, { t.type }, t.counterpart, defaultHint = s(R.string.bub_hint)) { guess ->
            save.setOnClickListener { commit(guess() ?: t.category.takeUnless { it.isBlank() || it == OTHER } ?: OTHER) }
            save.isEnabled = false; save.alpha = 0.4f
            sheet.addView(noteRow(ctx, note, save), spaced(8f))
        }
        paintChips()
        // Nothing to pick from yet: go straight to typing.
        if (usual.isEmpty()) amount.postDelayed({ amount.requestFocus(); showKeyboard(amount) }, 250)
    }

    // ---- pieces -------------------------------------------------------------------------

    private fun meta(t: Tx): String {
        val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(t.time))
        val left = queue.size - 1
        return listOf(Labels.channel(appCtx!!, t.channel), if (t.amountCents == null) Labels.kind(appCtx!!, t.kind) else "", time).filter { it.isNotBlank() }.joinToString(" · ") +
            if (left > 0) s(R.string.bub_more_left, left) else ""
    }

    private fun headRow(ctx: Context, amount: View, meta: String, onMore: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(amount, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (amount.tag == "amountBox") { width = 0; weight = 1f }
        })
        addView(TextView(ctx).apply {
            text = meta; textSize = 11f; setTextColor(MUTED); maxLines = 1
            setPadding(dp(8f), 0, dp(8f), 0)
        }, if (amount.tag == "amountBox") LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(ctx).apply {
            text = "⋯"; textSize = 16f; setTextColor(INK); gravity = Gravity.CENTER
            background = round(CHIP, 12f)
            setOnClickListener { onMore() }
            contentDescription = s(R.string.bub_more)
        }, LinearLayout.LayoutParams(dp(36f), dp(30f)))
    }

    /** Hidden row behind "⋯": switch expense/income, delete. */
    private fun moreRow(ctx: Context, t: Tx, canFlip: Boolean, onFlip: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        setPadding(0, dp(10f), 0, 0)
        if (canFlip) addView(chip(ctx, s(R.string.bub_flip)) { onFlip() })
        addView(View(ctx), LinearLayout.LayoutParams(dp(6f), 1))
        addView(chip(ctx, s(R.string.bub_not_this), danger = true) {
            Thread { Repo.delete(t.id) }.start()
            queue.remove(t.id)
            toast(s(R.string.bub_deleted))
            next()
        })
    }

    private fun categoryRow(ctx: Context, type: TxType, onPick: (String) -> Unit): View {
        val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (name in Repo.categoriesByUse(type, since)) {
            val st = CategoryStyle.of(name)
            row.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(ctx).apply {
                    text = st.emoji; textSize = 19f; gravity = Gravity.CENTER
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(CHIP) }
                }, LinearLayout.LayoutParams(dp(42f), dp(42f)))
                addView(TextView(ctx).apply {
                    text = Labels.category(ctx, name); textSize = 10.5f; setTextColor(MUTED); gravity = Gravity.CENTER; maxLines = 1
                })
                setOnClickListener { onPick(name) }
                contentDescription = name
            }, LinearLayout.LayoutParams(dp(54f), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun noteField(ctx: Context, initial: String) = EditText(ctx).apply {
        hint = s(R.string.bub_note_hint)
        setText(initial)
        textSize = 14f
        isSingleLine = true
        setTextColor(INK); setHintTextColor(0xFF9A95AE.toInt())
        background = round(CHIP, 12f)
        setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
    }

    private fun noteRow(ctx: Context, note: EditText, save: TextView) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(note, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(View(ctx), LinearLayout.LayoutParams(dp(8f), 1))
        addView(save)
    }

    private fun saveButton(ctx: Context, onClick: () -> Unit) = TextView(ctx).apply {
        text = s(R.string.save)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = round(BRAND, 12f)
        setPadding(dp(18f), dp(9f), dp(18f), dp(9f))
        setOnClickListener { onClick() }
    }

    private fun chip(
        ctx: Context, label: String, selected: Boolean = false, accent: Boolean = false,
        dashed: Boolean = false, danger: Boolean = false, onClick: () -> Unit,
    ) = TextView(ctx).apply {
        text = label
        textSize = 13.5f
        typeface = if (label.startsWith(Money.sym)) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(when { danger -> EXPENSE; selected || accent -> BRAND; dashed -> MUTED; else -> INK })
        gravity = Gravity.CENTER
        setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
        background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            when {
                selected -> { setColor(SOFT); setStroke(dp(1.5f), BRAND) }
                dashed -> { setColor(Color.TRANSPARENT); setStroke(dp(1f), LINE, dp(4f).toFloat(), dp(3f).toFloat()) }
                else -> setColor(CHIP)
            }
        }
        setOnClickListener { onClick() }
    }

    private fun round(color: Int, radiusDp: Float) = GradientDrawable().apply { cornerRadius = dp(radiusDp).toFloat(); setColor(color) }

    private fun spaced(topDp: Float = 12f) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(topDp) }

    private fun showKeyboard(v: EditText) {
        val imm = appCtx!!.getSystemService(InputMethodManager::class.java)
        // The overlay only gets input focus a moment after it is shown; vivo ignores an earlier request.
        val run = Runnable { if (v.isAttachedToWindow) { v.requestFocus(); imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) } }
        v.postDelayed(run, 120)
        v.postDelayed(run, 450)
    }

    private fun hideKeyboard(v: View) {
        appCtx!!.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun s(id: Int, vararg args: Any): String = appCtx!!.getString(id, *args)

    private fun toast(msg: String) = Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()

    // ---- actions ------------------------------------------------------------------------

    private fun save(t: Tx, category: String, note: String) {
        val updated = t.copy(category = category.ifBlank { OTHER }, note = note.trim(), status = Status.OK)
        Thread { Repo.update(updated); Repo.markActive() }.start()
        card?.takeIf { Prefs(it.context).fxHaptics }?.let { c ->
            c.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
        queue.remove(t.id)
        val label = CategoryStyle.of(updated.category).emoji + " " + Money.signed(updated).let { x -> if (x.endsWith(".00")) x.dropLast(3) else x }
        if (queue.isEmpty() && capsule != null && !appInFront) {
            closeCard()
            flashDone(label)
        } else {
            toast(s(R.string.bub_saved, Labels.category(appCtx!!, updated.category), Money.signed(updated)))
            next()
        }
    }

    /** The capsule turns into "✓ 🍜 -30", pops once and slides away. */
    private fun flashDone(label: String) {
        val v = capsule ?: return hideAll()
        main.removeCallbacks(autoHide)
        v.visibility = View.VISIBLE
        val text = (0 until v.childCount).map { v.getChildAt(it) }.firstOrNull { it is TextView } as? TextView
        text?.text = "✓ $label"
        text?.setTextColor(Color.WHITE)
        val r = dp(22f).toFloat()
        v.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(INCOME_2, INCOME, INCOME)).apply {
            cornerRadii = if (onLeft) floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f) else floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        }
        v.scaleX = 0.8f; v.scaleY = 0.8f
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(160).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
        main.postDelayed({
            v.animate().alpha(0f).translationX(if (onLeft) -v.width.toFloat() else v.width.toFloat()).setDuration(260)
                .withEndAction {
                    if (queue.isEmpty()) hideAll() else {
                        // a new payment came in meanwhile: show it instead
                        v.alpha = 1f; v.translationX = 0f
                        refreshCapsule(); scheduleHide()
                    }
                }.start()
        }, 1100)
    }

    /** Tap on the dim area / back: entries with an amount are already saved; amount-less ones stay waiting. */
    private fun dismiss(t: Tx) {
        if (t.amountCents != null) {
            if (t.category.isBlank()) Thread { Repo.update(t.copy(category = OTHER)) }.start()
            queue.remove(t.id)
        }
        next()
    }

    /** Hide the card but keep the capsule (e.g. while the app edits the usual amounts). */
    private fun collapse() {
        closeCard()
        capsule?.visibility = if (appInFront) View.GONE else View.VISIBLE
        refreshCapsule()
        scheduleHide()
    }

    /** After save/delete/dismiss: the next waiting entry, or close everything. */
    private fun next() {
        closeCard()
        main.postDelayed({
            val rest = items()
            when {
                rest.isEmpty() -> hideAll()
                rest.any { it.amountCents == null } -> collapse()
                else -> openCard()
            }
        }, 200)
    }

    private fun closeCard() {
        card?.let { c ->
            hideKeyboard(c)
            runCatching { wm?.removeView(c) }
        }
        card = null
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
