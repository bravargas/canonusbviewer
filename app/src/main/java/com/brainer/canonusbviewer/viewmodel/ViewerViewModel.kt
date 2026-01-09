package com.brainer.canonusbviewer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brainer.canonusbviewer.data.mediastore.MediaStoreDataSource
import com.brainer.canonusbviewer.data.settings.SettingsStore
import com.brainer.canonusbviewer.model.MediaPhoto
import com.brainer.canonusbviewer.model.ZoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _zoomStates = MutableStateFlow<List<ZoomState>>(emptyList())
    val zoomStates = _zoomStates.asStateFlow()

    private val _overlayVisible = MutableStateFlow(true)
    val overlayVisible = _overlayVisible.asStateFlow()

    private val _status = MutableStateFlow("Waiting for Canon imports...")
    val status = _status.asStateFlow()

    // A trigger for manual refreshes. Using a changing value like a timestamp ensures it always fires.
    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    val folderContains: StateFlow<String> = settingsStore.cameraRelativePathContains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Canon EOS R50")

    // This is the definitive, reactive stream for the photo list.
    // It automatically re-executes when either the folder OR the refresh trigger changes.
    val photos: StateFlow<List<MediaPhoto>> = combine(
        folderContains,
        refreshTrigger
    ) { folder, _ ->
        folder
    }.flatMapLatest { filter ->
        mediaStoreDataSource.getPhotoFlow(filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // This block now only handles side-effects based on the photos flow.
        photos.onEach { photoList ->
            _zoomStates.value = List(photoList.size) { ZoomState() }
            _status.value = if (photoList.isNotEmpty()) {
                "Latest: ${photoList.first().displayName}"
            } else {
                val currentFilter = folderContains.value
                if (currentFilter.isEmpty()) {
                    "No photos found. Grant permission or check settings."
                } else {
                    "No photos found in '${currentFilter}'."
                }
            }
        }.launchIn(viewModelScope)
    }

    // This function is needed for the UI to trigger a manual refresh.
    fun refresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    fun setCameraRelativePathContains(path: String) {
        viewModelScope.launch {
            settingsStore.setCameraRelativePathContains(path)
        }
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
}
