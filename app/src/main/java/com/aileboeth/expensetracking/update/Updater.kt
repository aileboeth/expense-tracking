package com.aileboeth.expensetracking.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-update from GitHub Releases. Each release carries three files with fixed names:
 *   version.json                 {"version","versionCode","apk","sha256","notes"}
 *   ExpenseTracking-<ver>.apk
 *   SHA256SUMS.txt
 * Only github.com links are used (no API, no rate limit), so a download mirror prefix works for all of them.
 */
object Updater {
    const val REPO = "aileboeth/expense-tracking"
    private const val LATEST_JSON = "https://github.com/$REPO/releases/latest/download/version.json"
    private const val CHECK_EVERY_MS = 12L * 3600 * 1000

    data class Info(val version: String, val versionCode: Long, val apk: String, val sha256: String, val notes: String) {
        val apkUrl get() = "https://github.com/$REPO/releases/download/v$version/$apk"
    }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val current: String) : State
        data class Available(val info: Info, val current: String) : State
        data class Downloading(val info: Info, val done: Long, val total: Long) : State
        data class Ready(val info: Info, val file: File) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** true when the user pressed the button: then "up to date" and errors are shown too. */
    @Volatile var manual = false
        private set

    fun dismiss() { _state.value = State.Idle }

    fun currentVersion(ctx: Context): Pair<String, Long> {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return (info.versionName ?: "?") to info.longVersionCode
    }

    fun autoCheck(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.autoUpdate) return
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < CHECK_EVERY_MS) return
        check(ctx, manual = false)
    }

    fun check(ctx: Context, manual: Boolean) {
        val app = ctx.applicationContext
        res = app
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        this.manual = manual
        _state.value = State.Checking
        scope.launch {
            val prefs = Prefs(app)
            try {
                val json = JSONObject(String(get(prefs.mirrorPrefix + LATEST_JSON), Charsets.UTF_8))
                val info = Info(
                    version = json.getString("version"),
                    versionCode = json.getLong("versionCode"),
                    apk = json.getString("apk"),
                    sha256 = json.optString("sha256").lowercase(),
                    notes = json.optString("notes"),
                )
                prefs.lastUpdateCheck = System.currentTimeMillis()
                val (name, code) = currentVersion(app)
                _state.value = if (info.versionCode > code) State.Available(info, name) else State.UpToDate(name)
            } catch (e: Exception) {
                _state.value = State.Failed(app.getString(R.string.update_err_check, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun download(ctx: Context, info: Info) {
        val app = ctx.applicationContext
        res = app
        manual = true
        _state.value = State.Downloading(info, 0, 0)
        scope.launch {
            try {
                val dir = File(app.cacheDir, "update").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val part = File(dir, info.apk + ".part")
                stream(Prefs(app).mirrorPrefix + info.apkUrl, part) { done, total ->
                    _state.value = State.Downloading(info, done, total)
                }
                if (info.sha256.isNotEmpty()) {
                    val actual = sha256(part)
                    if (actual != info.sha256) {
                        part.delete()
                        throw IllegalStateException(app.getString(R.string.update_err_hash))
                    }
                }
                val apk = File(dir, info.apk)
                part.renameTo(apk)
                _state.value = State.Ready(info, apk)
                install(app, apk)
            } catch (e: Exception) {
                _state.value = State.Failed(app.getString(R.string.update_err_download, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Opens the system installer; asks for "install unknown apps" first when needed. */
    fun install(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** For error texts in the user's language. */
    @Volatile private var res: Context? = null

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ExpenseTracking-Android")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("redirect without Location")
                c.disconnect()
                current = URL(URL(current), loc).toString()
                return@repeat
            }
            if (code != 200) {
                c.disconnect()
                throw IllegalStateException(res?.let { r -> if (code == 404) r.getString(R.string.update_err_404) else r.getString(R.string.update_err_code, code) } ?: "HTTP $code")
            }
            return c
        }
        throw IllegalStateException(res?.getString(R.string.update_err_redirect) ?: "too many redirects")
    }

    private fun get(url: String): ByteArray = open(url).let { c -> try { c.inputStream.readBytes() } finally { c.disconnect() } }

    private fun stream(url: String, to: File, progress: (Long, Long) -> Unit) {
        val c = open(url)
        try {
            val total = c.contentLengthLong
            var done = 0L
            var lastReport = 0L
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 256 * 1024) { progress(done, total); lastReport = done }
                    }
                }
            }
            progress(done, total)
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
