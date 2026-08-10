package com.iknalos.aiprep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iknalos.aiprep.screens.FlashScreen
import com.iknalos.aiprep.screens.FocusScreen
import com.iknalos.aiprep.screens.HomeScreen
import com.iknalos.aiprep.screens.LessonDetailScreen
import com.iknalos.aiprep.screens.LessonsScreen
import com.iknalos.aiprep.screens.MockScreen
import com.iknalos.aiprep.screens.NewsScreen
import com.iknalos.aiprep.screens.QuizScreen
import com.iknalos.aiprep.screens.SettingsScreen
import com.iknalos.aiprep.screens.StatsScreen
import com.iknalos.aiprep.screens.StudyScreen
import com.iknalos.aiprep.ui.AIPrepTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Idempotent: re-registering the same unique work just refreshes the schedule,
        // which is what we want after an update changes the job.
        if (Settings(this).autoUpdate) {
            DailySyncWorker.schedule(this)
        }

        setContent {
            AIPrepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (vm.ready) AppRoot(vm) else LoadingScreen()
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading question bank...", style = MaterialTheme.typography.bodyMedium)
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

// Six is one past the Material guideline, but every mode earns its place and the
// labels are short enough to fit a small phone.
private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.FLASH, "Flash", Icons.Filled.Bolt),
    Tab(Routes.STUDY, "Study", Icons.Filled.Style),
    Tab(Routes.QUIZ, "Quiz", Icons.Filled.TaskAlt),
    Tab(Routes.LEARN, "Learn", Icons.Filled.MenuBook),
    Tab(Routes.NEWS, "News", Icons.Filled.Newspaper)
)

object Routes {
    const val HOME = "home"
    const val FLASH = "flash"
    const val STUDY = "study"
    const val QUIZ = "quiz"
    const val LEARN = "learn"
    const val NEWS = "news"
    const val MOCK = "mock"
    const val STATS = "stats"
    const val FOCUS = "focus"
    const val LESSON = "lesson"
    const val SETTINGS = "settings"
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { nav.navigateTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onStudy = { nav.navigateTab(Routes.STUDY) },
                    onFlash = { nav.navigateTab(Routes.FLASH) },
                    onQuiz = { nav.navigateTab(Routes.QUIZ) },
                    onMock = { nav.navigate(Routes.MOCK) },
                    onLearn = { nav.navigateTab(Routes.LEARN) },
                    onNews = { nav.navigateTab(Routes.NEWS) },
                    onStats = { nav.navigate(Routes.STATS) },
                    onFilters = { nav.navigate(Routes.FOCUS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.FLASH) {
                FlashScreen(
                    vm = vm,
                    onLesson = { topicId -> nav.navigate("${Routes.LESSON}/$topicId") },
                    onExit = { nav.navigateTab(Routes.HOME) }
                )
            }
            composable(Routes.STUDY) {
                StudyScreen(vm) { nav.navigateTab(Routes.HOME) }
            }
            composable(Routes.QUIZ) {
                QuizScreen(vm) { nav.navigateTab(Routes.HOME) }
            }
            composable(Routes.LEARN) {
                LessonsScreen(vm) { topicId -> nav.navigate("${Routes.LESSON}/$topicId") }
            }
            composable(Routes.NEWS) {
                NewsScreen(vm)
            }
            composable(Routes.MOCK) {
                MockScreen(vm) { nav.popBackStack() }
            }
            composable(Routes.STATS) {
                StatsScreen(vm)
            }
            composable(Routes.FOCUS) {
                FocusScreen(vm) { nav.popBackStack() }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm)
            }
            composable("${Routes.LESSON}/{topicId}") { entry ->
                val topicId = entry.arguments?.getString("topicId").orEmpty()
                LessonDetailScreen(
                    vm = vm,
                    topicId = topicId,
                    onBack = { nav.popBackStack() },
                    onDrill = {
                        vm.startQuiz(10)
                        nav.navigateTab(Routes.QUIZ)
                    }
                )
            }
        }
    }
}

/** Tab navigation that keeps a single instance per destination. */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
