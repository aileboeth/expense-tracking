package com.aileboeth.expensetracking.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.aileboeth.expensetracking.R

/**
 * Chinese phone makers stop background apps in their own ways. For each one: what to switch on,
 * where it is, and the system pages we try to open directly (these differ by system version, so
 * several are tried; if none opens, the app's details page is shown instead).
 */
object Brands {
    data class Step(@StringRes val title: Int, @StringRes val where: Int, val targets: List<Target> = emptyList())

    /** One way to open a settings page. */
    sealed interface Target {
        data class Component(val pkg: String, val cls: String, val withPackage: Boolean = false) : Target
        data class Action(val action: String, val withPackage: Boolean = false) : Target
        data object AppDetails : Target
        data object BatteryOptimization : Target
    }

    data class Guide(@StringRes val brandRes: Int, val system: String, val steps: List<Step>) {
        fun brandName(ctx: Context): String = if (brandRes == R.string.brand_other) Build.MANUFACTURER.ifBlank { ctx.getString(brandRes) } else ctx.getString(brandRes)
    }

    private val lockRecents = Step(R.string.step_lock, R.string.step_lock_where)

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
        "xiaomi" -> Guide(R.string.brand_xiaomi, "HyperOS / MIUI", listOf(
            Step(R.string.step_autostart, R.string.step_xiaomi_autostart, listOf(
                Target.Component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Target.Component("com.miui.securitycenter", "com.miui.permcenter.MainAcitivty"),
            )),
            Step(R.string.step_xiaomi_battery, R.string.step_xiaomi_battery_where, listOf(
                Target.Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity", withPackage = true),
                Target.AppDetails,
            )),
            lockRecents,
        ))
        "huawei" -> Guide(R.string.brand_huawei, "HarmonyOS / EMUI", listOf(
            Step(R.string.step_launch_manual, R.string.step_huawei_where, listOf(
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )),
            lockRecents,
        ))
        "honor" -> Guide(R.string.brand_honor, "MagicOS", listOf(
            Step(R.string.step_launch_manual, R.string.step_honor_where, listOf(
                Target.Component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                Target.Component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )),
            lockRecents,
        ))
        "oppo", "oneplus" -> Guide(if (maker == "oneplus") R.string.brand_oneplus else R.string.brand_oppo, "ColorOS", listOf(
            Step(R.string.step_autostart, R.string.step_oppo_autostart, listOf(
                Target.Component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                Target.Component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                Target.Component("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                Target.Component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                Target.Component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            )),
            Step(R.string.step_background, R.string.step_oppo_background, listOf(Target.AppDetails)),
            lockRecents,
        ))
        "vivo" -> Guide(R.string.brand_vivo, "OriginOS", listOf(
            Step(R.string.step_vivo_power, R.string.step_vivo_power_where, listOf(
                Target.Component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                Target.Component("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"),
                Target.AppDetails,
            )),
            Step(R.string.step_autostart, R.string.step_vivo_autostart, listOf(
                Target.Component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                Target.Component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                Target.Component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            )),
            Step(R.string.step_vivo_update, R.string.step_vivo_update_where),
            lockRecents,
        ))
        "samsung" -> Guide(R.string.brand_samsung, "One UI", listOf(
            Step(R.string.step_samsung_battery, R.string.step_samsung_battery_where, listOf(Target.AppDetails)),
            Step(R.string.step_samsung_sleep, R.string.step_samsung_sleep_where, listOf(
                Target.Component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                Target.Component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            )),
        ))
        "meizu" -> Guide(R.string.brand_meizu, "Flyme", listOf(
            Step(R.string.step_background, R.string.step_meizu_background, listOf(
                Target.Action("com.meizu.safe.security.SHOW_APPSEC", withPackage = true),
                Target.AppDetails,
            )),
            lockRecents,
        ))
        else -> Guide(R.string.brand_other, "Android", listOf(
            Step(R.string.step_battery_opt, R.string.step_battery_opt_where, listOf(Target.BatteryOptimization, Target.AppDetails)),
            Step(R.string.step_generic_bg, R.string.step_generic_bg_where, listOf(Target.AppDetails)),
        ))
    }

    /** Opens the first target that works. Returns false when it fell back to nothing at all. */
    fun open(ctx: Context, step: Step): Boolean {
        val pkg = ctx.packageName
        for (t in step.targets) {
            val intent = when (t) {
                is Target.Component -> Intent().setComponent(ComponentName(t.pkg, t.cls)).apply {
                    if (t.withPackage) { putExtra("package_name", pkg); putExtra("packageName", pkg); putExtra("package_label", ctx.getString(R.string.app_name)) }
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
