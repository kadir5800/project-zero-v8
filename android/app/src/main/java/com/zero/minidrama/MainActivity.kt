package com.zero.minidrama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zero.minidrama.data.BookResult
import com.zero.minidrama.ui.AppViewModel
import com.zero.minidrama.ui.navigation.AppRoute
import com.zero.minidrama.ui.screens.DetailScreen
import com.zero.minidrama.ui.screens.HistoryScreen
import com.zero.minidrama.ui.screens.HomeScreen
import com.zero.minidrama.ui.screens.PlayerScreen
import com.zero.minidrama.ui.screens.SettingsScreen
import com.zero.minidrama.ui.theme.ZeroTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZeroTheme { ZeroApp() }
        }
    }
}

private enum class Tab { Home, History, Settings }

@Composable
private fun ZeroApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Tab.Home) }
    var route by remember { mutableStateOf(AppRoute.Main) }
    var lastBackPress by remember { mutableLongStateOf(0L) }

    fun navigateBack() {
        when (route) {
            AppRoute.Player -> {
                vm.clearPlay()
                route = if (state.bookDetail != null) AppRoute.Detail else AppRoute.Main
            }
            AppRoute.Detail -> {
                vm.clearBookDetail()
                route = AppRoute.Main
            }
            AppRoute.Main -> {
                if (tab != Tab.Home) {
                    tab = Tab.Home
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPress < 2000) {
                        (context as? ComponentActivity)?.finish()
                    } else {
                        lastBackPress = now
                        scope.launch {
                            snackbar.showSnackbar("Cikmak icin tekrar geri basin")
                        }
                    }
                }
            }
        }
    }

    BackHandler { navigateBack() }

    LaunchedEffect(state.playPayload) {
        if (state.playPayload != null) {
            route = AppRoute.Player
        } else if (route == AppRoute.Player) {
            route = if (state.bookDetail != null) AppRoute.Detail else AppRoute.Main
        }
    }

    fun playNextEpisode() {
        val detail = state.bookDetail ?: return
        val current = state.playPayload?.serial ?: return
        val next = detail.episodes.find { it.serial == current + 1 } ?: return
        vm.playEpisode(
            bookId = detail.bookId,
            bookName = detail.bookName,
            pic = state.selectedBookPic,
            serial = next.serial,
            chapterId = next.chapterId,
            cover = next.cover,
            isLock = next.isLock
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route == AppRoute.Main) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Home,
                        onClick = { tab = Tab.Home },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Kesfet") },
                        colors = colors
                    )
                    NavigationBarItem(
                        selected = tab == Tab.History,
                        onClick = { tab = Tab.History },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Gecmis") },
                        colors = colors
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Settings,
                        onClick = { tab = Tab.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Ayarlar") },
                        colors = colors
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        slideInHorizontally { it / 3 } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
                    } else {
                        slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
                    }
                },
                label = "nav"
            ) { currentRoute ->
                when (currentRoute) {
                    AppRoute.Player -> PlayerScreen(
                        payload = state.playPayload,
                        playUrl = state.playUrl,
                        isLoading = state.isLoadingPlay,
                        loadError = state.error,
                        onBack = { navigateBack() },
                        onError = { msg -> vm.reportPlayError(msg) },
                        onEnded = { playNextEpisode() },
                        onSwipeNext = { playNextEpisode() }
                    )
                    AppRoute.Detail -> DetailScreen(
                        detail = state.bookDetail,
                        coverPic = state.selectedBookPic,
                        currentSerial = state.playPayload?.serial,
                        isLoading = state.isLoadingBook,
                        onBack = { navigateBack() },
                        onPlayFirst = {
                            val detail = state.bookDetail ?: return@DetailScreen
                            val ep = detail.episodes.firstOrNull() ?: return@DetailScreen
                            vm.playEpisode(
                                detail.bookId, detail.bookName, state.selectedBookPic,
                                ep.serial, ep.chapterId, ep.cover, ep.isLock
                            )
                            route = AppRoute.Player
                        },
                        onEpisodeClick = { ep ->
                            val detail = state.bookDetail ?: return@DetailScreen
                            vm.playEpisode(
                                detail.bookId, detail.bookName, state.selectedBookPic,
                                ep.serial, ep.chapterId, ep.cover, ep.isLock
                            )
                            route = AppRoute.Player
                        }
                    )
                    AppRoute.Main -> when (tab) {
                        Tab.Home -> HomeScreen(
                            state = state,
                            onQueryChange = vm::setQuery,
                            onSearch = { vm.search(1) },
                            onPlatformChange = vm::setPlatform,
                            onLangChange = vm::setLang,
                            onCategoryChange = vm::setCategory,
                            onBookClick = { book: BookResult ->
                                vm.openBook(book)
                                route = AppRoute.Detail
                            },
                            onPageChange = vm::search,
                            onResumeHistory = { item ->
                                vm.resumeHistory(item)
                                route = AppRoute.Player
                            }
                        )
                        Tab.History -> HistoryScreen(
                            history = state.history,
                            onResume = { item ->
                                vm.resumeHistory(item)
                                route = AppRoute.Player
                            },
                            onClear = vm::clearHistory
                        )
                        Tab.Settings -> SettingsScreen(
                            platform = state.platform,
                            lang = state.lang,
                            onPlatformChange = vm::setPlatform,
                            onLangChange = vm::setLang
                        )
                    }
                }
            }
        }
    }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Hata") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = vm::clearError) { Text("Tamam") }
            }
        )
    }
}
