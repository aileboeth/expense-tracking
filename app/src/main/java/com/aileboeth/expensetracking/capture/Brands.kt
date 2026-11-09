package com.aileboeth.expensetracking.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Chinese phone makers stop background apps in their own ways. For each one: what to switch on,
 * where it is, and the system pages we try to open directly (these differ by system version, so
 * several are tried; if none opens, the app's details page is shown instead).
 */
object Brands {
    data class Step(val title: String, val where: String, val targets: List<Target> = emptyList())

    /** One way to open a settings page. */
    sealed interface Target {
        data class Component(val pkg: String, val cls: String, val withPackage: Boolean = false) : Target
        data class Action(val action: String, val withPackage: Boolean = false) : Target
        data object AppDetails : Target
        data object BatteryOptimization : Target
    }

    data class Guide(val brand: String, val system: String, val steps: List<Step>)

    private val lockRecents = Step(
        "在最近任务里锁住「记账」",
        "打开最近任务（多任务）界面，找到记账的卡片，下拉或长按后点「锁定」。一键清理时就不会把它清掉。",
    )

    /** Which maker this phone is from (lower case, e.g. "xiaomi", "huawei"). */
    fun maker(): String {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> "xiaomi"
            "honor" in m -> "honor"
            "huawei" in m -> "huawei"
            "oneplus" in m -> "oneplus"
            "oppo" in m || "realme" in m -> "oppo"
            "vivo" in m || "iqoo" in m -> "vivo"
            "samsung" in m -> "samsung"
            "meizu" in m -> "meizu"
            else -> "other"
        }
    }

    fun current(): Guide = guide(maker())

    fun guide(maker: String): Guide = when (maker) {
        "xiaomi" -> Guide("小米 / Redmi", "HyperOS / MIUI", listOf(
            Step("允许自启动", "安全中心 → 应用管理 → 权限 → 自启动管理，打开「记账」。", listOf(
                Target.Component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Target.Component("com.miui.securitycenter", "com.miui.permcenter.MainAcitivty"),
            )),
            Step("省电策略选「无限制」", "设置 → 应用设置 → 应用管理 → 记账 → 省电策略 → 无限制。", listOf(
                Target.Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity", withPackage = true),
                Target.AppDetails,
            )),
            lockRecents,
        ))
        "huawei" -> Guide("华为", "HarmonyOS / EMUI", listOf(
            Step("应用启动管理改成手动", "设置 → 应用和服务 → 应用启动管理 → 记账：关掉「自动管理」，在弹出的窗口里打开「允许自启动」「允许关联启动」「允许后台活动」。", listOf(
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )),
            lockRecents,
        ))
        "honor" -> Guide("荣耀", "MagicOS", listOf(
            Step("应用启动管理改成手动", "设置 → 应用 → 应用启动管理 → 记账：关掉「自动管理」，打开「允许自启动」「允许关联启动」「允许后台活动」。", listOf(
                Target.Component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )),
            lockRecents,
        ))
        "oppo", "oneplus" -> Guide(if (maker == "oneplus") "一加" else "OPPO / realme", "ColorOS", listOf(
            Step("允许自启动", "设置 → 应用 → 自启动管理（或 手机管家 → 权限隐私 → 自启动管理），打开「记账」。", listOf(
                Target.Component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                Target.Component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                Target.Component("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                Target.Component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                Target.Component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )),
            Step("允许后台运行", "设置 → 电池 → 更多设置 / 应用耗电管理 → 记账：打开「允许后台运行」，关掉「智能限制」类选项。", listOf(Target.AppDetails)),
            lockRecents,
        ))
        "vivo" -> Guide("vivo / iQOO", "OriginOS", listOf(
            Step("允许后台高耗电", "设置 → 电池 → 后台耗电管理 → 记账 → 允许后台高耗电。", listOf(
                Target.Component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                Target.Component("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"),
                Target.AppDetails,
            )),
            Step("允许自启动", "i管家 → 应用管理 → 权限管理 → 自启动（或 设置 → 应用与权限 → 权限管理 → 自启动），打开「记账」。", listOf(
                Target.Component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                Target.Component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                Target.Component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            )),
            Step("App 更新后打开一次", "vivo 在 App 更新后不会自动重新连接通知监听。更新完打开一次记账就好，App 会自己重连。"),
            lockRecents,
        ))
        "samsung" -> Guide("三星", "One UI", listOf(
            Step("电池改成「不受限制」", "设置 → 应用程序 → 记账 → 电池 → 不受限制。", listOf(Target.AppDetails)),
            Step("不要放进「深度睡眠应用」", "设置 → 电池 → 后台使用限制：确认记账不在「休眠应用程序」「深度休眠应用程序」里。", listOf(
                Target.Component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                Target.Component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )),
        ))
        "meizu" -> Guide("魅族", "Flyme", listOf(
            Step("允许后台运行", "手机管家 → 权限管理 → 后台管理 → 记账 → 允许后台运行。", listOf(
                Target.Action("com.meizu.safe.security.SHOW_APPSEC", withPackage = true),
                Target.AppDetails,
            )),
            lockRecents,
        ))
        else -> Guide(Build.MANUFACTURER.ifBlank { "这台手机" }, "Android", listOf(
            Step("关闭电池优化", "设置 → 应用 → 记账 → 电池：选「不优化」或「不受限制」。", listOf(Target.BatteryOptimization, Target.AppDetails)),
            Step("允许自启动 / 后台运行", "如果系统有「自启动」「后台运行」之类的开关，给记账打开。", listOf(Target.AppDetails)),
        ))
    }

    /** Opens the first target that works. Returns false when it fell back to nothing at all. */
    fun open(ctx: Context, step: Step): Boolean {
        val pkg = ctx.packageName
        for (t in step.targets) {
            val intent = when (t) {
                is Target.Component -> Intent().setComponent(ComponentName(t.pkg, t.cls)).apply {
                    if (t.withPackage) { putExtra("package_name", pkg); putExtra("packageName", pkg); putExtra("package_label", "记账") }
                }
                is Target.Action -> Intent(t.action).apply { if (t.withPackage) putExtra("packageName", pkg) }
                Target.AppDetails -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                Target.BatteryOptimization -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(intent)
                return true
            } catch (_: Exception) {
                // not on this system version / not exported: try the next one
            }
        }
        return try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
