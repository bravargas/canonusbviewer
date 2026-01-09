package com.brainer.canonusbviewer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brainer.canonusbviewer.data.mediastore.MediaStoreDataSource
import com.brainer.canonusbviewer.data.settings.SettingsStore
import com.brainer.canonusbviewer.model.MediaPhoto
import com.brainer.canonusbviewer.model.ZoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _photos = MutableStateFlow<List<MediaPhoto>>(emptyList())
    val photos = _photos.asStateFlow()

    private val _zoomStates = MutableStateFlow<List<ZoomState>>(emptyList())
    val zoomStates = _zoomStates.asStateFlow()

    private val _overlayVisible = MutableStateFlow(true)
    val overlayVisible = _overlayVisible.asStateFlow()

    private val _status = MutableStateFlow("Waiting for Canon imports...")
    val status = _status.asStateFlow()

    init {
        settingsStore.cameraRelativePathContains
            .combine(mediaStoreDataSource.getPhotoFlow("")) { filter, photos ->
                _photos.value = photos
                _zoomStates.value = List(photos.size) { ZoomState() }
                _status.value = if (photos.isNotEmpty()) "Latest: ${photos.first().uri.lastPathSegment}" else "No photos found in '$filter'."
            }
            .launchIn(viewModelScope)
    }

    fun onZoomStateChange(index: Int, zoomState: ZoomState) {
        val newStates = _zoomStates.value.toMutableList()
        if (index in newStates.indices) {
            newStates[index] = zoomState
            _zoomStates.value = newStates
        }
    }

    fun toggleOverlay() {
        _overlayVisible.value = !_overlayVisible.value
    }

    fun showOverlay() {
        _overlayVisible.value = true
    }

    fun hideOverlay() {
        _overlayVisible.value = false
    }
}
