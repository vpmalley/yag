package fr.vpm.yag.cache

import android.net.Uri
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto
import java.util.Date

fun Album.toEntity(): AlbumEntity {
    return AlbumEntity(
        id = id ?: "",
        name = name,
        photoCount = photos.size,
        lastUpdated = System.currentTimeMillis()
    )
}

fun PhotoEntity.toDevicePhoto(): DevicePhoto {
    return DevicePhoto(
        id = id,
        uri = uri?.let { Uri.parse(it) },
        path = path,
        displayName = displayName,
        additionDate = Date(dateAdded),
        isFavorite = isFavorite
    )
}

fun DevicePhoto.toEntity(albumId: String): PhotoEntity {
    return PhotoEntity(
        id = id,
        albumId = albumId,
        uri = uri?.toString(),
        path = path,
        displayName = displayName,
        isFavorite = isFavorite,
        dateAdded = additionDate?.time ?: 0L
    )
}

fun AlbumWithPhotos.toAlbum(): Album {
    return Album(
        id = album.id,
        name = album.name,
        details = null,
        photos = photos.map { it.toDevicePhoto() }.toMutableList(),
        source = Album.SOURCE_DEVICE
    )
}
