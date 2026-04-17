package com.example.taskmaster.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

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
}