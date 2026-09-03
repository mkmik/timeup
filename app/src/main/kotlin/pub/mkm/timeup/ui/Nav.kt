package pub.mkm.timeup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pub.mkm.timeup.graph

@Composable
fun TimeUpNavHost() {
    val context = LocalContext.current
    val graph = context.graph
    val vm: MainViewModel = viewModel { MainViewModel(graph) }
    val nav = rememberNavController()
    val optionalId = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })

    NavHost(navController = nav, startDestination = "overview") {
        composable("overview") {
            val ui by vm.overview.collectAsStateWithLifecycle()
            OverviewScreen(
                ui = ui,
                onToggle = vm::toggle,
                onLog = { id -> nav.navigate("log?id=$id") },
                onEdit = { id -> nav.navigate("edit?id=$id") },
                onNew = { nav.navigate("edit") },
                onLogAny = { nav.navigate("log") },
                onExport = { vm.export { intent -> context.startActivity(intent) } },
                onSettings = { nav.navigate("settings") },
                onAbout = { nav.navigate("about") },
            )
        }
        composable("edit?id={id}", arguments = optionalId) { entry ->
            val id = entry.arguments?.getString("id")
            val state by vm.state.collectAsStateWithLifecycle()
            val existing = id?.let { state.priorities[it] }
            EditorScreen(
                existing = existing,
                isFirst = existing == null && state.activePriorities.isEmpty(),
                onSave = { name, color, budget, wrapUp ->
                    if (existing == null) vm.createPriority(name, color, budget, wrapUp)
                    else vm.updatePriority(existing.id, name, color, budget, wrapUp)
                },
                onArchive = { existing?.let { vm.archive(it.id) }; nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable("log?id={id}", arguments = optionalId) { entry ->
            val id = entry.arguments?.getString("id")
            val state by vm.state.collectAsStateWithLifecycle()
            LogScreen(
                priorities = state.activePriorities,
                preselectedId = id,
                onLog = { pid, minutes, endAt ->
                    vm.log(pid, minutes, endAt)
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                onExport = { vm.export { intent -> context.startActivity(intent) } },
                onDeleteEverything = { vm.deleteEverything() },
                onBack = { nav.popBackStack() },
            )
        }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
