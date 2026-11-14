package com.aileboeth.expensetracking.capture

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.parse.Parser

/**
 * Full auto (optional, off until the user turns it on in the system's accessibility settings): reads
 * the text of WeChat / Alipay screens — the 支付成功 page, the 微信支付 chat, a transfer's detail —
 * so payments without a notification are recorded and amounts the notification left out get filled.
 * Nothing is sent anywhere; only WeChat and Alipay screens are read (and, while "自动去读金额" is on,
 * which app is in front, to know when the home screen is showing).
 */
class ScreenService : AccessibilityService() {

    companion object {
        @Volatile var instance: ScreenService? = null
            private set

        val connected: Boolean get() = instance != null

        /** Debug builds: a screen of our own that starts with this line poses as WeChat / Alipay. */
        const val POSE = "[as "

        fun enabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, ScreenService::class.java)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun open(ctx: Context) {
            val ok = runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (!ok) runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        /** Re-apply which apps the service listens to (after the 自动去读金额 switch changed). */
        fun reconfigure() { instance?.configure() }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val debugPose by lazy { packageName.endsWith(".debug") }
    private val watched by lazy { buildSet { add(Parser.WECHAT); add(Parser.ALIPAY); if (debugPose) add(packageName) } }
    private var readScheduled = false
    /** The app in front (from window changes), for [AutoFetch]. */
    @Volatile var foreground = ""
        private set
    /** Same, but counting this app too (the debug stand-in screen is ours). */
    @Volatile var rawForeground = ""
        private set
    val isDebug: Boolean get() = debugPose
    val homePackages: Set<String> by lazy {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet() - setOf("com.android.settings", "com.android.fallbackhome")
    }
    private val ignoredForeground by lazy { setOf("com.android.systemui", packageName, "android") }

    override fun onServiceConnected() {
        instance = this
        Repo.init(this)
        configure()
        ScreenLink.note("全自动已连接")
    }

    fun configure() {
        val info = serviceInfo ?: return
        val prefs = Prefs(this)
        // Only WeChat / Alipay, unless auto-fetch needs to know when the home screen is in front.
        info.packageNames = if (prefs.autoFetch) null else watched.toTypedArray()
        // Screen samples on: every kind of event, to find out what still comes through when a window can't be read.
        info.eventTypes = if (prefs.screenSamples) AccessibilityEvent.TYPES_ALL_MASK
            else AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        serviceInfo = info
    }

    /** Recent WeChat / Alipay events (type, class, the text the event itself carries), for the diagnostics sample. */
    private val recentEvents = ArrayDeque<String>()
    /** The last read found no readable window: try the event's own source node next time. */
    @Volatile private var lastReadFailed = false
    private var pendingSource: AccessibilityNodeInfo? = null

    private fun track(e: AccessibilityEvent, pkg: String) {
        val type = AccessibilityEvent.eventTypeToString(e.eventType).removePrefix("TYPE_")
        val cls = e.className?.toString()?.substringAfterLast('.').orEmpty()
        val text = e.text.joinToString(" / ") { it?.toString().orEmpty() }.let(ScreenSamples::keep)
        val desc = e.contentDescription?.toString()?.let(ScreenSamples::keep).orEmpty()
        val who = if (pkg == Parser.ALIPAY) "支付宝" else "微信"
        synchronized(recentEvents) {
            recentEvents.addLast("$who $type $cls 窗口${e.windowId} 文字[$text]" + if (desc.isNotEmpty()) " 描述[$desc]" else "")
            while (recentEvents.size > 12) recentEvents.removeFirst()
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val pkg = e?.packageName?.toString() ?: return
        if (pkg in watched && Prefs(this).screenSamples) {
            runCatching { track(e, pkg) }
            // Notification events carry the notification itself; the listener handles those.
            if (e.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        }
        if (pkg in watched && lastReadFailed && pendingSource == null) pendingSource = try { e.source } catch (_: Throwable) { null }
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) rawForeground = pkg
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg !in ignoredForeground &&
            e.className?.toString()?.contains("Toast") != true
        ) {
            foreground = pkg
            ScreenLink.lastWindow = System.currentTimeMillis() to pkg
            AutoFetch.onForeground(this, pkg)
        }
        if (pkg !in watched) return
        lastEventPkg = pkg
        // Pages fire many change events; read at most every 400 ms.
        if (!readScheduled) {
            readScheduled = true
            handler.postDelayed({ readScheduled = false; readNow() }, 400)
        }
    }

    private var lastEventPkg = ""
    private var lastUnreadableNote = 0L

    private fun readNow() {
        // The active window first; else any window on screen from WeChat / Alipay (an app clone, a pop-up on top).
        val active = try { rootInActiveWindow } catch (_: Throwable) { null }
        var root = active?.takeIf { it.packageName?.toString() in watched }
            ?: try { windows.mapNotNull { it.root }.firstOrNull { it.packageName?.toString() in watched } } catch (_: Throwable) { null }
        if (root == null) {
            // Last resort: the node the event came from, climbed up to its top.
            val src = pendingSource
            pendingSource = null
            root = src?.let(::topOf)?.takeIf { it.packageName?.toString() in watched }
            if (root != null && lastReadFailed) ScreenLink.note("窗口读不到，改从事件来源读到了内容")
        }
        if (root == null) {
            lastReadFailed = true
            // Events come from WeChat / Alipay but no window content can be read (e.g. an app clone in another user).
            val now = System.currentTimeMillis()
            if (now - lastUnreadableNote > 30_000) {
                lastUnreadableNote = now
                ScreenLink.note("收到${if (lastEventPkg == Parser.ALIPAY) "支付宝" else "微信"}的画面变化，但读不到内容（前台窗口：${active?.packageName ?: "读不到"}）")
            }
            if (Prefs(this).screenSamples && now - lastDiag > 8_000) {
                lastDiag = now
                runCatching { ScreenSamples.addDiag(this, lastEventPkg, diagLines(active), now) }
            }
            return
        }
        lastReadFailed = false
        var pkg = root.packageName?.toString() ?: return
        var lines = collect(root)
        if (pkg == packageName) {
            // Our own screens are never read, except the debug stand-in for WeChat / Alipay.
            val first = lines.firstOrNull().orEmpty()
            if (!debugPose || !first.startsWith(POSE)) return
            pkg = first.removePrefix(POSE).removeSuffix("]").trim()
            lines = lines.drop(1)
        }
        if (lines.isEmpty()) return
        ScreenLink.onScreen(this, pkg, lines)
    }

    private var lastDiag = 0L

    private fun topOf(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var cur = n
        repeat(60) { cur = (try { cur.parent } catch (_: Throwable) { null }) ?: return cur }
        return cur
    }

    /** What can be seen when no WeChat / Alipay window is readable: the windows on screen and the recent events. */
    private fun diagLines(active: AccessibilityNodeInfo?): List<String> {
        val out = ArrayList<String>()
        out += "【诊断】读不到窗口内容 前台=${active?.packageName ?: "读不到"} 前台窗口=${active?.windowId ?: "-"}"
        try {
            for (w in windows) {
                val r = try { w.root } catch (_: Throwable) { null }
                out += "窗口${w.id} 类型${w.type} 层${w.layer} 活动=${w.isActive} 标题[${w.title?.toString()?.let(ScreenSamples::keepTitle).orEmpty()}] " +
                    "内容=${r?.packageName ?: "读不到"} 子节点${r?.childCount ?: 0}"
            }
        } catch (e: Throwable) {
            out += "窗口列表出错：${e.javaClass.simpleName}"
        }
        synchronized(recentEvents) { recentEvents.forEach { out += "事件 $it" } }
        return out
    }

    /** All visible text in reading order (text, else the content description), at most 600 lines. */
    private fun collect(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && out.size < 600 && visited < 3000) {
            val n = stack.removeLast()
            visited++
            if (!n.isVisibleToUser) continue
            val t = n.text?.toString()?.trim().orEmpty().ifEmpty { n.contentDescription?.toString()?.trim().orEmpty() }
            if (t.isNotEmpty()) t.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { out += it }
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it) }
        }
        return out
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        AutoFetch.cancel()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }
}

