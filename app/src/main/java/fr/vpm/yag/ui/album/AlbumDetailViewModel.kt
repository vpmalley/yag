package fr.vpm.yag.ui.album

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto
import fr.vpm.yag.device.model.Photo
import fr.vpm.yag.device.model.isRawPhoto

class AlbumDetailViewModel : ViewModel() {

    // Current album being viewed
    private val _currentAlbum = MutableLiveData<Album?>()
    val currentAlbum: LiveData<Album?> = _currentAlbum

    // All photos from the current album (unsorted, unfiltered)
    private val _allPhotos = MutableLiveData<List<Photo>>()
    val allPhotos: LiveData<List<Photo>> = _allPhotos

    // Display photos list (sorted and filtered)
    private val _displayPhotos = MutableLiveData<List<Photo>>()
    val displayPhotos: LiveData<List<Photo>> = _displayPhotos

    // Filter state
    private val _isFilterEnabled = MutableLiveData<Boolean>()
    val isFilterEnabled: LiveData<Boolean> = _isFilterEnabled

    // Sort options
    enum class SortOrder {
        DATE_NEWEST_FIRST,
        DATE_OLDEST_FIRST,
        NAME_ASCENDING,
        NAME_DESCENDING
    }

    private val _sortOrder = MutableLiveData<SortOrder>()
    val sortOrder: LiveData<SortOrder> = _sortOrder

    // Current position for photo viewer
    private val _currentPhotoPosition = MutableLiveData<Int>()
    val currentPhotoPosition: LiveData<Int> = _currentPhotoPosition

    // Selection state - track selected photo IDs
    private val _selectedPhotoIds = MutableLiveData<Set<Long>>()
    val selectedPhotoIds: LiveData<Set<Long>> = _selectedPhotoIds

    // Selection mode
    private val _isSelectionMode = MutableLiveData<Boolean>()
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    init {
        _isFilterEnabled.value = false
        _sortOrder.value = SortOrder.DATE_NEWEST_FIRST
        _currentPhotoPosition.value = 0
        _selectedPhotoIds.value = emptySet()
        _isSelectionMode.value = false
    }

    /**
     * Set the current album and initialize photos
     */
    fun setAlbum(album: Album?) {
        _currentAlbum.value = album
        if (album != null) {
            _allPhotos.value = album.photos
            updateDisplayPhotos()
        } else {
            _allPhotos.value = emptyList()
            _displayPhotos.value = emptyList()
        }
    }

    /**
     * Get the album ID
     */
    fun getAlbumId(): String? {
        return _currentAlbum.value?.id
    }

    /**
     * Enable or disable the RAW photo filter
     */
    fun setFilterEnabled(enabled: Boolean) {
        _isFilterEnabled.value = enabled
        updateDisplayPhotos()
    }

    /**
     * Toggle the RAW photo filter
     */
    fun toggleFilter() {
        _isFilterEnabled.value = _isFilterEnabled.value != true
        updateDisplayPhotos()
    }

