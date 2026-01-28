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

    fun loadAlbums(context: Context) {
        viewModelScope.launch {
            val cachedAlbums = albums.value
            if (cachedAlbums != null) {
                _text.value =
                    "This device has ${cachedAlbums.size} albums with ${cachedAlbums.sumOf { it.media.size }} items"
            } else {
                fetchDeviceAlbums(context)
            }
        }
    }

    fun fetchDeviceAlbums(context: Context) {
        viewModelScope.launch {
            _text.value = "Scanning device for photos..."
            val localAlbums = deviceAlbumsRetriever.getLocalAlbums(context)
            albums.postValue(localAlbums)
            _text.value =
                "This device has ${localAlbums.size} albums with ${localAlbums.sumOf { it.media.size }} items"
        }
    }
}