package com.brainer.canonusbviewer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val cameraRelativePathKey = stringPreferencesKey("camera_relative_path_contains")

    val cameraRelativePathContains: Flow<String>
        get() = context.dataStore.data.map { preferences ->
            // Default to the user's preferred folder name.
            preferences[cameraRelativePathKey] ?: "Canon EOS R50"
        }

    suspend fun setCameraRelativePathContains(value: String) {
        context.dataStore.edit {
            it[cameraRelativePathKey] = value
        }
    }
}