    /**
     * Set the sort order for photos
     */
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        updateDisplayPhotos()
    }

    /**
     * Set the current photo position (for photo viewer)
     */
    fun setCurrentPhotoPosition(position: Int) {
        _currentPhotoPosition.value = position
    }

    /**
     * Update the display photos list based on current filter and sort settings
     */
    private fun updateDisplayPhotos() {
        val photos = _allPhotos.value ?: emptyList()
        val filterEnabled = _isFilterEnabled.value ?: false
        val sortOrder = _sortOrder.value ?: SortOrder.DATE_NEWEST_FIRST

        // Apply sorting first
        val sortedPhotos = when (sortOrder) {
            SortOrder.DATE_NEWEST_FIRST -> {
                photos.sortedByDescending { photo ->
                    (photo as? DevicePhoto)?.additionDate?.time ?: 0L
                }
            }
            SortOrder.DATE_OLDEST_FIRST -> {
                photos.sortedBy { photo ->
                    (photo as? DevicePhoto)?.additionDate?.time ?: 0L
                }
            }
            SortOrder.NAME_ASCENDING -> {
                photos.sortedBy { photo ->
                    (photo as? DevicePhoto)?.path ?: ""
                }
            }
            SortOrder.NAME_DESCENDING -> {
                photos.sortedByDescending { photo ->
                    (photo as? DevicePhoto)?.path ?: ""
                }
            }
        }

        // Apply filtering
        val filteredPhotos = if (filterEnabled) {
            sortedPhotos.filterNot { photo ->
                photo.isRawPhoto()
            }
        } else {
            sortedPhotos
        }

        _displayPhotos.value = filteredPhotos
    }

    /**
     * Get the display photos list (convenience method)
     */
    fun getDisplayPhotos(): List<Photo> {
        return _displayPhotos.value ?: emptyList()
    }

    /**
     * Get filter state (convenience method)
     */
    fun isFilterEnabled(): Boolean {
        return _isFilterEnabled.value ?: false
    }

    /**
     * Get current sort order (convenience method)
     */
    fun getCurrentSortOrder(): SortOrder {
        return _sortOrder.value ?: SortOrder.DATE_NEWEST_FIRST
    }

    /**
     * Find the position of a specific photo in the display list
     */
    fun findPhotoPosition(photo: Photo): Int {
        val displayPhotos = getDisplayPhotos()
        return displayPhotos.indexOfFirst { 
            val photo1 = it as? DevicePhoto
            val photo2 = photo as? DevicePhoto
            photo1?.id == photo2?.id
        }
    }

    /**
     * Check if the album contains the given photo by URI
     */
    fun containsPhoto(photo: Photo): Boolean {
        val allPhotos = _allPhotos.value ?: emptyList()
        val targetPhoto = photo as? DevicePhoto
        return allPhotos.any { 
            val existingPhoto = it as? DevicePhoto
            existingPhoto?.uri == targetPhoto?.uri
        }
    }

    // Selection methods

    /**
     * Enable or disable selection mode
     */
    fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        // Clear selection when exiting selection mode
        if (!enabled) {
            clearSelection()
        }
    }

    /**
     * Toggle selection mode
     */
    fun toggleSelectionMode() {
        _isSelectionMode.value = _isSelectionMode.value != true
        // Clear selection when entering selection mode
        if (_isSelectionMode.value == true) {
            clearSelection()
        }
    }

    /**
     * Check if selection mode is active
     */
    fun isSelectionModeActive(): Boolean {
        return _isSelectionMode.value ?: false
    }

    /**
     * Select or deselect a photo by its ID
     */
    fun togglePhotoSelection(photoId: Long) {
        val currentSelection = _selectedPhotoIds.value ?: emptySet()
        val newSelection = if (currentSelection.contains(photoId)) {
            currentSelection - photoId
        } else {
            currentSelection + photoId
        }
        _selectedPhotoIds.value = newSelection
    }

    /**
     * Select a photo by its ID
     */
    fun selectPhoto(photoId: Long) {
        val currentSelection = _selectedPhotoIds.value ?: emptySet()
        _selectedPhotoIds.value = currentSelection + photoId
    }

    /**
     * Deselect a photo by its ID
     */
    fun deselectPhoto(photoId: Long) {
        val currentSelection = _selectedPhotoIds.value ?: emptySet()
        _selectedPhotoIds.value = currentSelection - photoId
    }

    /**
     * Check if a photo is selected
     */
    fun isPhotoSelected(photoId: Long): Boolean {
        return _selectedPhotoIds.value?.contains(photoId) ?: false
    }

    /**
     * Clear all selections
     */
    fun clearSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    /**
     * Select all photos
     */
    fun selectAllPhotos() {
        val allPhotos = _displayPhotos.value ?: emptyList()
        val allIds = allPhotos.mapNotNull { (it as? DevicePhoto)?.id }
        _selectedPhotoIds.value = allIds.toSet()
    }

    /**
     * Get count of selected photos
     */
    fun getSelectedCount(): Int {
        return _selectedPhotoIds.value?.size ?: 0
    }

    /**
     * Get list of selected photos
     */
    fun getSelectedPhotos(): List<Photo> {
        val selectedIds = _selectedPhotoIds.value ?: emptySet()
        val allPhotos = _displayPhotos.value ?: emptyList()
        return allPhotos.filter { photo ->
            selectedIds.contains((photo as? DevicePhoto)?.id)
        }
    }
}