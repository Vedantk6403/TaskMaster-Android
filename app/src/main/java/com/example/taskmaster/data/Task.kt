package com.example.taskmaster.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskCategory {
    DEADLINE, BIWEEKLY, WEEKLY, DAILY, SHORT_TERM
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val category: TaskCategory,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val deadlineDate: Long? = null,
    val isCompleted: Boolean = false,
    val isSnoozed: Boolean = false,
    val requiresRolloverDecision: Boolean = false
)