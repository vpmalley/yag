package fr.vpm.yag.ui.album

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import fr.vpm.yag.MainActivity
import fr.vpm.yag.R
import fr.vpm.yag.databinding.FragmentPhotoViewerBinding
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto
import fr.vpm.yag.ui.home.DeviceAlbumViewModel
import fr.vpm.yag.utils.ExifUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!

    // Share DeviceAlbumViewModel with other fragments for album list
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()

    // Share AlbumDetailViewModel with AlbumDetailFragment for sorting/filtering
    private val albumDetailViewModel: AlbumDetailViewModel by activityViewModels()

    // Receive arguments
    private val args: PhotoViewerFragmentArgs by navArgs()

    // Full screen mode flag
    private var isFullScreen = false

    // Bottom sheet behavior
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var bottomSheetView: View? = null
    private var currentPhotoPosition: Int = 0
    private var currentPhotoView: View? = null
    private var isBottomSheetExpanded = false

    // Parallax effect variables
    private var initialTouchY = 0f
    private var initialImageY = 0f
    private var isDraggingBottomSheet = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val albumId = args.albumId
        val initialPosition = args.photoPosition
        val filterEnabled = args.filterEnabled

        // Sync filter state with ViewModel
        albumDetailViewModel.setFilterEnabled(filterEnabled)

        val album = findAlbumById(albumId)

        if (album != null) {
            // Set the album in the ViewModel if not already set
            if (albumDetailViewModel.getAlbumId() != albumId) {
                albumDetailViewModel.setAlbum(album)
                albumDetailViewModel.setFilterEnabled(filterEnabled)
            }
            setupViewPager(initialPosition)
            setupBottomSheet()
        }
    }

    override fun onResume() {
        super.onResume()
        // Restore full screen state if needed
        updateSystemUiVisibility(isFullScreen)
    }

    override fun onPause() {
        super.onPause()
        // Ensure we exit full screen mode when fragment is paused
        // This prevents the app from staying in full screen when navigating to another fragment
        if (isFullScreen) {
            isFullScreen = false
            updateSystemUiVisibility(false)
        }
    }

    private fun findAlbumById(albumId: String): fr.vpm.yag.device.model.Album? {
        return deviceAlbumViewModel.albums.value?.firstOrNull { it.id == albumId }
    }

    private fun setupViewPager(initialPosition: Int) {
        val viewPager = binding.viewPager

        // Get the sorted and filtered photos from the ViewModel
        val photosToShow = albumDetailViewModel.getDisplayPhotos()

        // Create adapter with filtered photos and click listeners
        val adapter = PhotoViewerAdapter(
            onPhotoClick = {
                toggleFullScreen()
            },
            onFavoriteClick = { devicePhoto, newFavoriteState ->
                updateFavoriteInMediaStore(devicePhoto, newFavoriteState)
            },
            onDeleteClick = { devicePhoto ->
                showDeleteConfirmation(devicePhoto)
            },
            onShareClick = { devicePhoto ->
                sharePhoto(devicePhoto)
            },
            onPhotoLoaded = { position, photoView ->
                currentPhotoPosition = position
                currentPhotoView = photoView
                setupPhotoParallax(photoView)
            }
        ).apply {
            submitList(photosToShow)
        }
        viewPager.adapter = adapter

        // Set initial position
        viewPager.setCurrentItem(initialPosition, false)

        // Observe changes to display photos (in case filter/sort changes)
        albumDetailViewModel.displayPhotos.observe(viewLifecycleOwner) { photos ->
            adapter.submitList(photos)
        }

        // Listen for page changes to update current position and reset parallax
        viewPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPhotoPosition = position
                // Reset parallax effect when page changes
                resetParallaxEffect()
            }
        })
    }

    private fun updateFavoriteInMediaStore(devicePhoto: DevicePhoto, isFavorite: Boolean) {
        Log.d("PhotoViewer", "Updating favorite for photo ${devicePhoto.id} to $isFavorite")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentUri = devicePhoto.uri ?: return@launch

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_FAVORITE, if (isFavorite) 1 else 0)
                }

                requireContext().contentResolver.update(
                    contentUri,
                    values,
                    null,
                    null
                )

                Log.d("PhotoViewer", "Successfully updated IS_FAVORITE for photo ${devicePhoto.id}")
            } catch (e: Exception) {
                Log.e("PhotoViewer", "Failed to update IS_FAVORITE for photo ${devicePhoto.id}", e)
            }
        }
    }

    private fun showDeleteConfirmation(devicePhoto: DevicePhoto) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to move this photo to the recycle bin?")
            .setPositiveButton("Delete") { _, _ ->
                movePhotoToTrash(devicePhoto)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun movePhotoToTrash(devicePhoto: DevicePhoto) {
        val uri = devicePhoto.uri ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 31+ - use MediaStore trash API
            val trashUri = try {
                val id = ContentUris.parseId(uri)
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            } catch (e: NumberFormatException) {
                Toast.makeText(
                    requireContext(),
                    "Failed to move to trash: Invalid URI",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // Use MediaStore to create trash request
            val trashRequest = MediaStore.createTrashRequest(
                requireContext().contentResolver,
                listOf(trashUri),
                true  // allow partial deletion
            )

            try {
                trashRequest.send()
                Toast.makeText(requireContext(), "Photo moved to recycle bin", Toast.LENGTH_SHORT)
                    .show()
                // Remove the photo from the current list and notify adapter
                removePhotoFromList(devicePhoto)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to move to trash: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // For older Android versions, show a message that trash is not supported
            Toast.makeText(
                requireContext(),
                "Recycle bin is only available on Android 11+",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun removePhotoFromList(devicePhoto: DevicePhoto) {
        // Get current album
        val currentAlbum = albumDetailViewModel.currentAlbum.value ?: return

        // Create a new list without the deleted photo
        val updatedPhotos = currentAlbum.photos.filterNot { photo ->
            val p = photo as? DevicePhoto
            p?.id == devicePhoto.id
        }.toMutableList()

        // Create a new album with the updated photos
        val updatedAlbum = Album(
            id = currentAlbum.id,
            name = currentAlbum.name,
            details = currentAlbum.details,
            photos = updatedPhotos,
            source = currentAlbum.source
        )

        // Update the ViewModel with the new album
        albumDetailViewModel.setAlbum(updatedAlbum)

        // If we deleted the current photo, we may need to navigate back or adjust position
        val displayPhotos = albumDetailViewModel.getDisplayPhotos()
        if (displayPhotos.isEmpty()) {
            // If no photos left, navigate back
            findNavController().popBackStack()
        } else {
            // Check if we need to adjust the current position
            val currentPosition = args.photoPosition
            if (currentPosition >= displayPhotos.size) {
                // If current position is now out of bounds, navigate back
                findNavController().popBackStack()
            }
        }
    }

    private fun sharePhoto(devicePhoto: DevicePhoto) {
        val uri = devicePhoto.uri ?: return

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        updateSystemUiVisibility(isFullScreen)

        // Hide bottom sheet when entering full screen
        if (isFullScreen) {
            hideBottomSheet()
        }
    }

    private fun updateSystemUiVisibility(fullScreen: Boolean) {
        val activityWindow = activity?.window
        if (activityWindow == null) {
            Log.e(ContentValues.TAG, "Activity window is null")
            return
        }
        // Also hide/show the Toolbar and BottomNavigationView
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.setToolbarVisibility(!isFullScreen)
            mainActivity.setNavBarVisibility(!isFullScreen)
        }
        if (fullScreen) {
            // Hide system UI
            WindowInsetsControllerCompat(activityWindow, binding.root).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Show system UI
            WindowInsetsControllerCompat(
                activityWindow,
                binding.root
            ).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupBottomSheet() {
        // Inflate the bottom sheet layout
        bottomSheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.layout_photo_details_bottom_sheet, binding.root as ViewGroup, false)

        // Add the bottom sheet to the fragment layout
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        binding.root.addView(bottomSheetView, layoutParams)

        val bottomSheet = bottomSheetView?.findViewById<View>(R.id.bottomSheet)
        Log.d("photo", "bottom sheet set up")
        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            Log.d("photo", "bottom sheet behavior set up")

            bottomSheetBehavior.isHideable = true
            // Configure bottom sheet behavior
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            Log.d("photo", "bottom sheet is ${bottomSheetBehavior.state}")
//            bottomSheetBehavior.skipCollapsed = true
            bottomSheetBehavior.isDraggable = true  // Disable dragging, only show via swipe up
            bottomSheetBehavior.addBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    Log.d("photo", "bottom sheet state changed to $newState")
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            isBottomSheetExpanded = false
                            resetParallaxEffect()
                        }

                        BottomSheetBehavior.STATE_EXPANDED -> {
                            isBottomSheetExpanded = true
                            // Update content when bottom sheet is expanded
                            updateBottomSheetContent(currentPhotoPosition)
                        }

                        BottomSheetBehavior.STATE_DRAGGING -> {
                            isDraggingBottomSheet = true
                        }

                        BottomSheetBehavior.STATE_SETTLING -> {
                            isDraggingBottomSheet = false
                        }

                        else -> {
                            isDraggingBottomSheet = false
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Apply parallax effect based on slide offset
                    if (::bottomSheetBehavior.isInitialized) {
                        applyParallaxEffect(slideOffset)
                    }
                }
            })
        }

        // Set up drag handle touch listener for manual swipe
        val dragHandle = bottomSheetView?.findViewById<View>(R.id.dragHandle)
        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchY = event.y
                    true
                }

                else -> false
            }
        }

        // Set up nested scroll view
        val scrollView = bottomSheetView?.findViewById<NestedScrollView>(R.id.scrollView)
        scrollView?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            // Handle scroll events if needed
        }
    }

    private fun setupPhotoParallax(photoView: View) {
        photoView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchY = event.y
                    initialImageY = photoView.y
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - initialTouchY

                    // Check if we should start dragging the bottom sheet
                    if (!isDraggingBottomSheet && !isBottomSheetExpanded &&
                        deltaY < 0 && ::bottomSheetBehavior.isInitialized &&
                        bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN
                    ) {

                        // Only handle swipe up if we're at the bottom of the screen
                        val viewPager = binding.viewPager
                        val location = intArrayOf(0, 0)
                        viewPager.getLocationOnScreen(location)
                        val touchY = event.rawY
                        val viewPagerBottom = location[1] + viewPager.height

                        // If touch is in the bottom third of the screen, allow swipe up
                        if (touchY > viewPagerBottom - (viewPager.height / 3)) {
                            // Apply parallax effect to the photo
                            val parallaxFactor = 0.3f
                            val newY = initialImageY + (deltaY * parallaxFactor)
                            photoView.y = newY

                            // If swipe up is significant, show the bottom sheet
                            if (deltaY < -100) {
                                showBottomSheet()
                                return@setOnTouchListener true
                            }
                        }
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Reset position if bottom sheet wasn't shown
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                        photoView.y = initialImageY
                    }
                    false
                }

                else -> false
            }
        }
    }

    private fun applyParallaxEffect(slideOffset: Float) {
        // Apply parallax effect to the current photo view
        currentPhotoView?.let { photoView ->
            // Apply parallax effect: move the photo up as the bottom sheet expands
            val parallaxOffset = slideOffset * 200 // Adjust multiplier for desired effect
            photoView.y = -parallaxOffset
        }
    }

    private fun resetParallaxEffect() {
        // Reset the current photo view position
        currentPhotoView?.let { photoView ->
            photoView.y = 0f
        }
    }

    private fun showBottomSheet() {
        if (::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            // Update content when showing bottom sheet
            updateBottomSheetContent(currentPhotoPosition)
        }
    }

    private fun hideBottomSheet() {
        if (::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun updateBottomSheetContent(position: Int) {
        val photos = albumDetailViewModel.getDisplayPhotos()
        if (position >= 0 && position < photos.size) {
            val photo = photos[position] as? DevicePhoto ?: return

            val bottomSheetView = this.bottomSheetView
            if (bottomSheetView != null) {
                val photoName = bottomSheetView.findViewById<TextView>(R.id.photoName)
                val photoPath = bottomSheetView.findViewById<TextView>(R.id.photoPath)
                val fileSize = bottomSheetView.findViewById<TextView>(R.id.fileSize)
                val focalLength = bottomSheetView.findViewById<TextView>(R.id.focalLength)
                val shutterSpeed = bottomSheetView.findViewById<TextView>(R.id.shutterSpeed)
                val aperture = bottomSheetView.findViewById<TextView>(R.id.aperture)
                val iso = bottomSheetView.findViewById<TextView>(R.id.iso)
                val exifTagsContainer =
                    bottomSheetView.findViewById<ViewGroup>(R.id.exifTagsContainer)

                // Set basic photo info
                photoName.text = photo.displayName ?: "Unknown"
                photoPath.text = photo.path ?: "Unknown path"

                // Set file size
                photo.uri?.let { uri ->
                    fileSize.text = getFileSize(requireContext(), uri)
                } ?: run {
                    fileSize.text = "Unknown size"
                }

                // Load EXIF data asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    val exifData = photo.uri?.let { uri ->
                        ExifUtils.getExifData(requireContext(), uri)
                    } ?: ExifUtils.ExifData()

                    withContext(Dispatchers.Main) {
                        // Update EXIF fields
                        focalLength.text = exifData.focalLength
                        shutterSpeed.text = exifData.shutterSpeed
                        aperture.text = exifData.aperture
                        iso.text = exifData.iso

                        // Update EXIF tags
                        updateExifTags(exifTagsContainer, exifData)
                    }
                }
            }
        }
    }

    private fun updateExifTags(container: ViewGroup, exifData: ExifUtils.ExifData) {
        // Clear existing chips
        container.removeAllViews()

        // Get the list of EXIF tags
        val tags = ExifUtils.getExifTagList(exifData)

        // Create chips for each tag
        tags.forEach { tag ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_exif_chip, container, false) as Chip
            chip.text = tag
            chip.setOnClickListener {
                // Optional: show more details when chip is clicked
                val fullTagName = "TAG_$tag"
                val tagValue = exifData.allTags[fullTagName] ?: "N/A"
                Toast.makeText(requireContext(), "$tag: $tagValue", Toast.LENGTH_SHORT).show()
            }
            container.addView(chip)
        }

        // If no tags, show a message
        if (tags.isEmpty()) {
            val noTagsText = TextView(requireContext())
            noTagsText.text = "No EXIF tags found"
            noTagsText.setTextColor(0x80FFFFFF.toInt())
            noTagsText.textSize = 12f
            container.addView(noTagsText)
        }
    }

    private fun getFileSize(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val size = inputStream?.available()?.toLong() ?: 0L
            inputStream?.close()
            formatFileSize(size)
        } catch (e: Exception) {
            "Unknown size"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${
                String.format(
                    "%.2f",
                    bytes / (1024.0 * 1024.0 * 1024.0)
                )
            } GB"

            bytes >= 1024 * 1024 -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${String.format("%.2f", bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure we exit full screen mode when fragment is destroyed
        // This prevents the app from staying in full screen when navigating back
        if (isFullScreen) {
            isFullScreen = false
            updateSystemUiVisibility(false)
        }
        _binding = null
    }
}
