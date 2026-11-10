package com.mrgnstr.harmony.core.appicon

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import com.mrgnstr.harmony.core.model.AccentColor

/** The Material shape behind the notes of the launcher icon. */
enum class AppIconShape(val label: String) {
    Cookie("Cookie"), Circle("Circle"), Square("Square"), Pill("Pill"), Clover("Clover"),
    Flower("Flower"), Sunny("Sunny"), Burst("Burst"), Pentagon("Pentagon"), Arch("Arch"),
}

/**
 * A launcher icon the user can pick: a shape in a colour. [AccentColor.Wallpaper] is the icon drawn in the system's
 * palette (it follows the wallpaper on API 31+); the presets are fixed colours.
 *
 * Each one is an `<activity-alias>` in the manifest pointing at MainActivity with its own icon; exactly one is
 * enabled, and the package manager remembers which. Artwork, resources and the manifest's alias block all come from
 * `python3 -I tools/app_icons/gen_m3.py`: a new shape or colour is added there and in the enums.
 */
data class AppIcon(val shape: AppIconShape, val color: AccentColor) {
    private val aliasName: String
        get() = "com.mrgnstr.harmony.launcher." + shape.name + if (color == AccentColor.Wallpaper) "" else color.name

    private fun component(context: Context) = ComponentName(context.packageName, aliasName)

    /** The icon's mipmap, 0 if missing. Looked up by name: ninety R fields would only repeat the generator. */
    fun resource(context: Context): Int {
        val shapeName = shape.name.lowercase()
        val name = when {
            color != AccentColor.Wallpaper -> "ic_launcher_${shapeName}_${color.name.lowercase()}"
            shape == AppIconShape.Cookie -> "ic_launcher"
            else -> "ic_launcher_$shapeName"
        }
        return context.resources.getIdentifier(name, "mipmap", context.packageName)
    }

    companion object {
        val Default = AppIcon(AppIconShape.Cookie, AccentColor.Wallpaper)

        private val all: List<AppIcon> =
            AppIconShape.entries.flatMap { shape -> AccentColor.entries.map { AppIcon(shape, it) } }

        private fun PackageManager.stateOf(context: Context, icon: AppIcon) =
            getComponentEnabledSetting(icon.component(context))

        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return all.firstOrNull { pm.stateOf(context, it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                ?: Default // nothing switched yet: the manifest's own defaults apply
        }

        /**
         * Enables [icon]'s alias before disabling the rest, so the app is never without a launcher entry.
         *
         * Android removes a task whose base intent names a component that gets disabled, and ours was started
         * through the old alias: the app would vanish to the home screen. So in between, the app moves to a new
         * task started on MainActivity itself, which is never disabled. (Clearing and reusing the old task is not
         * enough: it keeps the alias as its base intent.)
         */
        fun select(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            fun set(target: AppIcon, state: Int) =
                pm.setComponentEnabledSetting(target.component(context), state, PackageManager.DONT_KILL_APP)
            // Only aliases that are on need switching off (each call wakes the launcher): any explicitly enabled,
            // and the default one while it still has its manifest state.
            val on = all.filter {
                it != icon && when (pm.stateOf(context, it)) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> it == Default
                    else -> false
                }
            }
            set(icon, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            // Old task first: while its MainActivity is on top, singleTop would hand it the new intent
            // instead of starting the new task.
            var c = context
            while (c !is Activity && c is ContextWrapper) c = c.baseContext
            (c as? Activity)?.finishAndRemoveTask()
            context.applicationContext.startActivity(
                Intent().setClassName(context, "com.mrgnstr.harmony.app.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            )
            on.forEach { set(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
        }
    }
}
