package com.yosuanru.htulist.data.backup

import com.yosuanru.htulist.data.entity.Course
import com.yosuanru.htulist.data.entity.Semester

data class SemesterSnapshot(
    val name: String,
    val startDate: Long,
    val totalWeeks: Int
) {
    companion object {
        fun from(semester: Semester) = SemesterSnapshot(
            name = semester.name,
            startDate = semester.startDate,
            totalWeeks = semester.totalWeeks
        )
    }
}

data class SettingsSnapshot(
    val showWeekend: Boolean = true,
    val showTime: Boolean = true,
    val showInactiveCourses: Boolean = true,
    val sectionHeightDp: Int = 72,
    val reminderEnabled: Boolean = true,
    val defaultReminderMinutes: Int = 15,
    val sectionTimes: List<String> = emptyList(),
    val sectionEndTimes: List<String> = emptyList()
)

data class ScheduleBackup(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val semester: SemesterSnapshot,
    val settings: SettingsSnapshot,
    val courses: List<Course>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
