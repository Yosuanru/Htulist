package com.courseschedule.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待办任务。
 *
 * 数据模型参考开源项目 Todometer（Apache-2.0）的 TaskEntity / ProjectEntity，
 * 但做了两处简化：
 *  - 它的 TaskState 有 待办 / 进行中 / 已完成 三态，本应用需求只要「勾选 / 未勾选」两态，
 *    故用 [isDone] 布尔值；同时保留 [completedAt] 记录完成时刻。
 *  - 砍掉 Tag（标签），需求里没有。
 * 新增了需求要求的 [isPinned]（置顶）、[dueAt]（到期，供日历联动用）与 [sortOrder]。
 */
@Entity(
    tableName = "tasks",
    indices = [Index("projectId")]
)
data class TaskItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    /** 多行备注 */
    val note: String = "",

    /** 是否已完成（圆形勾选框的状态） */
    val isDone: Boolean = false,

    /** 是否置顶（钉住） */
    val isPinned: Boolean = false,

    /** 所属分组 */
    val projectId: Long = DEFAULT_PROJECT_ID,

    /** 到期时间戳，null 表示不设到期 */
    val dueAt: Long? = null,

    /** 同组内排序，越小越靠前 */
    val sortOrder: Int = 0,

    val createTime: Long = System.currentTimeMillis(),

    /** 完成时刻，未完成时为 null */
    val completedAt: Long? = null
) {
    companion object {
        const val DEFAULT_PROJECT_ID = 1L
    }
}
