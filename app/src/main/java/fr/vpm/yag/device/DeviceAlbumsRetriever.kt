package fr.vpm.yag.device

import android.app.Activity
import android.content.ContentUris
import android.database.Cursor
import android.os.Build
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.util.Log
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto
import java.util.Date

class DeviceAlbumsRetriever {
    fun getLocalAlbums(activity: Activity, onAlbumsRetrievedListener: OnAlbumsRetrievedListener) {

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        activity.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.DATE_ADDED
            ),
            null,
            null,
            null
        )?.use { localAlbumsCursor ->
            val albumsById = mapAlbums(localAlbumsCursor)
            Log.d("bg-media", "Got ${albumsById.size} albums")
            onAlbumsRetrievedListener.onAlbumsRetrieved(albumsById.values)
        }
    }

    private fun mapAlbums(localImageCursor: Cursor): Map<String, Album> {
        val albumsById: MutableMap<String, Album> = HashMap<String, Album>()
        var currentAlbum: Album? = null
        if (localImageCursor.moveToFirst()) {
            while (localImageCursor.moveToNext()) {
                val bucketIdColumnIndex = localImageCursor.getColumnIndex(MediaColumns.BUCKET_ID)
                val bucketId = localImageCursor.getString(bucketIdColumnIndex)
                val bucketNameColumnIndex =
                    localImageCursor.getColumnIndex(MediaColumns.BUCKET_DISPLAY_NAME)
                val bucketName =
                    localImageCursor.getString(bucketNameColumnIndex)
                Log.d("bg-media", "Got image of bucket $bucketName")
                if (currentAlbum == null || !albumsById.containsKey(bucketId)) {
                    currentAlbum =
                        Album(bucketId, bucketName, null, mutableListOf(), Album.SOURCE_DEVICE)
                    albumsById[bucketId] = currentAlbum
                    Log.d("bg-media", "Got a result for images")
                    // extract first image
                    val idColumnIndex = localImageCursor.getColumnIndex(BaseColumns._ID)
                    val photoId = localImageCursor.getLong(idColumnIndex)
                    val fileNameColumnIndex = localImageCursor.getColumnIndex(MediaColumns.DATA)
                    val fileName = localImageCursor.getString(fileNameColumnIndex)
                    val displayNameColumnIndex =
                        localImageCursor.getColumnIndex(MediaColumns.DISPLAY_NAME)
                    val displayName = localImageCursor.getString(displayNameColumnIndex)
                    val dateAddedColumnIndex =
                        localImageCursor.getColumnIndex(MediaColumns.DATE_ADDED)
                    val dateAdded = localImageCursor.getString(dateAddedColumnIndex)
                    val photoUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        photoId
                    )
                    val devicePhoto =
                        DevicePhoto(
                            photoId,
                            photoUri,
                            fileName,
                            displayName,
                            Date(dateAdded.toLong())
                        )
                    albumsById[bucketId]?.addPhoto(devicePhoto)
                }
            }
        }
        return albumsById
    }

    interface OnAlbumsRetrievedListener {
        fun onAlbumsRetrieved(albums: Collection<Album>)
    }
}