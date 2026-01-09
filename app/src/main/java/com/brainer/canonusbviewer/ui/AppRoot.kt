package com.brainer.canonusbviewer.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brainer.canonusbviewer.data.settings.SettingsStore
import com.brainer.canonusbviewer.ui.settings.SettingsScreen
import com.brainer.canonusbviewer.ui.viewer.ViewerScreen
import com.brainer.canonusbviewer.viewmodel.ViewerViewModel
import com.brainer.canonusbviewer.viewmodel.ViewerViewModelFactory
import kotlinx.coroutines.launch

private enum class Screen { VIEWER, SETTINGS }

@Composable
fun AppRoot(
    viewModel: ViewerViewModel = viewModel(factory = ViewerViewModelFactory(LocalContext.current)),
    setImmersive: (Boolean) -> Unit
) {
    val photos by viewModel.photos.collectAsState()
    val zoomStates by viewModel.zoomStates.collectAsState()
    val overlayVisible by viewModel.overlayVisible.collectAsState()
    val status by viewModel.status.collectAsState()
    val minRatingFilter by viewModel.minRatingFilter.collectAsState()

    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val folderContains by settingsStore.cameraRelativePathContains.collectAsState(initial = "")

    var screen by remember { mutableStateOf(Screen.VIEWER) }
    val pagerState = rememberPagerState(initialPage = 0) { photos.size }
    val scope = rememberCoroutineScope()

    // When the RATING FILTER changes, scroll to the first page.
    LaunchedEffect(minRatingFilter) {
        if (pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    when (screen) {
        Screen.VIEWER -> {
            setImmersive(true)
            ViewerScreen(
                status = status,
                photos = photos,
                pagerState = pagerState,
                zoomStates = zoomStates,
                overlayVisible = overlayVisible,
                minRatingFilter = minRatingFilter,
                onZoomStateChange = { index, zoomState -> viewModel.onZoomStateChange(index, zoomState) },
                onToggleOverlay = { viewModel.toggleOverlay() },
                onHideOverlay = { viewModel.hideOverlay() },
                onOpenSettings = { screen = Screen.SETTINGS },
                onSelectIndex = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                onSetRating = { mediaStoreId, rating -> viewModel.setRating(mediaStoreId, rating) },
                onSetMinRatingFilter = { rating -> viewModel.setMinRatingFilter(rating) }
            )
        }
        Screen.SETTINGS -> {
            setImmersive(false)
            SettingsScreen(
                currentFolderContains = folderContains,
                onSaveFolderContains = { newValue ->
                    scope.launch {
                        settingsStore.setCameraRelativePathContains(newValue)
                    }
                },
                onBack = { screen = Screen.VIEWER }
            )
        }
    }
}
