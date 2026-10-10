package com.courseschedule.domain

import com.courseschedule.data.entity.Course
import com.courseschedule.data.entity.Semester
import com.courseschedule.utils.SchedulePreferences

enum class SemesterPhase {
    BEFORE,
    ACTIVE,
    AFTER
}

data class SemesterWeekStatus(
    val phase: SemesterPhase,
    val week: Int
)

object ScheduleRules {

    /** weekType=3：生效周次完全由 Course.weekList 决定 */
    const val WEEK_TYPE_CUSTOM = 3

    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun semesterWeekStatus(
        semester: Semester,
        now: Long = System.currentTimeMillis()
    ): SemesterWeekStatus {
        val rawWeek = Math.floorDiv(now - semester.startDate, WEEK_MILLIS).toInt() + 1
        return when {
            rawWeek < 1 -> SemesterWeekStatus(SemesterPhase.BEFORE, 1)
            rawWeek > semester.totalWeeks -> SemesterWeekStatus(
                SemesterPhase.AFTER,
                semester.totalWeeks
            )
            else -> SemesterWeekStatus(SemesterPhase.ACTIVE, rawWeek)
        }
    }

    fun isCourseInWeek(course: Course, week: Int): Boolean = when (course.weekType) {
        WEEK_TYPE_CUSTOM -> week in parseWeekList(course.weekList)
        1 -> week in course.startWeek..course.endWeek && week % 2 == 1
        2 -> week in course.startWeek..course.endWeek && week % 2 == 0
        else -> week in course.startWeek..course.endWeek
    }

    /** 该课程实际生效的全部周次（升序去重）。日历导出、提醒排期都以此为准。 */
    fun activeWeeks(course: Course): List<Int> =
        if (course.weekType == WEEK_TYPE_CUSTOM) {
            parseWeekList(course.weekList)
        } else {
            (course.startWeek..course.endWeek).filter { isCourseInWeek(course, it) }
        }

    /** 从 [fromWeek] 起（含）该课程的下一个生效周，没有则返回 null */
    fun nextActiveWeek(course: Course, fromWeek: Int): Int? =
        activeWeeks(course).firstOrNull { it >= fromWeek }

    /**
     * 解析自定义周次字符串，容忍逗号/顿号/分号/空白/换行等分隔。
     * 如 "6,9,12,15"、"5-13" 展开由调用方负责，这里只接受离散周次。
     */
    fun parseWeekList(raw: String): List<Int> =
        raw.split(',', '，', ';', '；', '、', ' ', '\n', '\t')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..60 }
            .distinct()
            .sorted()

    fun selectCoursesForWeek(
        courses: List<Course>,
        week: Int,
        showInactiveCourses: Boolean
    ): List<Course> = courses
        .groupBy { Triple(it.dayOfWeek, it.startSection, it.endSection) }
        .values
        .mapNotNull { sameSlot ->
            sameSlot.mapNotNull { course ->
                val nextWeek = nextActiveWeek(course, week)
                if (nextWeek == null || (!showInactiveCourses && nextWeek != week)) {
                    null
                } else {
                    course to nextWeek
                }
            }.minByOrNull { it.second }?.first
        }

    fun coursesOverlap(first: Course, second: Course): Boolean {
        if (first.semesterId != second.semesterId || first.dayOfWeek != second.dayOfWeek) {
            return false
        }
        if (first.endSection < second.startSection || second.endSection < first.startSection) {
            return false
        }
        val firstWeek = maxOf(first.startWeek, second.startWeek)
        val lastWeek = minOf(first.endWeek, second.endWeek)
        if (firstWeek > lastWeek) return false
        return (firstWeek..lastWeek).any { week ->
            isCourseInWeek(first, week) && isCourseInWeek(second, week)
        }
    }

    fun isDuplicate(first: Course, second: Course): Boolean {
        return first.courseName.trim().equals(second.courseName.trim(), ignoreCase = true) &&
            first.teacher.trim().equals(second.teacher.trim(), ignoreCase = true) &&
            first.classroom.trim().equals(second.classroom.trim(), ignoreCase = true) &&
            first.dayOfWeek == second.dayOfWeek &&
            first.startSection == second.startSection &&
            first.endSection == second.endSection &&
            first.startWeek == second.startWeek &&
            first.endWeek == second.endWeek &&
            first.weekType == second.weekType
    }

    fun isValidCourse(course: Course, totalWeeks: Int): Boolean {
        val sectionCount = SchedulePreferences.SECTION_COUNT
        return course.courseName.isNotBlank() &&
            course.dayOfWeek in 1..7 &&
            course.startSection in 1..sectionCount &&
            course.endSection in course.startSection..sectionCount &&
            course.startWeek in 1..totalWeeks &&
            course.endWeek in course.startWeek..totalWeeks &&
            // 注意要包含 WEEK_TYPE_CUSTOM(=3)，否则自定义周次的课程会被导入流程丢弃
            course.weekType in 0..WEEK_TYPE_CUSTOM &&
            (course.weekType != WEEK_TYPE_CUSTOM || parseWeekList(course.weekList).isNotEmpty())
    }
}
