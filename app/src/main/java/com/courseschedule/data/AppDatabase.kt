package com.courseschedule.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.courseschedule.data.dao.CourseDao
import com.courseschedule.data.dao.SemesterDao
import com.courseschedule.data.dao.TaskDao
import com.courseschedule.data.dao.TaskProjectDao
import com.courseschedule.data.entity.Course
import com.courseschedule.data.entity.Semester
import com.courseschedule.data.entity.TaskItem
import com.courseschedule.data.entity.TaskProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 应用数据库
 */
@Database(
    entities = [Course::class, Semester::class, TaskItem::class, TaskProject::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun semesterDao(): SemesterDao
    abstract fun taskDao(): TaskDao
    abstract fun taskProjectDao(): TaskProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2：courses 表新增自定义周次列 week_list。
         * 必须显式迁移——用户已有课表不能被清掉。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN week_list TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v2 -> v3：新增待办模块的两张表。
         * 同样是显式迁移，不能用破坏性迁移，否则用户课表会被清空。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_projects` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`colorIndex` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createTime` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`isDone` INTEGER NOT NULL, " +
                        "`isPinned` INTEGER NOT NULL, " +
                        "`projectId` INTEGER NOT NULL, " +
                        "`dueAt` INTEGER, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createTime` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "course_schedule_database"
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * 数据库创建回调 - 初始化默认学期
     */
    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    initDefaultSemester(database.semesterDao())
                    ensureDefaultProject(database.taskProjectDao())
                }
            }
        }

        /**
         * 每次打开库都兜一次默认分组。
         * 放在 onOpen 而不是 onCreate，是因为老用户是从 v2 迁移上来的，不会走 onCreate。
         */
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    ensureDefaultProject(database.taskProjectDao())
                }
            }
        }

        private suspend fun ensureDefaultProject(dao: TaskProjectDao) {
            if (dao.getById(TaskItem.DEFAULT_PROJECT_ID) == null) {
                dao.insert(
                    TaskProject(
                        id = TaskItem.DEFAULT_PROJECT_ID,
                        name = "默认清单",
                        colorIndex = 0,
                        sortOrder = 0
                    )
                )
            }
        }

        private suspend fun initDefaultSemester(semesterDao: SemesterDao) {
            // 创建默认学期
            val calendar = Calendar.getInstance()
            // 只取日期：不清零时分秒的话，startDate 会带上建库那一刻的时刻，
            // 于是「第 N 周」要到一天中间的某个时刻才翻页，和日历对不上。
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH) + 1

            // 根据当前月份确定学期
            val semesterName: String
            val startMonth: Int

            if (currentMonth >= 9) {
                // 秋季学期
                semesterName = "${currentYear}-${currentYear + 1} 第一学期"
                startMonth = 9
            } else if (currentMonth >= 2) {
                // 春季学期
                semesterName = "${currentYear - 1}-${currentYear} 第二学期"
                startMonth = 2
            } else {
                // 寒假，显示上一秋季学期
                semesterName = "${currentYear - 1}-${currentYear} 第一学期"
                startMonth = 9
                calendar.set(Calendar.YEAR, currentYear - 1)
            }

            calendar.set(Calendar.MONTH, startMonth - 1)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            // 设置为周一
            while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            val defaultSemester = Semester(
                name = semesterName,
                startDate = calendar.timeInMillis,
                totalWeeks = 20,
                isCurrent = true
            )

            semesterDao.insertSemester(defaultSemester)
        }
    }
}
