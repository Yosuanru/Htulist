package com.yosuanru.htulist.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/**
 * 深色模式的唯一入口。两条通道分工明确，不要混用。
 *
 * 1）界面内切换 —— [applyInApp]，只用 AppCompat，与引入深色模式时完全一致，
 *    Activity 重建过程平滑，不会闪。
 *
 * 2）系统侧同步 —— [syncToSystem]。冷启动最先显示的是系统画的「启动窗口」
 *    （Android 12+ 的 splash），它由系统按**设备**的深色设置去解析 values-night，
 *    进程内的 AppCompat 系统看不到；于是「系统浅色 + App 深色」时启动窗口会先闪一帧
 *    浅色。API 31 起可以用 setApplicationNightMode() 把夜间模式持久化给系统，启动窗口
 *    就跟着走了。
 *
 * 但 setApplicationNightMode() 会立刻对**正在运行的应用**下发一次配置变更并重建
 * Activity。如果在切换开关的当下调用，它会和 AppCompat 的那次重建叠成两次，
 * 观感就是卡顿 + 闪烁。所以只在界面不可见的时刻同步：
 * 应用退到后台时（Application.onTrimMemory(TRIM_MEMORY_UI_HIDDEN)）、
 * 以及冷启动还没有任何 Activity 时。
 */
internal object AppNightMode {

    /** 界面内切换。和引入深色模式时的写法一致，保证过渡平滑。 */
    fun applyInApp(darkOverride: Boolean?) {
        AppCompatDelegate.setDefaultNightMode(
            when (darkOverride) {
                true -> AppCompatDelegate.MODE_NIGHT_YES
                false -> AppCompatDelegate.MODE_NIGHT_NO
                null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /**
     * 把深色选择同步给系统，只影响「下一次冷启动」的启动窗口。
     *
     * 界面可见时**不要**调用 —— 会触发一次用户看得见的 Activity 重建。
     */
    fun syncToSystem(context: Context, darkOverride: Boolean?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        // 跟随系统：不动系统里的应用级夜间模式设置
        if (darkOverride == null) return
        val desiredMode = if (darkOverride) {
            UiModeManager.MODE_NIGHT_YES
        } else {
            UiModeManager.MODE_NIGHT_NO
        }
        val desiredMask = if (darkOverride) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        // 应用当前生效的深浅色就写在资源配置里（系统会把它挂在应用上），
        // 已经一致就别白白再触发一次配置变更
        val current = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (current == desiredMask) return
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return
        runCatching { manager.setApplicationNightMode(desiredMode) }
    }
}
