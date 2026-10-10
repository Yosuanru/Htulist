package com.yosuanru.htulist.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办分组（清单）。
 *
 * 对应 Todometer 的 ProjectEntity。默认分组 id 固定为 [TaskItem.DEFAULT_PROJECT_ID]。
 */
@Entity(tableName = "task_projects")
data class TaskProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** 取色下标，与课表课程颜色同一套色板的用法 */
    val colorIndex: Int = 0,

    val sortOrder: Int = 0,

    val createTime: Long = System.currentTimeMillis()
)
