package com.brainer.canonusbviewer

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "canon_usb_viewer_settings")

object SettingsKeys {
    val CAMERA_RELATIVE_PATH_CONTAINS = stringPreferencesKey("camera_relative_path_contains")
}

class SettingsStore(private val context: Context) {

    val cameraRelativePathContains: Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[SettingsKeys.CAMERA_RELATIVE_PATH_CONTAINS] ?: "Canon EOS R50"
        }

    suspend fun setCameraRelativePathContains(value: String) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.CAMERA_RELATIVE_PATH_CONTAINS] = value
        }
    }
}