/**
 * 自动去读金额 (optional): a watch payment's notification has no amount, but the chat it opens does.
 * When the phone is unlocked and on the home screen, open that chat for a few seconds, let
 * [ScreenLink] read the card, and go back home. Each entry is tried once; never over another app.
 */
object AutoFetch {
    private class Job(val txId: Long, val pkg: String, val open: PendingIntent, val created: Long, var tried: Boolean = false)

    private val main = Handler(Looper.getMainLooper())
    private val jobs = LinkedHashMap<Long, Job>()
    private var running: Job? = null
    private const val EXPIRE = 3 * 3600_000L
    // Bill pages are web pages and can take a while to load.
    private const val TIMEOUT = 15_000L
    private val timeout = Runnable { finish("超时，没读到：" + ScreenLink.lastScan.ifEmpty { "这段时间没收到微信/支付宝的画面" }) }
    private val check = Runnable { ScreenService.instance?.let { maybeStart(it) } }

    fun offer(ctx: Context, txId: Long, pkg: String, open: PendingIntent?) {
        if (open == null) return
        main.post {
            jobs[txId] = Job(txId, pkg, open, System.currentTimeMillis())
            main.removeCallbacks(check); main.postDelayed(check, 1500)
        }
    }

    fun done(txId: Long) {
        main.post {
            jobs.remove(txId)
            if (running?.txId == txId) finish("读到了")
        }
    }

