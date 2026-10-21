package fr.vpm.yag.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.vpm.yag.device.DeviceAlbumsRetriever
import fr.vpm.yag.device.model.Album
import kotlinx.coroutines.launch

class DeviceAlbumViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    private val deviceAlbumsRetriever = DeviceAlbumsRetriever()

    private val _albums = MutableLiveData<Collection<Album>>()
    val albums: LiveData<Collection<Album>> = _albums

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadAlbums(context: Context) {
        val cachedAlbums = _albums.value
        if (cachedAlbums != null) {
            _text.value =
                "This device has ${cachedAlbums.size} albums with ${cachedAlbums.sumOf { it.photos.size }} photos"
        } else {
            fetchDeviceAlbums(context)
        }
    }

    fun fetchDeviceAlbums(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _text.value = "Scanning device for photos..."
            try {
                val localAlbums = deviceAlbumsRetriever.getLocalAlbums(context)
                _albums.value = localAlbums
                _text.value =
                    "This device has ${localAlbums.size} albums with ${localAlbums.sumOf { it.photos.size }} photos"
            } catch (e: Exception) {
                _text.value = "Error scanning device: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update a photo's favorite status in the cached albums
     * This should be called when a photo's favorite status changes in MediaStore
     */
    fun updatePhotoFavoriteStatus(photoId: Long, isFavorite: Boolean) {
        val currentAlbums = _albums.value ?: return
        val updatedAlbums = currentAlbums.map { album ->
            val updatedPhotos = album.photos.map { photo ->
                if (photo is fr.vpm.yag.device.model.DevicePhoto && photo.id == photoId) {
                    // Create a new DevicePhoto with updated favorite status
                    fr.vpm.yag.device.model.DevicePhoto(
                        id = photo.id,
                        uri = photo.uri,
                        path = photo.path,
                        displayName = photo.displayName,
                        additionDate = photo.additionDate,
                        isFavorite = isFavorite
                    )
                } else {
                    photo
                }
            }.toMutableList()
            // Create a new Album with updated photos
            fr.vpm.yag.device.model.Album(
                id = album.id,
                name = album.name,
                details = album.details,
                photos = updatedPhotos,
                source = album.source
            )
        }.toList()
        _albums.value = updatedAlbums
    }

    /**
     * Refresh albums from MediaStore
     * Call this when data might have changed (e.g., after modifying photos)
     */
    fun refreshAlbums(context: Context) {
        fetchDeviceAlbums(context)
    }
}