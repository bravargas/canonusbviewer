package com.brainer.canonusbviewer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/* ============================================================
   Activity
   ============================================================ */

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply immersive after decorView exists
        window.decorView.post { applyImmersiveMode(true) }

        setContent {
            MaterialTheme {
                AppRoot(setImmersive = { enabled -> applyImmersiveMode(enabled) })
            }
        }
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.decorView.windowInsetsController
            if (controller == null) {
                window.decorView.post { applyImmersiveMode(enabled) }
                return
            }

            if (enabled) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (enabled) {
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                } else {
                    View.SYSTEM_UI_FLAG_VISIBLE
                }
        }
    }
}

/* ============================================================
   Models / State
   ============================================================ */

private enum class Screen { VIEWER, SETTINGS }

private data class MediaPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAddedSeconds: Long,
    val relativePath: String?
)

private data class ZoomState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero
)

/* ============================================================
   Root
   ============================================================ */

@Composable
private fun AppRoot(setImmersive: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }

    var screen by remember { mutableStateOf(Screen.VIEWER) }
    var overlayVisible by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Waiting for Canon imports...") }

    // Permission
    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val requestPerm = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Permission granted. Waiting for Canon imports..." else "Permission denied."
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPerm.launch(permission)
        } else {
            status = "Permission granted. Waiting for Canon imports..."
        }
    }

    // Config: folder filter
    val folderContains by settings.cameraRelativePathContains.collectAsState(initial = "Canon EOS R50")

    // Photos list
    var photos by remember { mutableStateOf<List<MediaPhoto>>(emptyList()) }

    // Mark app start
    val appStartSeconds = remember { System.currentTimeMillis() / 1000 }

    fun refreshPhotos() {
        photos = queryCanonPhotos(context, folderContains, minDateAddedSeconds = 0L)
        status = if (photos.isNotEmpty()) "Latest: ${photos.first().name}" else "No photos found in '$folderContains'."
    }

    LaunchedEffect(folderContains) { refreshPhotos() }

    // Observe MediaStore changes -> refresh list
    DisposableEffect(folderContains) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val updated = queryCanonPhotos(context, folderContains, minDateAddedSeconds = appStartSeconds)
                if (updated.isNotEmpty()) {
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
                onShowOverlay = { overlayVisible = true },
                onHideOverlay = { overlayVisible = false },
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

/* ============================================================
   Viewer
   ============================================================ */

@Composable
private fun ViewerScreen(
    status: String,
    photos: List<MediaPhoto>,
    pagerState: PagerState,
    overlayVisible: Boolean,
    onToggleOverlay: () -> Unit,
    onShowOverlay: () -> Unit,
    onHideOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSelectIndex: (Int) -> Unit
) {
    // ===== Zoom state per page =====
    var zoomStates by remember(photos) {
        mutableStateOf(List(photos.size) { ZoomState() })
    }
    // ==============================

    // ===== PASO C: Auto-hide overlay =====
    val autoHideMillis = 2500L
    var lastUserActivity by remember { mutableStateOf(System.currentTimeMillis()) }

    fun bumpUserActivity() {
        lastUserActivity = System.currentTimeMillis()
    }

    LaunchedEffect(overlayVisible, lastUserActivity) {
        if (!overlayVisible) return@LaunchedEffect
        kotlinx.coroutines.delay(autoHideMillis)

        val idleFor = System.currentTimeMillis() - lastUserActivity
        if (overlayVisible && idleFor >= autoHideMillis) {
            onHideOverlay()
        }
    }
    // ===== FIN PASO C =====

    // ===== ESTO ES LO QUE FALTABA =====
    val currentZoom = zoomStates.getOrNull(pagerState.currentPage) ?: ZoomState()
    val pagerScrollEnabled = currentZoom.scale <= 1.001f
    // ================================

    Box(Modifier.fillMaxSize()) {

        // Full screen photo area
        Box(Modifier.fillMaxSize()) {
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos yet.\nOpen Canon Camera Connect and take a photo.")
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = pagerScrollEnabled
                ) { page ->
                    val p = photos[page]

                    ZoomableAsyncImage(
                        model = p.uri,
                        contentDescription = p.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        maxScale = 6f,
                        doubleTapScale = 2f,
                        zoomState = zoomStates[page],
                        onZoomStateChange = { zs ->
                            zoomStates = zoomStates.toMutableList().also { it[page] = zs }
                        },
                        onUserInteraction = { bumpUserActivity() },
                        onSingleTap = {
                            bumpUserActivity()
                            if (overlayVisible) onHideOverlay() else onShowOverlay()
                        }

                    )
                }
            }
        }

        if (overlayVisible) {
            // Top overlay
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
                    Button(onClick = { bumpUserActivity(); onRefresh() }) { Text("Refresh") }
                    Button(onClick = { bumpUserActivity(); onOpenSettings() }) { Text("Settings") }

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
                                .clickable {
                                    bumpUserActivity()
                                    onSelectIndex(index)
                                }

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

/* ============================================================
   Settings
   ============================================================ */

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
                    "Viewer: swipe for previous/next. Single tap toggles overlays. Double tap zooms to finger. Pinch zoom supported."
        )
    }
}

