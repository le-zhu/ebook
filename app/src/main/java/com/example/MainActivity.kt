package com.example

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import com.example.ui.reader.EpubContent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.LocalLibrary
import com.example.ui.bookshelf.BookshelfScreen
import com.example.ui.bookshelf.BookshelfViewModel
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderViewModel
import com.example.ui.theme.EpubReaderTheme
import com.example.ui.EbookNotice

const val ROUTE_BOOKSHELF = "bookshelf"
const val ROUTE_READER_ARG_BOOK_ID = "bookId"
const val ROUTE_READER = "reader/{$ROUTE_READER_ARG_BOOK_ID}"

class MainActivity : FragmentActivity() {
    private val imports: ImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
        // Readium requires a live Publication. Recreate restored navigators from the saved Locator.
        supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().forEach {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) accept(intent)
        setContent {
            EpubReaderTheme { EbookApp((application as ReaderApplication).library, imports, this) }
        }
    }

    private fun accept(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_VIEW) incoming.data?.let { uri ->
            if (uri.scheme == "content" || uri.scheme == "file") imports.submit(listOf(uri), open = true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }
}

@Composable
private fun EbookApp(repository: LocalLibrary, imports: ImportViewModel, activity: FragmentActivity) {
    val navController = rememberNavController()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        folder?.let(imports::submitFolder)
    }
    val importStatus by imports.progress.collectAsStateWithLifecycle()
    val messages = remember { SnackbarHostState() }
    val requestedBook by imports.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(requestedBook) {
        val id = requestedBook ?: return@LaunchedEffect
        if (navController.currentBackStackEntry?.arguments?.getString(ROUTE_READER_ARG_BOOK_ID) != id) {
            navController.navigate("reader/$id") {
                popUpTo(ROUTE_BOOKSHELF)
                launchSingleTop = true
            }
        }
        imports.opened(id)
    }
    LaunchedEffect(imports, navController) {
        imports.results.collect { result ->
            if (navController.currentDestination?.route == ROUTE_BOOKSHELF) launch {
                messages.currentSnackbarData?.dismiss()
                val answer = messages.showSnackbar(result.message,
                    actionLabel = result.readActionId?.let { activity.getString(R.string.bookshelf_imported_action_read) },
                    duration = SnackbarDuration.Short)
                if (answer == SnackbarResult.ActionPerformed && result.readActionId != null)
                    navController.navigate("reader/" + result.readActionId) { launchSingleTop = true }
            } else Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
        }
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_BOOKSHELF,
            enterTransition = {
                fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 8 }
            },
            exitTransition = {
                fadeOut(tween(180))
            },
            popEnterTransition = {
                fadeIn(tween(220))
            },
            popExitTransition = {
                fadeOut(tween(200)) + slideOutHorizontally(tween(240)) { it / 8 }
            },
        ) {
            composable(route = ROUTE_BOOKSHELF) {
                val bookshelfViewModel: BookshelfViewModel = viewModel(
                    factory = remember(repository) { BookshelfViewModel.provideFactory(repository) },
                )
                BookshelfScreen(
                    viewModel = bookshelfViewModel,
                    snackbarHostState = messages,
                    onImport = { picker.launch(null) },
                    onOpenBook = { bookId ->
                        navController.navigate("reader/$bookId") {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = ROUTE_READER,
                arguments = listOf(
                    navArgument(ROUTE_READER_ARG_BOOK_ID) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString(ROUTE_READER_ARG_BOOK_ID).orEmpty()
                val readerViewModel: ReaderViewModel = viewModel(
                    key = "reader_vm_$bookId",
                    factory = remember(bookId, repository) {
                        ReaderViewModel.provideFactory(bookId = bookId, repository = repository)
                    },
                )
                ReaderScreen(
                    viewModel = readerViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    readerContent = { modifier, state, palette ->
                        EpubContent(activity, readerViewModel, state, palette, modifier)
                    },
                )
            }
        }
        importStatus?.let { message ->
            EbookNotice(message, Modifier.align(Alignment.TopCenter).statusBarsPadding())
        }
    }
}
