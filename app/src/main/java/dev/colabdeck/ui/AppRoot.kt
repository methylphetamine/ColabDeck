package dev.colabdeck.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.colabdeck.MainViewModel
import dev.colabdeck.ui.screens.AuthScreen
import dev.colabdeck.ui.screens.FileBrowserScreen
import dev.colabdeck.ui.screens.GuideScreen
import dev.colabdeck.ui.screens.HomeScreen
import dev.colabdeck.ui.screens.ProfileEditScreen
import dev.colabdeck.ui.screens.ProfilesScreen
import dev.colabdeck.ui.screens.SettingsScreen
import dev.colabdeck.ui.screens.TerminalScreen
import dev.colabdeck.ui.theme.Ink
import dev.colabdeck.ui.theme.InkRaised
import dev.colabdeck.ui.theme.TextPrimary

@Composable
fun AppRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.bootstrap()
    }

    LaunchedEffect(Unit) {
        vm.events.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    onSignIn = { nav.navigate("auth") },
                    onOpenTerminal = { endpoint -> nav.navigate("terminal/${Uri.encode(endpoint)}") },
                    onOpenFiles = { endpoint -> nav.navigate("files/${Uri.encode(endpoint)}") },
                    onOpenProfiles = { nav.navigate("profiles") },
                    onOpenGuide = { nav.navigate("guide") },
                    onOpenSettings = { nav.navigate("settings") }
                )
            }

            composable("auth") {
                AuthScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpenGuide = { nav.navigate("guide") }
                )
            }

            composable("profiles") {
                ProfilesScreen(
                    vm = vm,
                    onConnect = { id -> nav.navigate("terminal-profile/$id") },
                    onEdit = { id -> nav.navigate("profile/$id") },
                    onAdd = { nav.navigate("profile/new") }
                )
            }

            composable(
                route = "profile/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                ProfileEditScreen(
                    vm = vm,
                    profileId = entry.arguments?.getString("id") ?: "new",
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                route = "terminal/{endpoint}",
                arguments = listOf(navArgument("endpoint") { type = NavType.StringType })
            ) { entry ->
                val endpoint = entry.arguments?.getString("endpoint")
                    ?.let { Uri.decode(it) }
                    .orEmpty()
                TerminalScreen(
                    vm = vm,
                    endpoint = endpoint.ifBlank { null },
                    profileId = null,
                    onBack = { nav.popBackStack() },
                    onOpenFiles = { target -> nav.navigate("files/${Uri.encode(target)}") }
                )
            }

            composable(
                route = "terminal-profile/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                TerminalScreen(
                    vm = vm,
                    endpoint = null,
                    profileId = entry.arguments?.getString("id"),
                    onBack = { nav.popBackStack() },
                    onOpenFiles = { }
                )
            }

            composable(
                route = "files/{endpoint}",
                arguments = listOf(navArgument("endpoint") { type = NavType.StringType })
            ) { entry ->
                val endpoint = entry.arguments?.getString("endpoint")
                    ?.let { Uri.decode(it) }
                    .orEmpty()
                FileBrowserScreen(
                    vm = vm,
                    endpoint = endpoint,
                    onBack = { nav.popBackStack() }
                )
            }

            composable("guide") {
                GuideScreen(onBack = { nav.popBackStack() })
            }

            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.navigate("auth") },
                    onOpenGuide = { nav.navigate("guide") },
                    onOpenProfiles = { nav.navigate("profiles") }
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = InkRaised,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
