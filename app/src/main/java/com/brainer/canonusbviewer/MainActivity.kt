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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CanonImportViewer() } }
    }
}

private enum class Screen { VIEWER, GALLERY }

private data class LocalPhoto(
    val file: File,
    val selected: Boolean = false
)

private data class NewestImage(
    val id: Long,
    val uri: Uri,
    val name: String,
    val relativePath: String?
)

@Composable
private fun CanonImportViewer() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Open Canon Camera Connect and take a photo.") }

    var localPhotos by remember { mutableStateOf(loadLocalPhotos(context)) }
    var fullScreen by remember { mutableStateOf(localPhotos.firstOrNull()?.file) }

    var screen by remember { mutableStateOf(Screen.VIEWER) }

    // mark app start so we ignore old gallery items
    val appStartSeconds = remember { System.currentTimeMillis() / 1000 }

    // Track already imported MediaStore IDs so we don’t re-import the same file
    val importedIds = remember { mutableStateMapOf<Long, Boolean>() }

    // Permission (Android 13+ vs older)
    val perm = remember {
        if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES"
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val requestPerm = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) {
            "Permission granted. Waiting for Canon photos..."
        } else {
            "Permission denied. Cannot read imported photos."
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            requestPerm.launch(perm)
        } else {
            status = "Permission granted. Waiting for Canon photos..."
        }
    }

    // Observe MediaStore changes and auto-import newest Canon photo
    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val newest = queryNewestCanonImage(context, appStartSeconds)
                if (newest != null && importedIds[newest.id] != true) {
                    try {
                        val saved = copyMediaStoreUriToLocal(context, newest.uri, newest.name)
                        importedIds[newest.id] = true

                        localPhotos = loadLocalPhotos(context)
                        fullScreen = saved
                        status = "Imported: ${saved.name}"
                        screen = Screen.VIEWER
                    } catch (ex: Exception) {
                        status = "Import error: ${ex.message}"
                    }
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

    // UI: two screens
    when (screen) {
        Screen.VIEWER -> ViewerScreen(
            status = status,
            file = fullScreen,
            onOpenGallery = { screen = Screen.GALLERY }
        )

        Screen.GALLERY -> GalleryScreen(
            status = status,
            localPhotos = localPhotos,
            onRefresh = {
                localPhotos = loadLocalPhotos(context)
                fullScreen = localPhotos.firstOrNull()?.file
                status = "Refreshed local gallery."
            },
            onBackToViewer = { screen = Screen.VIEWER },
            onSelectFull = { f ->
                fullScreen = f
                screen = Screen.VIEWER
            },
            onToggleSelected = { file, checked ->
                localPhotos = localPhotos.map {
                    if (it.file == file) it.copy(selected = checked) else it
                }
            },
            onDeleteSelected = {
                val toDelete = localPhotos.filter { it.selected }
                scope.launch(Dispatchers.IO) {
                    toDelete.forEach { runCatching { it.file.delete() } }
                }
                localPhotos = loadLocalPhotos(context)
                fullScreen = localPhotos.firstOrNull()?.file
                status = "Deleted ${toDelete.size} photo(s)."
            }
        )
    }
}

@Composable
private fun ViewerScreen(
    status: String,
    file: File?,
    onOpenGallery: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Minimal top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onOpenGallery) { Text("Gallery") }
        }

        // Full screen image, no crop
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            if (file == null) {
                Text("No photo yet.")
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = "Full screen photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit // IMPORTANT: no crop
                )
            }
        }
    }
}

@Composable
private fun GalleryScreen(
    status: String,
    localPhotos: List<LocalPhoto>,
    onRefresh: () -> Unit,
    onBackToViewer: () -> Unit,
    onSelectFull: (File) -> Unit,
    onToggleSelected: (File, Boolean) -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onBackToViewer) { Text("Back") }
            Button(onClick = onRefresh) { Text("Refresh") }
            Button(
                onClick = onDeleteSelected,
                enabled = localPhotos.any { it.selected }
            ) { Text("Delete selected") }
        }

        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(localPhotos, key = { it.file.absolutePath }) { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = item.file,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.size(72.dp).clickable { onSelectFull(item.file) },
                        contentScale = ContentScale.Crop
                    )

                    Column(Modifier.weight(1f)) {
                        Text(item.file.name, style = MaterialTheme.typography.bodySmall)
                        Text("${item.file.length()} bytes", style = MaterialTheme.typography.bodySmall)
                    }

                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = { checked -> onToggleSelected(item.file, checked) }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * Finds the newest image imported by Canon Camera Connect.
 * Filters by RELATIVE_PATH containing "Canon EOS R50"
 * and DATE_ADDED >= app start to ignore older items.
 */
private fun queryNewestCanonImage(context: Context, minDateAddedSeconds: Long): NewestImage? {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.RELATIVE_PATH
    )

    val selection = """
        ${MediaStore.Images.Media.DATE_ADDED} >= ?
        AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?
    """.trimIndent()

    val selectionArgs = arrayOf(
        minDateAddedSeconds.toString(),
        "%Canon EOS R50%"
    )

    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return null

        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
        val rel = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

        return NewestImage(id = id, uri = uri, name = name, relativePath = rel)
    }

    return null
}

private fun copyMediaStoreUriToLocal(context: Context, uri: Uri, displayName: String): File {
    val dir = File(context.filesDir, "photos")
    if (!dir.exists()) dir.mkdirs()

    val safeName = displayName
        .replace("\\", "_")
        .replace("/", "_")
        .ifBlank { "photo_${System.currentTimeMillis()}.jpg" }

    val outFile = File(dir, "${System.currentTimeMillis()}_$safeName")

    context.contentResolver.openInputStream(uri)?.use { input ->
        outFile.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw IllegalStateException("Cannot open input stream for $uri")

    return outFile
}

private fun loadLocalPhotos(context: Context): List<LocalPhoto> {
    val dir = File(context.filesDir, "photos")
    if (!dir.exists()) return emptyList()

    val files = dir.listFiles() ?: return emptyList()
    return files
        .filter { it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".jpeg", true)) }
        .sortedByDescending { it.lastModified() }
        .map { LocalPhoto(it, selected = false) }
}
