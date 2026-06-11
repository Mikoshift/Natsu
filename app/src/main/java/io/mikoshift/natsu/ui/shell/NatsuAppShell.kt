package io.mikoshift.natsu.ui.shell

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.mikoshift.natsu.di.AppContainer
import io.mikoshift.natsu.ui.drawer.NatsuDrawerContent
import io.mikoshift.natsu.ui.navigation.NatsuNavHost
import io.mikoshift.natsu.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun NatsuAppShell(
    appContainer: AppContainer,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showDrawer = currentRoute?.startsWith("reader") != true
    val drawerWidth = (LocalConfiguration.current.screenWidthDp * 0.67f).dp

    val openDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    val closeDrawer: () -> Unit = {
        scope.launch { drawerState.close() }
    }

    CompositionLocalProvider(LocalDrawerOpen provides openDrawer) {
        if (showDrawer) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier
                            .width(drawerWidth)
                            .fillMaxHeight(),
                    ) {
                        NatsuDrawerContent(
                            selectedRoute = currentRoute,
                            onNavigateToLibrary = {
                                closeDrawer()
                                if (currentRoute != Routes.LIBRARY) {
                                    navController.navigate(Routes.LIBRARY) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onNavigateToDictionaries = {
                                closeDrawer()
                                if (currentRoute != Routes.DICTIONARIES) {
                                    navController.navigate(Routes.DICTIONARIES) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onNavigateToSettings = {
                                closeDrawer()
                                if (currentRoute != Routes.SETTINGS) {
                                    navController.navigate(Routes.SETTINGS) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                        )
                    }
                },
                modifier = modifier,
            ) {
                NatsuNavHost(
                    appContainer = appContainer,
                    navController = navController,
                )
            }
        } else {
            NatsuNavHost(
                appContainer = appContainer,
                navController = navController,
                modifier = modifier,
            )
        }
    }
}
