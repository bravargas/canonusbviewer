package com.brainer.canonusbviewer.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch

private enum class Screen { VIEWER, SETTINGS }

@Composable
fun AppRoot(
    viewModel: ViewerViewModel = viewModel(),
    setImmersive: (Boolean) -> Unit
) {
    val photos by viewModel.photos.collectAsState()
    val zoomStates by viewModel.zoomStates.collectAsState()
    val overlayVisible by viewModel.overlayVisible.collectAsState()
    val status by viewModel.status.collectAsState()

    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val folderContains by settingsStore.cameraRelativePathContains.collectAsState(initial = "")

    var screen by remember { mutableStateOf(Screen.VIEWER) }
    val pagerState = rememberPagerState(initialPage = 0) { photos.size }
    val scope = rememberCoroutineScope()

    when (screen) {
        Screen.VIEWER -> {
            setImmersive(true)
            ViewerScreen(
                status = status,
                photos = photos,
                pagerState = pagerState,
                zoomStates = zoomStates,
                overlayVisible = overlayVisible,
                onZoomStateChange = { index, zoomState -> viewModel.onZoomStateChange(index, zoomState) },
                onToggleOverlay = { viewModel.toggleOverlay() },
                onHideOverlay = { viewModel.hideOverlay() },
                onOpenSettings = { screen = Screen.SETTINGS },
                onSelectIndex = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
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
