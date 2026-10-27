package fr.vpm.yag.ui.album

import android.util.Log
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

    // Photos deleted in this session. They stay filtered out of the collection even
    // when the album is re-scanned from the Media Store, because the trash request
    // may not be confirmed yet when the re-scan runs.
    private val deletedPhotoIds = mutableSetOf<Long>()

    // Display photos list (sorted and filtered)
    private val _displayPhotos = MutableLiveData<List<Photo>>()
    val displayPhotos: LiveData<List<Photo>> = _displayPhotos

    // Filter mode
    enum class FilterMode {
        NONE,                   // No filtering - show all photos
        NO_RAW,                // Filter out RAW photos
        FAVOURITES_ONLY,       // Show only favourite photos
        FAVOURITES_WITH_RAW,   // Show favourite photos + RAW versions that match
        NON_FAVOURITES_ONLY    // Show only non-favourite photos
    }

    private val _filterMode = MutableLiveData<FilterMode>()
    val filterMode: LiveData<FilterMode> = _filterMode

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
        _filterMode.value = FilterMode.NONE
        _sortOrder.value = DEFAULT_SORT_ORDER
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
            _allPhotos.value = album.photos.filterNot { photo ->
                (photo as? DevicePhoto)?.id in deletedPhotoIds
            }
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
     * Set the filter mode
     */
    fun setFilterMode(mode: FilterMode) {
        _filterMode.value = mode
        updateDisplayPhotos()
    }

    /**
     * Toggle the filter mode - cycles through available modes
     */
    fun toggleFilter() {
        val currentMode = _filterMode.value ?: FilterMode.NONE
        val nextMode = when (currentMode) {
            FilterMode.NONE -> FilterMode.NO_RAW
            FilterMode.NO_RAW -> FilterMode.FAVOURITES_ONLY
            FilterMode.FAVOURITES_ONLY -> FilterMode.FAVOURITES_WITH_RAW
            FilterMode.FAVOURITES_WITH_RAW -> FilterMode.NON_FAVOURITES_ONLY
            FilterMode.NON_FAVOURITES_ONLY -> FilterMode.NONE
        }
        setFilterMode(nextMode)
    }

    /**
     * Check if any filtering is enabled (legacy method for compatibility)
     */
    fun isFilterEnabled(): Boolean {
        return _filterMode.value != FilterMode.NONE
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
        val filterMode = _filterMode.value ?: FilterMode.NONE
        val sortOrder = _sortOrder.value ?: DEFAULT_SORT_ORDER

        android.util.Log.d(
            "AlbumDetailVM",
            "Sort order: $sortOrder, Filter: $filterMode, Photo count: ${photos.size}"
        )

        // Log original display names
        val originalNames = photos.mapNotNull { (it as? DevicePhoto)?.displayName }
        android.util.Log.d("AlbumDetailVM", "Original displayNames: $originalNames")

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
                    (photo as? DevicePhoto)?.displayName ?: ""
                }
            }

            SortOrder.NAME_DESCENDING -> {
                photos.sortedByDescending { photo ->
                    (photo as? DevicePhoto)?.displayName ?: ""
                }
            }
        }

        // Log sorted display names
        val sortedNames = sortedPhotos.mapNotNull { (it as? DevicePhoto)?.displayName }
        android.util.Log.d("AlbumDetailVM", "Sorted displayNames: $sortedNames")

        // Apply filtering based on mode
        val filteredPhotos = when (filterMode) {
            FilterMode.NONE -> sortedPhotos
            FilterMode.NO_RAW -> sortedPhotos.filterNot { photo ->
                photo.isRawPhoto()
            }

            FilterMode.FAVOURITES_ONLY -> sortedPhotos.filter { photo ->
                (photo as? DevicePhoto)?.isFavorite == true
            }

            FilterMode.FAVOURITES_WITH_RAW -> {
                // Get all favourite photos first
                val favoritePhotos = sortedPhotos.filter { photo ->
                    (photo as? DevicePhoto)?.isFavorite == true
                }

                // Collect base filenames of favorites (without extension)
                val favoriteBaseNames = favoritePhotos.mapNotNull { photo ->
                    val devicePhoto = photo as? DevicePhoto
                    val path = devicePhoto?.path
                    path?.let { extractBaseFilename(it) }
                }.toSet()

                // Include all photos that match the base filenames of favorites
                sortedPhotos.filter { photo ->
                    val devicePhoto = photo as? DevicePhoto
                    val path = devicePhoto?.path
                    path?.let { filename ->
                        favoriteBaseNames.contains(extractBaseFilename(filename))
                    } ?: false
                }
            }

            FilterMode.NON_FAVOURITES_ONLY -> sortedPhotos.filter { photo ->
                (photo as? DevicePhoto)?.isFavorite != true
            }
        }

        Log.d("ux-album", "Displayed photos are $filteredPhotos")
        _displayPhotos.value = filteredPhotos
    }

    /**
     * Extract base filename without extension for matching RAW and JPEG versions
     */
    private fun extractBaseFilename(path: String): String {
        val lastSlashIndex = path.lastIndexOf('/')
        val filename = if (lastSlashIndex >= 0) path.substring(lastSlashIndex + 1) else path
        val lastDotIndex = filename.lastIndexOf('.')
        return if (lastDotIndex > 0) filename.substring(0, lastDotIndex) else filename
    }

    /**
     * Get the display photos list (convenience method)
     */
    fun getDisplayPhotos(): List<Photo> {
        return _displayPhotos.value ?: emptyList()
    }

    /**
     * Get current filter mode (convenience method)
     */
    fun getFilterMode(): FilterMode {
        return _filterMode.value ?: FilterMode.NONE
    }

    /**
     * Get current sort order (convenience method)
     */
    fun getCurrentSortOrder(): SortOrder {
        return _sortOrder.value ?: DEFAULT_SORT_ORDER
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
     * Replace the whole selection
     */
    fun setSelectedPhotos(photoIds: Set<Long>) {
        _selectedPhotoIds.value = photoIds
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

    /**
     * Called when photos have been deleted (moved to trash) from the album.
     * Filters them out of the collection and refreshes the display
     */
    fun removePhotos(photoIds: Collection<Long>) {
        if (photoIds.isEmpty()) return
        deletedPhotoIds += photoIds
        val currentAllPhotos = _allPhotos.value ?: emptyList()
        _allPhotos.value = currentAllPhotos.filterNot { photo ->
            (photo as? DevicePhoto)?.id in deletedPhotoIds
        }
        // Deleted photos can't stay selected
        _selectedPhotoIds.value = (_selectedPhotoIds.value ?: emptySet()) - deletedPhotoIds
        updateDisplayPhotos()
    }

    /**
     * Called when a photo's favorite status has been changed in MediaStore
     * Updates the local photo object and refreshes the display
     */
    fun onPhotoFavoriteChanged(updatedPhoto: DevicePhoto) {
        // Update the photo in the allPhotos list
        val currentAllPhotos = _allPhotos.value ?: emptyList()
        val updatedPhotos = currentAllPhotos.map { photo ->
            if (photo is DevicePhoto && photo.id == updatedPhoto.id) {
                // Return the updated photo with the new favorite status
                updatedPhoto
            } else {
                photo
            }
        }
        _allPhotos.value = updatedPhotos
        updateDisplayPhotos()
    }

    companion object {
        private val DEFAULT_SORT_ORDER = SortOrder.NAME_DESCENDING
    }
}