/* ============================================================
   Zoomable Image
   ============================================================ */

/**
 * Zoomable image:
 * - Pinch zoom
 * - Pan (only when zoomed)
 * - Double tap zoom to finger with animation
 * - Single tap calls [onSingleTap] (overlay show/hide)
 */
@Composable
private fun ZoomableAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxScale: Float = 6f,
    doubleTapScale: Float = 2f,
    zoomState: ZoomState,
    onZoomStateChange: (ZoomState) -> Unit,
    onUserInteraction: () -> Unit,
    onSingleTap: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Fast state
    var scale by remember(model) { mutableStateOf(zoomState.scale) }
    var offset by remember(model) { mutableStateOf(zoomState.offset) }

    // Detect 2 fingers to enable pinch
    var twoFingersDown by remember { mutableStateOf(false) }

    // Fling job (to cancel when user touches again)
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // For fling decay
    val decay = androidx.compose.animation.core.exponentialDecay<Float>()

    fun clampOffset(s: Float, o: Offset): Offset {
        if (containerSize.width == 0 || containerSize.height == 0) return Offset.Zero
        if (s <= 1f) return Offset.Zero

        val maxX = (containerSize.width * (s - 1f)) / 2f
        val maxY = (containerSize.height * (s - 1f)) / 2f

        return Offset(
            o.x.coerceIn(-maxX, maxX),
            o.y.coerceIn(-maxY, maxY)
        )
    }

    fun stopFling() {
        flingJob?.cancel()
        flingJob = null
    }


    // Animatables ONLY for double-tap animation
    val scaleAnim = remember(model) { Animatable(scale) }
    val offsetXAnim = remember(model) { Animatable(offset.x) }
    val offsetYAnim = remember(model) { Animatable(offset.y) }

    suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
        coroutineScope {
            launch { scaleAnim.animateTo(targetScale, tween(220)) }
            launch { offsetXAnim.animateTo(targetOffset.x, tween(220)) }
            launch { offsetYAnim.animateTo(targetOffset.y, tween(220)) }
        }
        scale = scaleAnim.value
        offset = Offset(offsetXAnim.value, offsetYAnim.value)
        onZoomStateChange(ZoomState(scale, offset))
    }

    // Enable transformable ONLY when 2 fingers are down (pinch start or pinch while zoomed)
    val transformEnabled = twoFingersDown

    // One-finger pan + fling ONLY when zoomed
    val panEnabled = scale > 1.001f

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .onSizeChanged { containerSize = it }

            // (1) Track pointer count (2 fingers)
            .pointerInput(model) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        twoFingersDown = pressed >= 2
                        if (pressed == 0) twoFingersDown = false
                    }
                }
            }

            .then(
                if (twoFingersDown) {
                    Modifier.pointerInput(model, containerSize) {
                        // DetectTransformGestures "viejo": solo tiene onGesture(centroid, pan, zoom, rotation)
                        while (true) {
                            var started = false

                            detectTransformGestures { _, pan, zoom, _ ->
                                if (!started) {
                                    started = true
                                    stopFling()
                                }

                                onUserInteraction()

                                val newScale = (scale * zoom).coerceIn(1f, maxScale)
                                scale = newScale

                                offset =
                                    if (newScale > 1f) clampOffset(newScale, offset + pan)
                                    else Offset.Zero

                                onZoomStateChange(ZoomState(scale, offset))
                            }

                            // Gesture ended
                            if (scale <= 1.001f) {
                                scale = 1f
                                offset = Offset.Zero
                                onZoomStateChange(ZoomState(scale, offset))
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )


            // (2) One-finger pan + fling (only when zoomed)
            .then(
                if (panEnabled) {
                    Modifier.pointerInput(model, panEnabled) {
                        val velocityTracker = VelocityTracker()

                        detectDragGestures(
                            onDragStart = {
                                stopFling()
                                velocityTracker.resetTracking()
                                onUserInteraction()
                            },
                            onDrag = { change, dragAmount ->
                                // IMPORTANT: consume only when we're handling pan (zoomed)
                                change.consumePositionChange()
                                onUserInteraction()

                                // Apply drag
                                offset = clampOffset(scale, offset + Offset(dragAmount.x, dragAmount.y))
                                onZoomStateChange(ZoomState(scale, offset))

                                // Track velocity
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    change.position
                                )
                            },
                            onDragEnd = {
                                onUserInteraction()

                                val v = velocityTracker.calculateVelocity()
                                val start = offset

                                // Start fling on both axes
                                stopFling()
                                flingJob = scope.launch {
                                    val animX = Animatable(start.x)
                                    val animY = Animatable(start.y)

                                    coroutineScope {
                                        launch {
                                            animX.animateDecay(v.x, decay) {
                                                val candidate = clampOffset(scale, Offset(value, animY.value))
                                                offset = candidate
                                                onZoomStateChange(ZoomState(scale, offset))
                                            }
                                        }
                                        launch {
                                            animY.animateDecay(v.y, decay) {
                                                val candidate = clampOffset(scale, Offset(animX.value, value))
                                                offset = candidate
                                                onZoomStateChange(ZoomState(scale, offset))
                                            }
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                stopFling()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )

            // (3) Taps
            .then(
                if (!twoFingersDown) {
                    Modifier.pointerInput(model, containerSize) {
                        detectTapGestures(
                            onTap = {
                                onUserInteraction()
                                onSingleTap()
                            },
                            onDoubleTap = { tap ->
                                onUserInteraction()
                                stopFling()

                                val currScale = scale
                                val currOffset = offset

                                val targetScale =
                                    if (currScale <= 1.05f) doubleTapScale.coerceAtMost(maxScale) else 1f

                                if (containerSize.width == 0 || containerSize.height == 0) {
                                    scope.launch { animateTo(targetScale, Offset.Zero) }
                                    return@detectTapGestures
                                }

                                if (targetScale == 1f) {
                                    scope.launch { animateTo(1f, Offset.Zero) }
                                } else {
                                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                    val tapFromCenter = tap - center
                                    val scaleRatio = targetScale / currScale

                                    val newOffset = currOffset - tapFromCenter * (scaleRatio - 1f)
                                    val clamped = clampOffset(targetScale, newOffset)

                                    scope.launch { animateTo(targetScale, clamped) }
                                }
                            }
                        )
                    }
                } else {
                    Modifier   // <- esto significa: "no agregues nada"
                }
            )

            // (4) Apply transforms
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }

    )
}



/* ============================================================
   MediaStore Query
   ============================================================ */

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
