package fr.vpm.yag.device.model

class Album(
    var id: String? = null,
    var name: String? = null,
    var details: String? = null,
    var photos: MutableList<Photo> = mutableListOf(),
    var source: String? = null
) {
    fun addPhoto(devicePhoto: DevicePhoto) {
        photos.add(devicePhoto)
    }

    companion object {
        internal const val SOURCE_DEVICE = "source_device"
        internal const val SOURCE_FLICKR = "source_flickr"
    }
}