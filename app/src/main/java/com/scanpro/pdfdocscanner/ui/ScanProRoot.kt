package com.scanpro.pdfdocscanner.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.ui.documents.DocumentsScreen
import com.scanpro.pdfdocscanner.ui.folders.FolderDetailScreen
import com.scanpro.pdfdocscanner.ui.folders.FoldersScreen
import com.scanpro.pdfdocscanner.ui.home.HomeScreen
import com.scanpro.pdfdocscanner.ui.imagetopdf.ImageToPdfScreen
import com.scanpro.pdfdocscanner.ui.importdocs.ImportScreen
import com.scanpro.pdfdocscanner.ui.navigation.Routes
import com.scanpro.pdfdocscanner.ui.pdftools.PdfToolsScreen
import com.scanpro.pdfdocscanner.ui.scan.ScanScreen
import com.scanpro.pdfdocscanner.ui.settings.SettingsScreen
import com.scanpro.pdfdocscanner.ui.viewer.ViewerScreen

private const val ANIM_MS = 280

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    slideInHorizontally(tween(ANIM_MS)) { it / 6 } + fadeIn(tween(ANIM_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    slideOutHorizontally(tween(ANIM_MS)) { -it / 8 } + fadeOut(tween(ANIM_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(tween(ANIM_MS)) { -it / 8 } + fadeIn(tween(ANIM_MS))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(tween(ANIM_MS)) { it / 6 } + fadeOut(tween(ANIM_MS))

@Composable
fun ScanProRoot(container: AppContainer) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { enter() },
            exitTransition = { exit() },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onScanDocument = { nav.navigate(Routes.scan(Routes.MODE_DOCUMENT)) },
                    onImageToPdf = { nav.navigate(Routes.IMAGE_TO_PDF) },
                    onPdfTools = { nav.navigate(Routes.PDF_TOOLS) },
                    onIdScan = { nav.navigate(Routes.scan(Routes.MODE_ID)) },
                    onImport = { nav.navigate(Routes.IMPORT) },
                    onDocuments = { nav.navigate(Routes.documents()) },
                    onSearch = { nav.navigate(Routes.documents(focusSearch = true)) },
                    onOpenDocument = { id -> nav.navigate(Routes.viewer(id)) },
                    onFolders = { nav.navigate(Routes.FOLDERS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                Routes.SCAN,
                arguments = listOf(navArgument(Routes.ARG_MODE) { type = NavType.StringType }),
            ) { entry ->
                val mode = entry.arguments?.getString(Routes.ARG_MODE) ?: Routes.MODE_DOCUMENT
                ScanScreen(
                    container = container,
                    isIdMode = mode == Routes.MODE_ID,
                    onBack = back,
                    onOpenDocuments = {
                        nav.navigate(Routes.documents()) { popUpTo(Routes.HOME) }
                    },
                    onOpenViewer = { id -> nav.navigate(Routes.viewer(id)) },
                )
            }
            composable(Routes.IMAGE_TO_PDF) {
                ImageToPdfScreen(container, back) { id -> nav.navigate(Routes.viewer(id)) }
            }
            composable(Routes.PDF_TOOLS) { PdfToolsScreen(container, back) }
            composable(Routes.IMPORT) {
                ImportScreen(
                    container = container,
                    onBack = back,
                    onOpenDocuments = { nav.navigate(Routes.documents()) { popUpTo(Routes.HOME) } },
                )
            }
            composable(
                Routes.DOCUMENTS,
                arguments = listOf(
                    navArgument(Routes.ARG_SEARCH) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
            ) { entry ->
                val focusSearch = entry.arguments?.getBoolean(Routes.ARG_SEARCH) ?: false
                DocumentsScreen(
                    container = container,
                    focusSearch = focusSearch,
                    onBack = back,
                    onOpenViewer = { id -> nav.navigate(Routes.viewer(id)) },
                )
            }
            composable(Routes.FOLDERS) {
                FoldersScreen(container, back) { id -> nav.navigate(Routes.folder(id)) }
            }
            composable(
                Routes.FOLDER_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
                FolderDetailScreen(container, id, back) { docId -> nav.navigate(Routes.viewer(docId)) }
            }
            composable(
                Routes.VIEWER,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
                ViewerScreen(container, id, back)
            }
            composable(Routes.SETTINGS) { SettingsScreen(container, back) }
        }
    }
}
