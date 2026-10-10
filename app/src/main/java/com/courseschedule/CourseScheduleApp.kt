package com.courseschedule

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.Build
import com.courseschedule.utils.AppNightMode
import com.courseschedule.utils.SchedulePreferences
import app.rive.runtime.kotlin.core.Rive

/**
 * 应用程序类
 */
class CourseScheduleApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "course_reminder_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // 界面内用 AppCompat 切换深色，和改动前一致
        val preferences = SchedulePreferences(this)
        AppNightMode.applyInApp(preferences.darkModeOverride)
        // 冷启动时还没有任何 Activity，此刻对齐系统里的应用级夜间模式用户看不到重建
        AppNightMode.syncToSystem(this, preferences.darkModeOverride)

        runCatching { Rive.init(this) }

        // 创建通知渠道
        createNotificationChannel()
    }

    /**
     * 界面退到后台时把深色选择同步给系统。
     *
     * 此刻应用的窗口已经不可见，setApplicationNightMode() 带来的那次 Activity 重建
     * 用户看不到；同步完成后，下一次冷启动系统画启动窗口时就能取到正确的夜间模式，
     * 深色模式不会再闪一帧浅色。
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            AppNightMode.syncToSystem(this, SchedulePreferences(this).darkModeOverride)
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "课程提醒"
            val descriptionText = "课程开始前的提醒通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
