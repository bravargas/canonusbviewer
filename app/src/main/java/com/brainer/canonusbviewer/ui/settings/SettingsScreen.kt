package com.brainer.canonusbviewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
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
