package com.example.taskmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmaster.data.*
import com.example.taskmaster.ui.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = TaskDatabase.getDatabase(this)
        setContent {
            com.example.taskmaster.ui.theme.TaskMasterTheme {
                val viewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(database.taskDao()))
                MainScreenLayout(viewModel)
            }
        }
    }
}

// --- TAB STATE MANAGEMENT ---
sealed class TaskScreen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Active : TaskScreen("active", "Tasks", Icons.AutoMirrored.Filled.List)
    object History : TaskScreen("history", "Completed", Icons.Filled.CheckCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenLayout(viewModel: TaskViewModel) {
    var currentScreen by remember { mutableStateOf<TaskScreen>(TaskScreen.Active) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TaskMaster", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(TaskScreen.Active, TaskScreen.History).forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { padding ->
        Crossfade(targetState = currentScreen, animationSpec = tween(300), label = "TabSwitch") { screen ->
            when (screen) {
                TaskScreen.Active -> ActiveTasksScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
                TaskScreen.History -> CompletedTasksScreen(viewModel = viewModel, modifier = Modifier.padding(padding))
            }
        }
    }
}

// ================= TAB 1: ACTIVE TASKS SCREEN =================
// Note: Dashboard is intentionally removed from here to keep it execution-focused!
@Composable
fun ActiveTasksScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val tasks by viewModel.activeTasks.collectAsStateWithLifecycle()
    var isAddingNew by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = { FloatingActionButton(onClick = { isAddingNew = true }) { Icon(Icons.Filled.Add, "Add") } }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            items(tasks) { task ->
                TaskCard(
                    task = task,
                    onEditClick = { taskToEdit = task },
                    onDeleteClick = { viewModel.deleteTask(task) },
                    onCompletionToggle = { viewModel.toggleCompletion(task) },
                    onSnoozeToggle = { viewModel.toggleSnooze(task) } // Hooked up correctly
                )
            }
        }

        if (isAddingNew) { TaskDialog(initialTask = null, onDismiss = { isAddingNew = false }, onSave = { viewModel.addTask(it); isAddingNew = false }) }
        if (taskToEdit != null) { TaskDialog(initialTask = taskToEdit, onDismiss = { taskToEdit = null }, onSave = { viewModel.updateTask(it); taskToEdit = null }) }
    }
}

// ================= TAB 2: COMPLETED HISTORY SCREEN =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedTasksScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val pagedData by viewModel.filteredPagedTasks.collectAsStateWithLifecycle()

    val tasks = pagedData.first
    val currentPage = pagedData.second
    val totalPages = pagedData.third

    var showDatePicker by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = filterState.dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { viewModel.setFilterDate(dpState.selectedDateMillis); showDatePicker = false }) { Text("Filter") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Detailed Analytics Dashboard
        item {
            Spacer(modifier = Modifier.height(8.dp))
            AnalyticsDashboardCard(total = stats.first, completed = stats.second, breakdown = stats.third)
        }

        // 2. Category Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filterState.category == null,
                        onClick = { viewModel.setFilterCategory(null) },
                        label = { Text("All") }
                    )
                }
                items(TaskCategory.entries) { cat ->
                    FilterChip(
                        selected = filterState.category == cat,
                        onClick = { viewModel.setFilterCategory(cat) },
                        label = { Text(cat.name) },
                        leadingIcon = { Box(modifier = Modifier.size(10.dp).background(cat.getColor(), CircleShape)) }
                    )
                }
            }
        }

        // 3. Dynamic Filter Input
        item {
            if (filterState.category != null) {
                if (filterState.category == TaskCategory.SHORT_TERM) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search Scope")
                        }
                        AnimatedVisibility(visible = isSearchExpanded) {
                            OutlinedTextField(
                                value = filterState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search short term tasks...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Filled.DateRange, "Filter by Date") }
                        val dateText = if (filterState.dateMillis != null) "Filtered: ${dateFormatter.format(Date(filterState.dateMillis!!))}" else "Filter by Specific Date"
                        Text(dateText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (filterState.dateMillis != null) {
                            IconButton(onClick = { viewModel.setFilterDate(null) }) { Icon(Icons.Filled.Clear, "Clear Date") }
                        }
                    }
                }
            }
        }

        // 4. Paginated List
        items(tasks) { task ->
            TaskCard(
                task = task,
                onEditClick = {},
                onDeleteClick = { viewModel.deleteTask(task) },
                onCompletionToggle = { viewModel.toggleCompletion(task) },
                onSnoozeToggle = {} // Disabled for completed tasks
            )
        }

        // 5. Pagination Controls
        if (totalPages > 1) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.setPage(currentPage - 1) }, enabled = currentPage > 0) { Text("Previous") }
                    Text("Page ${currentPage + 1} of $totalPages", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { viewModel.setPage(currentPage + 1) }, enabled = currentPage < totalPages - 1) { Text("Next") }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ================= REUSABLE COMPONENTS =================

