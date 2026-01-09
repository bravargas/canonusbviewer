package com.brainer.canonusbviewer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.core.content.ContextCompat
import com.brainer.canonusbviewer.ui.settings.SettingsScreen
import com.brainer.canonusbviewer.ui.viewer.ViewerScreen
import com.brainer.canonusbviewer.viewmodel.ViewerViewModel
import kotlinx.coroutines.launch

private enum class Screen { VIEWER, SETTINGS }

@Composable
fun AppRoot(
    viewModel: ViewerViewModel,
    setImmersive: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsState()
    val zoomStates by viewModel.zoomStates.collectAsState()
    val overlayVisible by viewModel.overlayVisible.collectAsState()
    val status by viewModel.status.collectAsState()
    val folderContains by viewModel.folderContains.collectAsState()

    var screen by remember { mutableStateOf(Screen.VIEWER) }
    val pagerState = rememberPagerState(initialPage = 0) { photos.size }
    val thumbnailListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // --- PERMISSION LOGIC --- 
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, explicitly trigger a refresh.
            viewModel.refresh()
        } else {
            // You can optionally handle the permission denial case here, e.g., show a message.
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            // If permission is already granted, refresh immediately.
            viewModel.refresh()
        }
    }
    // --- END PERMISSION LOGIC ---

    when (screen) {
        Screen.VIEWER -> {
            setImmersive(true)
            ViewerScreen(
                status = status,
                photos = photos,
                pagerState = pagerState,
                thumbnailListState = thumbnailListState,
                zoomStates = zoomStates,
                overlayVisible = overlayVisible,
                onZoomStateChange = { index, zoomState -> viewModel.onZoomStateChange(index, zoomState) },
                onToggleOverlay = { viewModel.toggleOverlay() },
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
                    viewModel.setCameraRelativePathContains(newValue)
                },
                onBack = { screen = Screen.VIEWER }
            )
        }
    }
}
