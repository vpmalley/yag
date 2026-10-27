package fr.vpm.yag.ui.album

import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
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
        // Set while the high-res photo is loading or shown
        private var highResTarget: CustomTarget<Drawable>? = null

        init {
            binding.fullPhoto.setOnZoomListener(
                object : ZoomableImageView.OnZoomListener {
                    override fun onZoomStarted() {}
                    override fun onZoomChanged(scale: Float) {
                        // Only the zoomed-in photo is held in high resolution
                        if (scale > HIGH_RES_ZOOM_THRESHOLD && highResTarget == null) {
                            loadPhoto(highRes = true)
                        } else if (scale <= 1.01f && highResTarget != null) {
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
            clear()
            uri = devicePhoto?.uri
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
            if (highRes) loadHighRes(uri) else loadScreenSize(uri)
        }

        private fun loadHighRes(uri: Uri) {
            // Decoded off-screen: the screen-size photo stays visible until the high-res one is ready
            val target = object : CustomTarget<Drawable>() {
                private var shown: Drawable? = null

                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    Glide.with(binding.root.context).clear(binding.fullPhoto)
                    shown = resource
                    binding.fullPhoto.setImageDrawable(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // The bitmap goes back to Glide: the view must not draw it anymore
                    if (binding.fullPhoto.drawable === shown) {
                        binding.fullPhoto.setImageDrawable(null)
                    }
                    shown = null
                }
            }
            highResTarget = target
            Glide.with(binding.root.context)
                .load(uri)
                .apply(RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(HIGH_RES_MAX_SIZE)
                    .downsample(DownsampleStrategy.CENTER_INSIDE))
                .into(target)
        }

        private fun loadScreenSize(uri: Uri) {
            val highRes = highResTarget ?: return
            highResTarget = null
            val highResDrawable = binding.fullPhoto.drawable
            if (highRes.request?.isComplete != true) {
                // Still decoding: the screen-size photo is still shown
                Glide.with(binding.root.context).clear(highRes)
                return
            }
            // Keep showing the high-res photo (still held by its target) until the screen-size one is ready
            screenSizeRequest(uri)
                .placeholder(highResDrawable)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean = false

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        // Released once the screen-size photo is set on the view
                        binding.root.post { Glide.with(binding.root.context).clear(highRes) }
                        return false
                    }
                })
                .into(binding.fullPhoto)
        }

        fun clear() {
            // Release the bitmaps so views waiting in the recycled pool don't hold photos in memory
            Glide.with(binding.root.context).clear(binding.fullPhoto)
            highResTarget?.let { Glide.with(binding.root.context).clear(it) }
            highResTarget = null
            uri = null
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
