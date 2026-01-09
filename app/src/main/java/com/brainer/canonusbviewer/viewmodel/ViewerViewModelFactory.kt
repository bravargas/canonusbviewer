package com.brainer.canonusbviewer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.brainer.canonusbviewer.data.mediastore.MediaStoreDataSource
import com.brainer.canonusbviewer.data.settings.SettingsStore

class ViewerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ViewerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ViewerViewModel(
                mediaStoreDataSource = MediaStoreDataSource(context),
                settingsStore = SettingsStore(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
