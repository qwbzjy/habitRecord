package com.wenbo.habitrecord.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wenbo.habitrecord.feature.habits.HabitEditScreen
import com.wenbo.habitrecord.feature.habits.HabitsScreen
import com.wenbo.habitrecord.feature.rewards.RewardsScreen
import com.wenbo.habitrecord.feature.stats.StatsScreen
import com.wenbo.habitrecord.feature.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val HABITS = "habits"
    const val REWARDS = "rewards"
    const val STATS = "stats"
    const val HABIT_EDIT = "habit_edit?habitId={habitId}"

    fun habitEdit(habitId: Long?) = "habit_edit?habitId=${habitId ?: -1L}"
}

private data class TopLevelDest(val route: String, val label: String, val icon: ImageVector)

private val topLevelDests = listOf(
    TopLevelDest(Routes.TODAY, "今日", Icons.Filled.Checklist),
    TopLevelDest(Routes.HABITS, "习惯", Icons.Filled.EventRepeat),
    TopLevelDest(Routes.REWARDS, "奖励", Icons.Filled.CardGiftcard),
    TopLevelDest(Routes.STATS, "统计", Icons.Filled.BarChart),
)

@Composable
fun HabitRecordAppScaffold() {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelDests.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDests.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onEditHabit = { navController.navigate(Routes.habitEdit(it)) },
                    onManageHabits = { navController.navigate(Routes.HABITS) },
                )
            }
            composable(Routes.HABITS) {
                HabitsScreen(onEditHabit = { navController.navigate(Routes.habitEdit(it)) })
            }
            composable(Routes.REWARDS) {
                RewardsScreen()
            }
            composable(Routes.STATS) {
                StatsScreen()
            }
            composable(
                route = Routes.HABIT_EDIT,
                arguments = listOf(navArgument("habitId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
            ) { entry ->
                val habitId = entry.arguments?.getLong("habitId") ?: -1L
                HabitEditScreen(
                    habitId = habitId.takeIf { it >= 0 },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
