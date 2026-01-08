package com.brainer.canonusbviewer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen immersive by default
        setImmersive(true)

        setContent {
            MaterialTheme {
                AppRoot(
                    setImmersive = { enabled -> applyImmersiveMode(enabled) }
                )
            }
        }
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController
            if (enabled) {
                controller?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            if (enabled) {
                window.decorView.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            } else {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
}

private enum class Screen { VIEWER, SETTINGS }

private data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAddedSeconds: Long,
    val relativePath: String?
)

@Composable
private fun AppRoot(setImmersive: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }

    var screen by remember { mutableStateOf(Screen.VIEWER) }
    var overlayVisible by remember { mutableStateOf(true) }

    // Permission
    val perm = remember {
        if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES"
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var status by remember { mutableStateOf("Waiting for Canon imports...") }

    val requestPerm = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Permission granted. Waiting for Canon imports..." else "Permission denied."
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            requestPerm.launch(perm)
        } else {
            status = "Permission granted. Waiting for Canon imports..."
        }
    }

    // Config: folder filter (RELATIVE_PATH contains)
    val folderContains by settings.cameraRelativePathContains.collectAsState(initial = "Canon EOS R50")

    // Photos list (from MediaStore, system gallery)
    var photos by remember { mutableStateOf<List<MediaPhoto>>(emptyList()) }

    // Mark app start to avoid pulling very old photos if you want.
    // You can later turn this into a setting; for now it helps UX.
    val appStartSeconds = remember { System.currentTimeMillis() / 1000 }

    // Load photos helper
    fun refreshPhotos() {
        photos = queryCanonPhotos(context, folderContains, minDateAddedSeconds = 0L) // show all in that folder
        if (photos.isNotEmpty()) status = "Latest: ${photos.first().name}" else status = "No photos found in '$folderContains'."
    }

    // Initial load
    LaunchedEffect(folderContains) {
        refreshPhotos()
    }

    // Observe MediaStore changes -> refresh list, keep newest visible
    DisposableEffect(folderContains) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                // refresh, but if you want "only new since app open", use appStartSeconds here
                val updated = queryCanonPhotos(context, folderContains, minDateAddedSeconds = appStartSeconds)
                if (updated.isNotEmpty()) {
                    // Merge strategy: simplest = full refresh
                    refreshPhotos()
                }
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    // Viewer pager
    val pagerState = rememberPagerState(initialPage = 0) { photos.size }

    // If new photos arrive, force pager to newest (page 0)
    LaunchedEffect(photos.size) {
        if (photos.isNotEmpty()) {
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
                overlayVisible = overlayVisible,
                onToggleOverlay = { overlayVisible = !overlayVisible },
                onOpenSettings = { screen = Screen.SETTINGS },
                onRefresh = { refreshPhotos() },
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
                    scope.launch(Dispatchers.IO) {
                        settings.setCameraRelativePathContains(newValue.trim())
                    }
                },
                onBack = { screen = Screen.VIEWER }
            )
        }
    }
}

@Composable
private fun ViewerScreen(
    status: String,
    photos: List<MediaPhoto>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    overlayVisible: Boolean,
    onToggleOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSelectIndex: (Int) -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        // Full screen photo area; tap toggles overlay
        Box(
            Modifier
                .fillMaxSize()
                .clickable { onToggleOverlay() }
        ) {
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos yet.\nOpen Canon Camera Connect and take a photo.")
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val p = photos[page]
                    AsyncImage(
                        model = p.uri,
                        contentDescription = p.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit // IMPORTANT: no crop
                    )
                }
            }
        }

        if (overlayVisible) {
            // Top overlay (status + file name + buttons)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(12.dp)
                    .align(Alignment.TopCenter)
            ) {
                val current = photos.getOrNull(pagerState.currentPage)
                Text(
                    text = if (current != null) "File: ${current.name}" else "File: (none)",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh) { Text("Refresh") }
                    Button(onClick = onOpenSettings) { Text("Settings") }
                }
            }

            // Bottom thumbnails overlay
            if (photos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x88000000))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(photos, key = { _, item -> item.id }) { index, item ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable { onSelectIndex(index) }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (index == pagerState.currentPage) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color(0x55FFFFFF))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    currentFolderContains: String,
    onSaveFolderContains: (String) -> Unit,
    onBack: () -> Unit
) {
    var value by remember { mutableStateOf(currentFolderContains) }
    var savedMsg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text("Camera folder filter (MediaStore RELATIVE_PATH contains):")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Example: Canon EOS R50") }
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSaveFolderContains(value)
                savedMsg = "Saved."
            }) { Text("Save") }

            Button(onClick = onBack) { Text("Back") }
        }

        if (savedMsg.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(savedMsg)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Tip: If Canon Camera Connect changes the folder name, update the filter here.\n" +
                    "The viewer uses swipe for previous/next, and tap to show/hide overlays."
        )
    }
}

/**
 * Query images from MediaStore whose RELATIVE_PATH contains [folderContains],
 * sorted newest first.
 */
private fun queryCanonPhotos(
    context: Context,
    folderContains: String,
    minDateAddedSeconds: Long
): List<MediaPhoto> {

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.RELATIVE_PATH
    )

    val selection = buildString {
        append("${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?")
        if (minDateAddedSeconds > 0) {
            append(" AND ${MediaStore.Images.Media.DATE_ADDED} >= ?")
        }
    }

    val args = if (minDateAddedSeconds > 0) {
        arrayOf("%$folderContains%", minDateAddedSeconds.toString())
    } else {
        arrayOf("%$folderContains%")
    }

    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val out = mutableListOf<MediaPhoto>()
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        args,
        sortOrder
    )?.use { cursor ->
        val colId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val colName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val colDate = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val colRel = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(colId)
            val name = cursor.getString(colName) ?: "unknown.jpg"
            val dateAdded = cursor.getLong(colDate)
            val rel = cursor.getString(colRel)

            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            out.add(MediaPhoto(id, uri, name, dateAdded, rel))
        }
    }

    return out
}
