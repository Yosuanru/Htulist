package com.courseschedule.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.courseschedule.data.entity.TaskProject

@Dao
interface TaskProjectDao {

    @Query("SELECT * FROM task_projects ORDER BY sortOrder ASC, id ASC")
    fun getAll(): LiveData<List<TaskProject>>

    @Query("SELECT * FROM task_projects ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllSync(): List<TaskProject>

    @Query("SELECT * FROM task_projects WHERE id = :id")
    suspend fun getById(id: Long): TaskProject?

    @Insert
    suspend fun insert(project: TaskProject): Long

    @Update
    suspend fun update(project: TaskProject)

    @Delete
    suspend fun delete(project: TaskProject)

    @Query("SELECT COUNT(*) FROM task_projects")
    suspend fun count(): Int
}
