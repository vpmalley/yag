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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class DeviceAlbumsRetriever {
    suspend fun getLocalAlbums(activity: Activity): Collection<Album> {
        return withContext(Dispatchers.IO) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            activity.contentResolver.query(
                collection,
                arrayOf(
                    MediaColumns._ID,
                    MediaColumns.DISPLAY_NAME,
                    MediaColumns.BUCKET_ID,
                    MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaColumns.DATA,
                    MediaColumns.TITLE,
                    MediaColumns.DATE_ADDED,
                    MediaColumns.SIZE,
                    MediaColumns.MIME_TYPE
                ),
                null,
                null,
                "${MediaColumns.DATE_ADDED} DESC"
            )?.use { localAlbumsCursor ->
                val albumsById = mapAlbums(localAlbumsCursor)
                Log.d("bg-media", "Got ${albumsById.size} albums")
                return@withContext albumsById.values
            }
            return@withContext emptyList()
        }
    }

    private fun mapAlbums(localImageCursor: Cursor): Map<String, Album> {
        val albumsById: MutableMap<String, Album> = HashMap<String, Album>()
        var currentAlbum: Album? = null
        if (localImageCursor.moveToFirst()) { // possibly optional
            while (localImageCursor.moveToNext()) {
                val bucketIdColumnIndex =
                    localImageCursor.getColumnIndex(MediaColumns.BUCKET_ID)
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
                }
                Log.d("bg-media", "Got a result for images")
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
                val mimeTypeColumn =
                    localImageCursor.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
                val mimeType = localImageCursor.getString(mimeTypeColumn)
                val sizeColumn = localImageCursor.getColumnIndex(MediaColumns.SIZE)
                val size = localImageCursor.getLong(sizeColumn)
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
        return albumsById
    }

    interface OnAlbumsRetrievedListener {
        fun onAlbumsRetrieved(albums: Collection<Album>)
    }
}