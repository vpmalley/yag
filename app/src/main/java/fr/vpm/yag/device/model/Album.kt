package fr.vpm.yag.device.model

class Album(
    var id: String? = null,
    var name: String? = null,
    var details: String? = null,
    var media: MutableList<Photo> = mutableListOf(),
    var source: String? = null
) {
    fun addMedia(deviceMedia: DeviceMedia) {
        media.add(deviceMedia)
    }

    companion object {
        internal const val SOURCE_DEVICE = "source_device"
        internal const val SOURCE_FLICKR = "source_flickr"
    }
}