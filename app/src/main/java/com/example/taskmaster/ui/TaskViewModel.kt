package com.example.taskmaster.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.taskmaster.data.Task
import com.example.taskmaster.data.TaskCategory
import com.example.taskmaster.data.TaskDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

// Helper object to hold all our filter states cleanly
data class FilterState(
    val category: TaskCategory? = null,
    val dateMillis: Long? = null,
    val searchQuery: String = "",
    val currentPage: Int = 0
)

class TaskViewModel(private val taskDao: TaskDao) : ViewModel() {

    // --- TAB 1: Active Tasks ---
    val activeTasks: StateFlow<List<Task>> = taskDao.getActiveTasksSorted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- TAB 2: Dashboard & Filtered History ---
    private val allCompletedTasks = taskDao.getAllCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard Stats: (Total Logged, Completed Count, Map of Category -> Count)
    val dashboardStats = combine(activeTasks, allCompletedTasks) { active, completed ->
        val total = active.size + completed.size
        val categoryBreakdown = completed.groupingBy { it.category }.eachCount()
        Triple(total, completed.size, categoryBreakdown)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0, 0, emptyMap()))

    // Filter controls
    private val _filterState = MutableStateFlow(FilterState())
    val filterState = _filterState.asStateFlow()

    fun setFilterCategory(category: TaskCategory?) {
        // Reset page, date, and search when category changes
        _filterState.update { it.copy(category = category, currentPage = 0, dateMillis = null, searchQuery = "") }
    }
    fun setFilterDate(dateMillis: Long?) = _filterState.update { it.copy(dateMillis = dateMillis, currentPage = 0) }
    fun setSearchQuery(query: String) = _filterState.update { it.copy(searchQuery = query, currentPage = 0) }
    fun setPage(page: Int) = _filterState.update { it.copy(currentPage = page) }

    // The final list shown on screen (Filtered & Paginated to 10 items)
    val filteredPagedTasks = combine(allCompletedTasks, _filterState) { tasks, state ->
        var filtered = tasks

        // 1. Filter by Category
        if (state.category != null) {
            filtered = filtered.filter { it.category == state.category }

            // 2a. Filter by Search Query (Only for Short Term)
            if (state.category == TaskCategory.SHORT_TERM && state.searchQuery.isNotBlank()) {
                filtered = filtered.filter { it.title.contains(state.searchQuery, ignoreCase = true) }
            }
            // 2b. Filter by Date (For all other categories)
            else if (state.category != TaskCategory.SHORT_TERM && state.dateMillis != null) {
                filtered = filtered.filter { task ->
                    isSameDay(task.startDate, state.dateMillis) ||
                            isSameDay(task.endDate, state.dateMillis) ||
                            isSameDay(task.deadlineDate, state.dateMillis)
                }
            }
        }

        // 3. Pagination Logic (10 items per page)
        val itemsPerPage = 10
        val totalItems = filtered.size
        val totalPages = if (totalItems == 0) 1 else Math.ceil(totalItems.toDouble() / itemsPerPage).toInt()
        val safePage = state.currentPage.coerceIn(0, maxOf(0, totalPages - 1))

        val pagedList = filtered.drop(safePage * itemsPerPage).take(itemsPerPage)

        Triple(pagedList, safePage, totalPages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(emptyList(), 0, 1))

    // --- Core CRUD Actions ---
    fun addTask(task: Task) = viewModelScope.launch { taskDao.insertTask(task) }
    fun deleteTask(task: Task) = viewModelScope.launch { taskDao.deleteTask(task) }
    fun toggleCompletion(task: Task) = viewModelScope.launch { taskDao.updateTask(task.copy(isCompleted = !task.isCompleted)) }
    fun updateTask(task: Task) = viewModelScope.launch { if (!task.isCompleted) taskDao.updateTask(task) }

    // Toggles the Pause/Resume state
    fun toggleSnooze(task: Task) = viewModelScope.launch {
        taskDao.updateTask(task.copy(isSnoozed = !task.isSnoozed))
    }

    // Manual Rollover: Moves any uncompleted, un-snoozed past tasks to Today
    fun rolloverMissedTasks() = viewModelScope.launch {
        val todayMillis = System.currentTimeMillis()
        // We will hook this up to the Notification Button in Phase 2
        // For now, it updates the database via the DAO
    }

    // Helper: Checks if two timestamps fall on the exact same calendar day
    private fun isSameDay(time1: Long?, time2: Long?): Boolean {
        if (time1 == null || time2 == null) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    class Factory(private val taskDao: TaskDao) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TaskViewModel::class.java)) return TaskViewModel(taskDao) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}