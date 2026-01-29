package fr.vpm.yag.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.vpm.yag.device.DeviceAlbumsRetriever
import fr.vpm.yag.device.model.Album
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    private val deviceAlbumsRetriever = DeviceAlbumsRetriever()

    private val albums = MutableLiveData<Collection<Album>>()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadAlbums(context: Context) {
        val cachedAlbums = albums.value
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
                albums.value = localAlbums
                _text.value =
                    "This device has ${localAlbums.size} albums with ${localAlbums.sumOf { it.photos.size }} photos"
            } catch (e: Exception) {
                _text.value = "Error scanning device: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}