package dev.splitwise.offline.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.splitwise.offline.ui.expenses.AddExpenseScreen
import dev.splitwise.offline.ui.groups.GroupDetailScreen
import dev.splitwise.offline.ui.groups.GroupListScreen
import dev.splitwise.offline.ui.groups.NewGroupScreen

object Routes {
    const val GROUPS = "groups"
    const val NEW_GROUP = "groups/new"
    const val GROUP_DETAIL = "groups/{groupId}"
    const val ADD_EXPENSE = "groups/{groupId}/expenses/new"
    fun groupDetail(id: String) = "groups/$id"
    fun addExpense(id: String) = "groups/$id/expenses/new"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.GROUPS) {

        composable(Routes.GROUPS) {
            GroupListScreen(
                onNewGroup = { nav.navigate(Routes.NEW_GROUP) },
                onOpenGroup = { id -> nav.navigate(Routes.groupDetail(id)) },
            )
        }

        composable(Routes.NEW_GROUP) {
            NewGroupScreen(onCreated = { id -> nav.navigate(Routes.groupDetail(id)) { popUpTo(Routes.GROUPS) } })
        }

        composable(
            route = Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            GroupDetailScreen(
                groupId = groupId,
                onAddExpense = { nav.navigate(Routes.addExpense(groupId)) },
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.ADD_EXPENSE,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { entry ->
            val groupId = entry.arguments?.getString("groupId").orEmpty()
            AddExpenseScreen(
                groupId = groupId,
                onDone = { nav.popBackStack() },
            )
        }
    }
}