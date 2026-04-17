package com.example.taskmaster.ui

import androidx.compose.ui.graphics.Color
import com.example.taskmaster.data.TaskCategory

// Extension function to map your database categories to Jetpack Compose colors
fun TaskCategory.getColor(): Color {
    return when (this) {
        TaskCategory.DEADLINE -> Color.Red
        TaskCategory.BIWEEKLY -> Color.Blue
        TaskCategory.WEEKLY -> Color.Green
        TaskCategory.DAILY -> Color.Yellow
        TaskCategory.SHORT_TERM -> Color(0xFF008080) // Hex code for Teal
    }
}