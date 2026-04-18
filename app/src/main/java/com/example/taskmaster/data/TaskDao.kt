package com.example.taskmaster.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // Applies your exact sorting rules for the widget and main screen
    @Query("""
        SELECT * FROM tasks 
        WHERE isCompleted = 0 AND isSnoozed = 0
        ORDER BY 
        CASE category 
            WHEN 'DEADLINE' THEN 1 
            WHEN 'BIWEEKLY' THEN 2 
            WHEN 'WEEKLY' THEN 3 
            WHEN 'DAILY' THEN 4 
            WHEN 'SHORT_TERM' THEN 5 
            ELSE 6 
        END ASC
    """)
    fun getWidgetTasksSorted(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND requiresRolloverDecision = 1")
    fun getTasksPendingRollover(): Flow<List<Task>>

    @Query("""
        SELECT * FROM tasks 
        WHERE isCompleted = 0 AND isSnoozed = 0
        ORDER BY CASE category WHEN 'DEADLINE' THEN 1 WHEN 'BIWEEKLY' THEN 2 WHEN 'WEEKLY' THEN 3 WHEN 'DAILY' THEN 4 WHEN 'SHORT_TERM' THEN 5 ELSE 6 END ASC
    """)
    fun getActiveTasksSorted(): Flow<List<Task>>

    // --- New Completed Tasks History Queries ---

    // 1. Get ALL completed tasks, newest first
    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY id DESC")
    fun getAllCompletedTasks(): Flow<List<Task>>

    // 2. Filter completed tasks by a specific date (StartDate, EndDate, or Deadline matches)
    @Query("""
        SELECT * FROM tasks 
        WHERE isCompleted = 1 AND 
        ((startDate = :targetDateMillis) OR (endDate = :targetDateMillis) OR (deadlineDate = :targetDateMillis))
        ORDER BY id DESC
    """)
    fun getCompletedTasksByDate(targetDateMillis: Long): Flow<List<Task>>

    // 3. Filter completed tasks by a range (e.g., a week)
    @Query("""
        SELECT * FROM tasks 
        WHERE isCompleted = 1 AND 
        ((startDate BETWEEN :startMillis AND :endMillis) OR (endDate BETWEEN :startMillis AND :endMillis) OR (deadlineDate BETWEEN :startMillis AND :endMillis))
        ORDER BY id DESC
    """)
    fun getCompletedTasksByRange(startMillis: Long, endMillis: Long): Flow<List<Task>>

    // --- New Analytics Query ---
    @Query("SELECT COUNT(*) FROM tasks")
    fun getTotalTaskCount(): Flow<Int>
}