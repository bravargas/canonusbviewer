package com.brainer.canonusbviewer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brainer.canonusbviewer.data.repository.PhotoRepository
import com.brainer.canonusbviewer.data.settings.SettingsStore
import com.brainer.canonusbviewer.model.Photo
import com.brainer.canonusbviewer.model.ZoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val photoRepository: PhotoRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos = _photos.asStateFlow()

    private val _zoomStates = MutableStateFlow<List<ZoomState>>(emptyList())
    val zoomStates = _zoomStates.asStateFlow()

    private val _overlayVisible = MutableStateFlow(true)
    val overlayVisible = _overlayVisible.asStateFlow()

    private val _status = MutableStateFlow("Waiting for Canon imports...")
    val status = _status.asStateFlow()

    private val _minRatingFilter = MutableStateFlow(0)
    val minRatingFilter = _minRatingFilter.asStateFlow()

    init {
        settingsStore.cameraRelativePathContains
            .combine(minRatingFilter) { folderFilter, ratingFilter ->
                Pair(folderFilter, ratingFilter)
            }
            .flatMapLatest { (folderFilter, ratingFilter) ->
                photoRepository.getPhotos(folderFilter).map { allPhotos ->
                    val filteredPhotos = if (ratingFilter > 0) {
                        allPhotos.filter { it.rating >= ratingFilter }
                    } else {
                        allPhotos
                    }
                    Pair(allPhotos, filteredPhotos)
                }
            }
            .onEach { (allPhotos, filteredPhotos) ->
                _photos.value = filteredPhotos
                _zoomStates.value = List(filteredPhotos.size) { ZoomState() }
                _status.value = if (filteredPhotos.isNotEmpty()) {
                    "Displaying ${filteredPhotos.size} of ${allPhotos.size} photos"
                } else if (allPhotos.isNotEmpty()) {
                    "No photos match the current rating filter."
                } else {
                    "No photos found in the specified folder."
                }
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

    fun setRating(mediaStoreId: Long, rating: Int) {
        viewModelScope.launch {
            photoRepository.setRating(mediaStoreId, rating)
        }
    }

    fun setMinRatingFilter(rating: Int) {
        _minRatingFilter.value = rating
    }
}
