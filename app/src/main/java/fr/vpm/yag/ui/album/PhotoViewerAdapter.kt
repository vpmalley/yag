package fr.vpm.yag.ui.album

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
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
    private val onShareClick: (DevicePhoto) -> Unit = {}
) : RecyclerView.Adapter<PhotoViewerAdapter.PhotoViewHolder>() {

    private var photos: List<Photo> = emptyList()

    inner class PhotoViewHolder(private val binding: ItemPhotoViewerBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: Photo) {
            val devicePhoto = photo as? DevicePhoto
            val uri = devicePhoto?.uri
            
            if (uri != null) {
                Glide.with(binding.root.context)
                    .load(uri)
                    .apply(RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .fitCenter())
                    .into(binding.fullPhoto)
            } else {
                // No URI - set black background
                binding.fullPhoto.setImageDrawable(null)
                binding.fullPhoto.setBackgroundColor(
                    binding.root.context.getColor(android.R.color.black)
                )
            }
            
            // Show RAW badge if photo has RAW extension
            if (devicePhoto != null) {
                updateRawBadge(devicePhoto)
            }
            
            // Set up zoom listener for the ZoomableImageView
            (binding.fullPhoto as? ZoomableImageView)?.setOnZoomListener(
                object : ZoomableImageView.OnZoomListener {
                    override fun onZoomStarted() {}
                    override fun onZoomChanged(scale: Float) {}
                    override fun onZoomEnded() {}
                    override fun onDoubleTap() {}
                    override fun onSingleTap() {
                        // Handle single tap for full screen toggle
                        onPhotoClick()
                    }
                }
            )
            
            // Also keep the click listener for compatibility
            binding.fullPhoto.setOnClickListener {
                onPhotoClick()
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
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    fun submitList(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}
