package fr.vpm.yag.ui.home

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.vpm.yag.device.DeviceAlbumsRetriever
import fr.vpm.yag.device.model.Album

class HomeViewModel : ViewModel(), DeviceAlbumsRetriever.OnAlbumsRetrievedListener {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    val text: LiveData<String> = _text

    private val deviceAlbumsRetriever = DeviceAlbumsRetriever()

    fun fetchDeviceAlbums(activity: Activity) {
        _text.value = "Scanning device for photos..."
        deviceAlbumsRetriever.getLocalAlbums(activity, this)
    }

    override fun onAlbumsRetrieved(albums: Collection<Album>) {
        _text.value =
            "This device has ${albums.size} albums with ${albums.sumOf { it.photos.size }} photos"
    }
}