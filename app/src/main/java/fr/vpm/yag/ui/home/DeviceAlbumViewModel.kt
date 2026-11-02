package fr.vpm.yag.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.vpm.yag.cache.AlbumCacheRepository
import fr.vpm.yag.device.DeviceAlbumsRetriever
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceAlbumViewModel(
    private val cacheRepository: AlbumCacheRepository
) : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    private val deviceAlbumsRetriever = DeviceAlbumsRetriever()

    private val _albums = MutableLiveData<Collection<Album>>()
    val albums: LiveData<Collection<Album>> = _albums

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private var periodicRefreshJob: Job? = null

    companion object {
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    /**
     * Cache-first loading: load from cache immediately, then trigger background refresh.
     */
    fun loadAlbums(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cachedAlbums = cacheRepository.loadCachedAlbums()
                if (cachedAlbums != null && cachedAlbums.isNotEmpty()) {
                    _albums.value = cachedAlbums
                    _text.value = "This device has ${cachedAlbums.size} albums with ${cachedAlbums.sumOf { it.photos.size }} photos"
                    _isLoading.value = false
                    refreshAlbumsFromMediaStore(context)
                } else {
                    fetchDeviceAlbums(context)
                }
            } catch (e: Exception) {
                _text.value = "Error loading cached albums: ${e.message}"
                fetchDeviceAlbums(context)
            }
        }
    }

    fun fetchDeviceAlbums(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _text.value = "Scanning device for photos..."
            try {
                val localAlbums = deviceAlbumsRetriever.getLocalAlbums(context)
                _albums.value = localAlbums
                cacheRepository.saveAlbumsToCache(localAlbums)
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
     * Background refresh: scans MediaStore and updates cache without blocking UI.
     * Runs on IO dispatcher via the retriever and repository.
     */
    fun refreshAlbumsFromMediaStore(context: Context) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val localAlbums = withContext(Dispatchers.IO) {
                    deviceAlbumsRetriever.getLocalAlbums(context)
                }
                cacheRepository.saveAlbumsToCache(localAlbums)
                _albums.value = localAlbums
                _text.value =
                    "This device has ${localAlbums.size} albums with ${localAlbums.sumOf { it.photos.size }} photos"
            } catch (e: Exception) {
                // Silent failure for background refresh - keep showing cached data
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Start periodic refresh every 15 minutes while app is active.
     */
    fun startPeriodicRefresh(context: Context) {
        stopPeriodicRefresh()
        periodicRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                if (isActive) {
                    refreshAlbumsFromMediaStore(context)
                }
            }
        }
    }

    /**
     * Stop periodic refresh (call when app goes to background).
     */
    fun stopPeriodicRefresh() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
    }

    /**
     * Refresh on app resume if last refresh was more than 15 minutes ago.
     */
    fun refreshOnResume(context: Context) {
        viewModelScope.launch {
            val lastRefresh = cacheRepository.getLastRefreshTimestamp()
            val now = System.currentTimeMillis()
            if (lastRefresh == null || now - lastRefresh > REFRESH_INTERVAL_MS) {
                refreshAlbumsFromMediaStore(context)
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
                if (photo is DevicePhoto && photo.id == photoId) {
                    DevicePhoto(
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
            Album(
                id = album.id,
                name = album.name,
                details = album.details,
                photos = updatedPhotos,
                source = album.source
            )
        }.toList()
        _albums.value = updatedAlbums
        viewModelScope.launch {
            cacheRepository.saveAlbumsToCache(updatedAlbums)
        }
    }

    /**
     * Refresh albums from MediaStore
     * Call this when data might have changed (e.g., after modifying photos)
     */
    fun refreshAlbums(context: Context) {
        refreshAlbumsFromMediaStore(context)
    }

    override fun onCleared() {
        super.onCleared()
        stopPeriodicRefresh()
    }

    class Factory(
        private val cacheRepository: AlbumCacheRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeviceAlbumViewModel::class.java)) {
                return DeviceAlbumViewModel(cacheRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
