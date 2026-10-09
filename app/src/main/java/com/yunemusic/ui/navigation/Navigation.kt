package com.yunemusic.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yunemusic.R
import com.yunemusic.ui.carmode.CarModeScreen
import com.yunemusic.ui.discover.DiscoverScreen
import com.yunemusic.ui.library.LibraryScreen
import com.yunemusic.ui.player.MiniPlayer
import com.yunemusic.ui.player.PlayerScreen
import com.yunemusic.ui.player.PlayerViewModel
import com.yunemusic.ui.settings.SettingsScreen
import com.yunemusic.ui.theme.*

sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen(
        route = "home",
        labelRes = R.string.nav_discover,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore
    )
    object Library : Screen(
        route = "library",
        labelRes = R.string.nav_library,
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic
    )
    object Settings : Screen(
        route = "settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    object Player : Screen(
        route = "player",
        labelRes = R.string.nav_player,
        selectedIcon = Icons.Filled.MusicNote,
        unselectedIcon = Icons.Outlined.MusicNote
    )
    object CarMode : Screen(
        route = "carmode",
        labelRes = R.string.nav_car_mode,
        selectedIcon = Icons.Filled.DirectionsCar,
        unselectedIcon = Icons.Outlined.DirectionsCar
    )
}

val bottomNavScreens = listOf(Screen.Home, Screen.Library, Screen.Settings)

@Composable
fun YuneMusicNavHost(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route !in listOf(
        Screen.Player.route,
        Screen.CarMode.route
    )

    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    fun navigateToPlayer() {
        navController.navigate(Screen.Player.route) { launchSingleTop = true }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    AnimatedVisibility(
                        visible = playerUiState.currentTrack != null,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        playerUiState.currentTrack?.let { track ->
                            MiniPlayer(
                                track = track,
                                isPlaying = playerUiState.isPlaying,
                                isBusy = playerUiState.isLoading || playerUiState.isBuffering,
                                progress = playerUiState.progress,
                                onPlayPause = { playerViewModel.togglePlayPause() },
                                onSkipNext = { playerViewModel.skipNext() },
                                onClick = { navigateToPlayer() }
                            )
                        }
                    }

                    NavigationBar(
                        containerColor = SurfaceDark,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavScreens.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true
                            val label = stringResource(screen.labelRes)

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SignalOrange,
                                    selectedTextColor = SignalOrange,
                                    unselectedIconColor = TextTertiary,
                                    unselectedTextColor = TextTertiary,
                                    indicatorColor = SignalContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                DiscoverScreen(
                    onTrackClick = { track ->
                        playerViewModel.playTrack(track)
                        navigateToPlayer()
                    },
                    onPlayNext = { track -> playerViewModel.playNext(track) },
                    onAddToQueue = { track -> playerViewModel.addToQueue(track) },
                    onAddToLibrary = { track -> playerViewModel.likeTrackDirect(track) },
                    onDownload = { track -> playerViewModel.downloadTrack(track) },
                    onPlayAll = { tracks ->
                        playerViewModel.setShuffle(false)
                        playerViewModel.playQueue(tracks, 0)
                        navigateToPlayer()
                    },
                    onShuffleAll = { tracks ->
                        playerViewModel.setShuffle(true)
                        playerViewModel.playQueue(tracks.shuffled(), 0)
                        navigateToPlayer()
                    }
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onTrackClick = { track ->
                        playerViewModel.playTrack(track)
                        navigateToPlayer()
                    },
                    onPlayAll = { tracks ->
                        playerViewModel.setShuffle(false)
                        playerViewModel.playQueue(tracks, 0)
                        navigateToPlayer()
                    },
                    onShuffleAll = { tracks ->
                        playerViewModel.setShuffle(true)
                        playerViewModel.playQueue(tracks.shuffled(), 0)
                        navigateToPlayer()
                    },
                    onPlayNext = { track -> playerViewModel.playNext(track) },
                    onAddToQueue = { track -> playerViewModel.addToQueue(track) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onCarModeNavigate = {
                        navController.navigate(Screen.CarMode.route)
                    }
                )
            }
            composable(Screen.Player.route) {
                PlayerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onQueueClick = { playerViewModel.toggleQueueVisibility() },
                    viewModel = playerViewModel
                )
            }
            composable(Screen.CarMode.route) {
                CarModeScreen(
                    onExitCarMode = { navController.popBackStack() },
                    viewModel = playerViewModel
                )
            }
        }
    }
}
