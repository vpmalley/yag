package fr.vpm.yag.ui.album

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import fr.vpm.yag.R
import fr.vpm.yag.databinding.ItemPhotoViewerBinding
import fr.vpm.yag.device.model.DevicePhoto
import fr.vpm.yag.device.model.Photo
import fr.vpm.yag.device.model.isRawPhoto

class PhotoViewerAdapter(
    private val onPhotoClick: () -> Unit = {},
    private val onFavoriteClick: (DevicePhoto, Boolean) -> Unit = { _, _ -> },
    private val onDeleteClick: (DevicePhoto) -> Unit = {},
    private val onShareClick: (DevicePhoto) -> Unit = {},
    private val onPhotoLoaded: (position: Int, photoView: View) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<PhotoViewerAdapter.PhotoViewHolder>() {

    private var photos: List<Photo> = emptyList()

    inner class PhotoViewHolder(private val binding: ItemPhotoViewerBinding) : 
        RecyclerView.ViewHolder(binding.root) {

        private var uri: Uri? = null
        private var isHighResLoaded = false

        init {
            binding.fullPhoto.setOnZoomListener(
                object : ZoomableImageView.OnZoomListener {
                    override fun onZoomStarted() {}
                    override fun onZoomChanged(scale: Float) {
                        // Only the zoomed-in photo is held in high resolution
                        if (scale > HIGH_RES_ZOOM_THRESHOLD && !isHighResLoaded) {
                            loadPhoto(highRes = true)
                        } else if (scale <= 1.01f && isHighResLoaded) {
                            loadPhoto(highRes = false)
                        }
                    }
                    override fun onZoomEnded() {}
                    override fun onDoubleTap() {}
                    override fun onSingleTap() {
                        // Handle single tap for full screen toggle
                        onPhotoClick()
                    }
                }
            )
        }

        fun bind(photo: Photo, position: Int) {
            val devicePhoto = photo as? DevicePhoto
            uri = devicePhoto?.uri
            isHighResLoaded = false
            binding.fullPhoto.resetZoom()

            if (uri != null) {
                loadPhoto(highRes = false)
            } else {
                // No URI - set dark grey background
                binding.fullPhoto.setImageDrawable(null)
                binding.fullPhoto.setBackgroundColor(
                    binding.root.context.getColor(R.color.photo_background)
                )
            }
            
            // Notify that this photo has been loaded
            onPhotoLoaded(position, binding.fullPhoto)
            
            // Show RAW badge if photo has RAW extension
            if (devicePhoto != null) {
                updateRawBadge(devicePhoto)
            }

            // Handle favorite button
            if (devicePhoto != null) {
                updateFavoriteButton(devicePhoto)
                binding.favoriteButton.setOnClickListener {
                    val newFavoriteState = !devicePhoto.isFavorite
                    devicePhoto.isFavorite = newFavoriteState
                    updateFavoriteButton(devicePhoto)
                    onFavoriteClick(devicePhoto, newFavoriteState)
                }
            }
            
            // Handle share button
            if (devicePhoto != null) {
                binding.shareButton.setOnClickListener {
                    onShareClick(devicePhoto)
                }
            }
            
            // Handle delete button
            if (devicePhoto != null) {
                binding.deleteButton.setOnClickListener {
                    onDeleteClick(devicePhoto)
                }
            }
        }
        
        private fun screenSizeRequest(uri: Uri) =
            Glide.with(binding.root.context)
                .load(uri)
                .apply(RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .fitCenter())

        private fun loadPhoto(highRes: Boolean) {
            val uri = uri ?: return
            isHighResLoaded = highRes
            if (highRes) {
                // The screen-size version is in the memory cache: show it while the high-res one decodes
                Glide.with(binding.root.context)
                    .load(uri)
                    .apply(RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(HIGH_RES_MAX_SIZE)
                        .downsample(DownsampleStrategy.CENTER_INSIDE))
                    .thumbnail(screenSizeRequest(uri))
                    .into(binding.fullPhoto)
            } else {
                screenSizeRequest(uri).into(binding.fullPhoto)
            }
        }

        fun clear() {
            // Release the bitmap so views waiting in the recycled pool don't hold photos in memory
            Glide.with(binding.root.context).clear(binding.fullPhoto)
            uri = null
            isHighResLoaded = false
        }

        private fun updateRawBadge(devicePhoto: DevicePhoto) {
            val isRaw = devicePhoto.isRawPhoto()
            binding.rawBadge.visibility = if (isRaw) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        private fun updateFavoriteButton(devicePhoto: DevicePhoto) {
            if (devicePhoto.isFavorite) {
                binding.favoriteButton.setImageResource(R.drawable.ic_heart_filled)
            } else {
                binding.favoriteButton.setImageResource(R.drawable.ic_heart_outline)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoViewerBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        holder.clear()
    }

    override fun getItemCount(): Int = photos.size

    fun submitList(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    companion object {
        private const val HIGH_RES_ZOOM_THRESHOLD = 1.5f
        // Keeps a high-res bitmap around 48-64 MB, within what the canvas can draw
        private const val HIGH_RES_MAX_SIZE = 4096
    }
}