    fun onForeground(svc: ScreenService, pkg: String) {
        if (running == null && jobs.isNotEmpty() && pkg in svc.homePackages) {
            main.removeCallbacks(check); main.postDelayed(check, 1200)
        }
    }

    fun cancel() = main.post { running = null; main.removeCallbacks(timeout) }

    private fun maybeStart(svc: ScreenService) {
        if (running != null || !Prefs(svc).autoFetch) return
        val now = System.currentTimeMillis()
        jobs.values.removeAll { now - it.created > EXPIRE || it.tried }
        val job = jobs.values.firstOrNull() ?: return
        val pm = svc.getSystemService(PowerManager::class.java)
        val kg = svc.getSystemService(KeyguardManager::class.java)
        if (!pm.isInteractive || kg.isKeyguardLocked || svc.foreground !in svc.homePackages) return
        Thread {
            Repo.init(svc)
            val tx = Repo.get(job.txId)
            main.post {
                if (tx == null || tx.amountCents != null || tx.status != Status.PENDING) { jobs.remove(job.txId); return@post }
                if (running != null || svc.foreground !in svc.homePackages) return@post
                job.tried = true
                running = job
                ScreenLink.lastScan = ""
                ScreenLink.note("自动打开${if (job.pkg == Parser.ALIPAY) "支付宝" else "微信"}读 #${job.txId} 的金额")
                try {
                    val opts = ActivityOptions.makeBasic()
                    if (Build.VERSION.SDK_INT >= 34) {
                        @Suppress("DEPRECATION")
                        opts.setPendingIntentBackgroundActivityStartMode(
                            if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        )
                    }
                    job.open.send(svc, 0, null, null, null, null, opts.toBundle())
                    main.postDelayed(timeout, TIMEOUT)
                } catch (e: Throwable) {
                    running = null
                    ScreenLink.note("打不开：$e")
                }
            }
        }.start()
    }

    private fun finish(why: String) {
        val job = running ?: return
        running = null
        main.removeCallbacks(timeout)
        ScreenLink.note("自动读金额 #${job.txId}：$why")
        val svc = ScreenService.instance ?: return
        // Back to the home screen we came from, unless the user already went somewhere else.
        main.postDelayed({
            val front = svc.rawForeground
            if (front == job.pkg || (svc.isDebug && front == svc.packageName)) svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }, 500)
        main.postDelayed(check, 2500)
    }
}