@Composable
fun TaskCard(
    task: Task,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCompletionToggle: () -> Unit,
    onSnoozeToggle: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val dateText = buildString {
        if (task.startDate != null) append(dateFormatter.format(Date(task.startDate)))
        if (task.startDate != null && task.endDate != null) append(" - ")
        if (task.endDate != null) append(dateFormatter.format(Date(task.endDate)))
        else if (task.deadlineDate != null) append(dateFormatter.format(Date(task.deadlineDate)))
    }

    val isCompleted = task.isCompleted
    val isPaused = task.isSnoozed

    val cardAlpha = if (isCompleted) 0.5f else if (isPaused) 0.7f else 1f
    val titleDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCompleted) { onEditClick() }
            .alpha(cardAlpha),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted || isPaused) 0.dp else 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPaused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.fillMaxHeight().width(12.dp).background(if (isCompleted) Color.DarkGray else if (isPaused) Color.Gray else task.category.getColor()))

            Checkbox(checked = isCompleted, onCheckedChange = { onCompletionToggle() }, modifier = Modifier.padding(start = 8.dp))

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    text = if (isPaused) "⏸ ${task.title}" else task.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCompleted || isPaused) Color.Gray else MaterialTheme.colorScheme.onSurface,
                    textDecoration = titleDecoration,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = task.category.name, fontSize = 12.sp, color = if(isCompleted || isPaused) Color.Gray else MaterialTheme.colorScheme.primary)
                    if (dateText.isNotEmpty()) { Text(text = "|  $dateText", fontSize = 12.sp, color = if(isCompleted || isPaused) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // Standard built-in material icons fix the unresolved reference!
            if (!isCompleted) {
                IconButton(onClick = onSnoozeToggle) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = "Toggle Pause",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onDeleteClick, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Filled.Delete, "Delete", tint = if (isCompleted) Color.Gray else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AnalyticsDashboardCard(total: Int, completed: Int, breakdown: Map<TaskCategory, Int>) {
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CircularAnalyticsIndicator(progress = progress, completedCount = completed, strokeWidth = 10.dp)

            Column(modifier = Modifier.weight(1f).padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Task Breakdown", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)

                TaskCategory.entries.forEach { cat ->
                    val count = breakdown[cat] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(cat.getColor(), CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${cat.name.lowercase().replaceFirstChar { it.uppercase() }}: $count", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
fun CircularAnalyticsIndicator(progress: Float, completedCount: Int, strokeWidth: Dp = 10.dp) {
    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor = progressColor.copy(alpha = 0.1f)
    val animatedProgress = animateFloatAsState(targetValue = progress, animationSpec = tween(1000, 200), label = "ProgressAnimation")

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(100.dp)) {
            drawCircle(color = trackColor, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round))
            drawArc(color = progressColor, startAngle = -90f, sweepAngle = 360f * animatedProgress.value, useCenter = false, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$completedCount", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = progressColor)
            Text(text = "Done", fontSize = 12.sp, color = progressColor.copy(alpha = 0.8f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDialog(initialTask: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(initialTask?.category ?: TaskCategory.SHORT_TERM) }
    var startDateMillis by remember { mutableStateOf(initialTask?.startDate) }
    var endDateMillis by remember { mutableStateOf(initialTask?.endDate ?: initialTask?.deadlineDate) }
    var activeDatePicker by remember { mutableStateOf<String?>(null) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    if (activeDatePicker != null) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = if (activeDatePicker == "START") startDateMillis else endDateMillis)
        DatePickerDialog(
            onDismissRequest = { activeDatePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    if (activeDatePicker == "START") startDateMillis = dpState.selectedDateMillis else endDateMillis = dpState.selectedDateMillis
                    activeDatePicker = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { activeDatePicker = null }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTask == null) "New Task" else "Edit Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TaskCategory.entries) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.name) },
                            leadingIcon = { Box(modifier = Modifier.size(12.dp).background(category.getColor(), CircleShape)) }
                        )
                    }
                }
                if (selectedCategory != TaskCategory.SHORT_TERM) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedCategory != TaskCategory.DEADLINE) {
                            OutlinedButton(onClick = { activeDatePicker = "START" }, modifier = Modifier.weight(1f)) {
                                Text(if (startDateMillis != null) dateFormatter.format(Date(startDateMillis!!)) else "Start Date")
                            }
                        }
                        OutlinedButton(onClick = { activeDatePicker = "END" }, modifier = Modifier.weight(1f)) {
                            Text(if (endDateMillis != null) dateFormatter.format(Date(endDateMillis!!)) else if (selectedCategory == TaskCategory.DEADLINE) "Deadline" else "End Date")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) {
                    val finalStart = if (selectedCategory == TaskCategory.SHORT_TERM || selectedCategory == TaskCategory.DEADLINE) null else startDateMillis
                    val finalEnd = if (selectedCategory == TaskCategory.SHORT_TERM || selectedCategory == TaskCategory.DEADLINE) null else endDateMillis
                    val finalDead = if (selectedCategory == TaskCategory.DEADLINE) endDateMillis else null
                    onSave(Task(id = initialTask?.id ?: 0, title = title, category = selectedCategory, startDate = finalStart, endDate = finalEnd, deadlineDate = finalDead))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}