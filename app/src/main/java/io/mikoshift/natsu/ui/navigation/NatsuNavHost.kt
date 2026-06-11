package io.mikoshift.natsu.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.mikoshift.natsu.di.AppContainer
import io.mikoshift.natsu.ui.dictionaries.DictionariesScreen
import io.mikoshift.natsu.ui.dictionaries.DictionariesViewModel
import io.mikoshift.natsu.ui.library.LibraryScreen
import io.mikoshift.natsu.ui.library.LibraryViewModel
import io.mikoshift.natsu.ui.reader.ReaderScreen
import io.mikoshift.natsu.ui.reader.ReaderViewModel

@Composable
fun NatsuNavHost(
    appContainer: AppContainer,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier,
    ) {
        composable(Routes.LIBRARY) {
            val viewModel: LibraryViewModel = viewModel(factory = appContainer.viewModelFactory)
            LibraryScreen(
                viewModel = viewModel,
                onDocumentClick = { documentId ->
                    navController.navigate(Routes.reader(documentId))
                },
            )
        }
        composable(Routes.DICTIONARIES) {
            val viewModel: DictionariesViewModel = viewModel(factory = appContainer.viewModelFactory)
            DictionariesScreen(viewModel = viewModel)
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")
                ?: return@composable
            val viewModel: ReaderViewModel = viewModel(
                key = documentId,
                factory = appContainer.viewModelFactory,
            )
            ReaderScreen(
                viewModel = viewModel,
                documentId = documentId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
