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

    private val deviceAlbumsRetriever = DeviceAlbumsRetriever()

    private val albums = MutableLiveData<Collection<Album>>()

    fun loadAlbums(activity: Activity) {
        viewModelScope.launch {
            val cachedAlbums = albums.value
            if (cachedAlbums != null) {
                _text.value =
                    "This device has ${cachedAlbums.size} albums with ${cachedAlbums.sumOf { it.photos.size }} photos"
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
            _text.value =
                "This device has ${localAlbums.size} albums with ${localAlbums.sumOf { it.photos.size }} photos"
        }
    }
}