package fr.vpm.yag.ui.home

import android.app.Activity
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

    private val _albums = MutableLiveData<Collection<Album>>()
    val albumsToDisplay: LiveData<Collection<Album>> = _albums

    private val deviceAlbumsRetriever = DeviceAlbumsRetriever()

    private val albums = MutableLiveData<Collection<Album>>()

    fun loadAlbums(activity: Activity) {
        viewModelScope.launch {
            val cachedAlbums = albums.value
            if (cachedAlbums != null) {
                displayAlbumsCount(cachedAlbums)
            } else {
                fetchDeviceAlbums(activity)
            }
        }
    }

    fun fetchDeviceAlbums(activity: Activity) {
        viewModelScope.launch {
            _text.value = "Scanning device for photos..."
            val localAlbums = deviceAlbumsRetriever.getLocalAlbums(activity)
            albums.postValue(localAlbums)
            displayAlbumsCount(localAlbums)
            displayAlbums(localAlbums)
        }
    }

    private fun displayAlbums(localAlbums: Collection<Album>) {
        _albums.value = localAlbums
    }

    private fun displayAlbumsCount(mediaAlbums: Collection<Album>) {
        _text.value =
            "This device has ${mediaAlbums.size} albums with ${mediaAlbums.sumOf { it.photos.size }} photos"
    }
}