package com.yosuanru.htulist.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yosuanru.htulist.data.entity.TaskItem

/**
 * 待办任务读写。
 *
 * 排序规则统一在这里定义：**置顶在最前，未完成在已完成之前，然后按手动排序、最后按创建时间倒序**。
 * 所有查询都复用这套顺序，保证列表各处表现一致。
 */
@Dao
interface TaskDao {

    @Query(
        """
        SELECT * FROM tasks
        ORDER BY isPinned DESC, isDone ASC, sortOrder ASC, createTime DESC
        """
    )
    fun getAll(): LiveData<List<TaskItem>>

    @Query(
        """
        SELECT * FROM tasks WHERE projectId = :projectId
        ORDER BY isPinned DESC, isDone ASC, sortOrder ASC, createTime DESC
        """
    )
    fun getByProject(projectId: Long): LiveData<List<TaskItem>>

    /** 未完成的数量，用于底部导航的小红点 / 分组角标 */
    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0")
    fun pendingCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND projectId = :projectId")
    fun pendingCountInProject(projectId: Long): LiveData<Int>

    @Insert
    suspend fun insert(task: TaskItem): Long

    @Update
    suspend fun update(task: TaskItem)

    @Delete
    suspend fun delete(task: TaskItem)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)

    /** 勾选 / 取消勾选。completedAt 与 isDone 必须同时更新，否则会留下不一致的完成时间。 */
    @Query("UPDATE tasks SET isDone = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, completedAt: Long?)

    @Query("UPDATE tasks SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** 把分组内所有任务移到另一个分组（删除分组时用） */
    @Query("UPDATE tasks SET projectId = :targetProjectId WHERE projectId = :fromProjectId")
    suspend fun moveAllToProject(fromProjectId: Long, targetProjectId: Long)

    // ---- 日历联动用：只取有到期时间的未完成任务 ----
    @Query("SELECT * FROM tasks WHERE dueAt IS NOT NULL AND isDone = 0")
    suspend fun getPendingWithDue(): List<TaskItem>
}
