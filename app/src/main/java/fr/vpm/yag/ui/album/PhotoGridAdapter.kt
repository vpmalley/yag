package fr.vpm.yag.ui.album

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import fr.vpm.yag.R
import fr.vpm.yag.databinding.ItemPhotoGridBinding
import fr.vpm.yag.device.model.DevicePhoto
import fr.vpm.yag.device.model.Photo
import fr.vpm.yag.device.model.isRawPhoto

class PhotoGridAdapter(
    private val onPhotoClick: (Int) -> Unit = { _ -> },
    private val onPhotoLongClick: (Int) -> Unit = { _ -> },
    private val isSelectionMode: () -> Boolean = { false },
    private val isPhotoSelected: (Long) -> Boolean = { false }
) : RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder>() {

    private var photos: List<Photo> = emptyList()

    inner class PhotoViewHolder(private val binding: ItemPhotoGridBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: Photo, position: Int) {
            val devicePhoto = photo as? DevicePhoto
            val uri = devicePhoto?.uri
            
            if (uri != null) {
                Glide.with(binding.root.context)
                    .load(uri)
                    .override(300, 300)
                    .centerCrop()
                    .priority(Priority.LOW)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.color.purple_200)
                    .error(R.color.purple_200)
                    .into(binding.photoImage)
            } else {
                // No URI - set placeholder background
                setPlaceholderBackground()
            }
            
            // Show RAW badge if photo has RAW extension
            // Show favorite badge if photo is favorite
            if (devicePhoto != null) {
                updateRawBadge(devicePhoto)
                updateFavoriteBadge(devicePhoto)
                updateSelectionOverlay(devicePhoto.id, isSelectionMode(), isPhotoSelected)
            }
            
            // Set click listener - pass to handler
            binding.root.setOnClickListener {
                onPhotoClick(position)
            }
            
            // Set long click listener for selection mode
            binding.root.setOnLongClickListener {
                onPhotoLongClick(position)
                true
            }
        }
        
        private fun updateRawBadge(devicePhoto: DevicePhoto) {
            val isRaw = devicePhoto.isRawPhoto()
            binding.rawBadge.visibility = if (isRaw) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        private fun updateFavoriteBadge(devicePhoto: DevicePhoto) {
            binding.favoriteBadge.visibility = if (devicePhoto.isFavorite) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        private fun updateSelectionOverlay(photoId: Long, selectionMode: Boolean, isSelected: (Long) -> Boolean) {
            val selected = isSelected(photoId)
            // Only show overlay if in selection mode
            if (selectionMode) {
                binding.selectionCheckmark.visibility = if (selected) android.view.View.VISIBLE else android.view.View.GONE
            } else {
                binding.selectionCheckmark.visibility = android.view.View.GONE
            }
            // Add semi-transparent overlay when in selection mode
            if (selectionMode) {
                binding.photoImage.alpha = if (selected) 0.7f else 1.0f
            } else {
                binding.photoImage.alpha = 1.0f
            }
        }
        
        private fun setPlaceholderBackground() {
            Glide.with(binding.root.context).clear(binding.photoImage)
            binding.photoImage.setImageDrawable(null)
            binding.photoImage.setBackgroundColor(
                binding.root.context.getColor(R.color.purple_200)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoGridBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position], position)
    }

    override fun getItemCount(): Int = photos.size

    fun submitList(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}
