package fr.vpm.yag.ui.album

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
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
import androidx.core.net.toUri

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

    // Request codes for favorite update permission
    private companion object {
        private const val REQUEST_CODE_FAVORITE_UPDATE = 1001
        private const val REQUEST_CODE_MANAGE_MEDIA = 1002

        // The URIs travel inside the PendingIntent, so cap them to stay under the binder size limit
        private const val MAX_WRITE_REQUEST_URIS = 500

        // Offer media management access at most once per app run, so declining doesn't nag
        private var mediaManagementOffered = false
    }

    // Pending favorite update state (for handling permission result)
    private var pendingFavoritePhoto: DevicePhoto? = null
    private var pendingFavoriteState: Boolean = false

    // Bottom sheet behavior
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var bottomSheetView: View? = null
    private var currentPhotoPosition: Int = 0
    private var isBottomSheetExpanded = false

    // Parallax effect variables
    private var initialTouchY = 0f
    private var initialImageY = 0f
    private var initialTouchX = 0f
    private var isSwipingVertically = false
    private var wasFullScreenBeforeSheet = false
    private var swipeDirectionDecided = false
    private var touchStartedInSwipeUpZone = false

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
        val filterModeOrdinal = args.filterMode
        val initialFilterMode =
            AlbumDetailViewModel.FilterMode.values().getOrNull(filterModeOrdinal)
                ?: AlbumDetailViewModel.FilterMode.NONE

        val album = findAlbumById(albumId)

        if (album != null) {
            // Set the album in the ViewModel if not already set
            // Only set the filter mode when first loading the album
            if (albumDetailViewModel.getAlbumId() != albumId) {
                albumDetailViewModel.setAlbum(album)
                albumDetailViewModel.setFilterMode(initialFilterMode)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_FAVORITE_UPDATE) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                // Permission granted, retry the pending favorite update
                pendingFavoritePhoto?.let { devicePhoto ->
                    updateFavoriteInMediaStore(devicePhoto, pendingFavoriteState)
                    pendingFavoritePhoto = null
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permission denied. Cannot update favorite.",
                    Toast.LENGTH_SHORT
                ).show()
                pendingFavoritePhoto = null
            }
        } else if (requestCode == REQUEST_CODE_MANAGE_MEDIA) {
            // The settings screen gives no meaningful result code: just retry. If access was
            // granted the write request goes through silently, otherwise it asks for the album
            pendingFavoritePhoto?.let { devicePhoto ->
                updateFavoriteInMediaStore(devicePhoto, pendingFavoriteState)
            }
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
                // Update local photo immediately for UI feedback
                devicePhoto.isFavorite = newFavoriteState
                // Notify ViewModels that photo data changed
                albumDetailViewModel.onPhotoFavoriteChanged(devicePhoto)
                deviceAlbumViewModel.updatePhotoFavoriteStatus(devicePhoto.id, newFavoriteState)
            },
            onDeleteClick = { devicePhoto ->
                showDeleteConfirmation(devicePhoto)
            },
            onShareClick = { devicePhoto ->
                sharePhoto(devicePhoto)
            },
            onPhotoLoaded = { _, photoView ->
                // Neighbouring pages are bound too, so this must not touch the "current" state
                setupPhotoParallax(photoView)
            }
        ).apply {
            submitList(photosToShow)
        }
        viewPager.adapter = adapter

        // Set initial position
        viewPager.setCurrentItem(initialPosition, false)
        currentPhotoPosition = initialPosition

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

    private fun updateViewPagerInputEnabled() {
        // Only allow swiping between photos when the bottom sheet is fully hidden
        // (not while it is dragged, settling or expanded)
        binding.viewPager.isUserInputEnabled =
            !::bottomSheetBehavior.isInitialized || bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN
    }

    private fun currentPhotoView(): View? {
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        return recyclerView.findViewHolderForAdapterPosition(binding.viewPager.currentItem)
            ?.itemView?.findViewById(R.id.fullPhoto)
    }

    private fun updateFavoriteInMediaStore(devicePhoto: DevicePhoto, isFavorite: Boolean) {
        Log.d("PhotoViewer", "Updating favorite for photo ${devicePhoto.id} to $isFavorite")

        CoroutineScope(Dispatchers.IO).launch {
            val contentUri = devicePhoto.uri ?: return@launch

            try {
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

                // Clear any pending favorite update
                pendingFavoritePhoto = null

            } catch (e: SecurityException) {
                // RecoverableSecurityException only exists on API 29+, so it can't be named in a
                // catch clause with minSdk 23: catch its parent and check the type behind a version guard
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    requestFavoriteWritePermission(e, devicePhoto, isFavorite, contentUri)
                } else {
                    showFavoriteUpdateError(devicePhoto, e)
                }
            } catch (e: Exception) {
                showFavoriteUpdateError(devicePhoto, e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun requestFavoriteWritePermission(
        e: RecoverableSecurityException,
        devicePhoto: DevicePhoto,
        isFavorite: Boolean,
        contentUri: Uri
    ) {
        Log.e("PhotoViewer", "Recoverable security exception for photo ${devicePhoto.id}", e)
        // Store pending state for retry after permission is granted
        pendingFavoritePhoto = devicePhoto
        pendingFavoriteState = isFavorite

        withContext(Dispatchers.Main) {
            if (shouldOfferMediaManagement()) {
                offerMediaManagement(e, contentUri)
            } else {
                launchFavoriteWriteRequest(e, contentUri)
            }
        }
    }

    private fun shouldOfferMediaManagement(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !mediaManagementOffered &&
                !MediaStore.canManageMedia(requireContext())

    @RequiresApi(Build.VERSION_CODES.S)
    private fun offerMediaManagement(e: RecoverableSecurityException, contentUri: Uri) {
        mediaManagementOffered = true
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Stop asking for each photo?")
            .setMessage(
                "Android asks before the app changes a photo. Allow YAG to manage media " +
                        "in the settings and favourites will be updated without asking."
            )
            .setPositiveButton("Open settings") { _, _ ->
                // The result comes back in onActivityResult with REQUEST_CODE_MANAGE_MEDIA
                val intent = Intent(
                    Settings.ACTION_REQUEST_MANAGE_MEDIA,
                    "package:${requireContext().packageName}".toUri()
                )
                try {
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_MEDIA)
                } catch (ex: Exception) {
                    Log.e("PhotoViewer", "Failed to open media management settings", ex)
                    launchFavoriteWriteRequest(e, contentUri)
                }
            }
            .setNegativeButton("Not now") { _, _ ->
                launchFavoriteWriteRequest(e, contentUri)
            }
            .setCancelable(false)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun launchFavoriteWriteRequest(e: RecoverableSecurityException, contentUri: Uri) {
        // Both APIs give a PendingIntent asking the user to allow the write;
        // the result comes back in onActivityResult with REQUEST_CODE_FAVORITE_UPDATE
        try {
            val writeRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: Use MediaStore.createWriteRequest, granted silently with media management
                MediaStore.createWriteRequest(
                    requireContext().contentResolver,
                    albumWriteUris(contentUri)
                )
            } else {
                // Android 10: Use the recovery action carried by the exception
                e.userAction.actionIntent
            }
            startIntentSenderForResult(
                writeRequest.intentSender,
                REQUEST_CODE_FAVORITE_UPDATE,
                null, 0, 0, 0, null
            )
        } catch (ex: Exception) {
            Log.e("PhotoViewer", "Failed to start permission request", ex)
            Toast.makeText(
                requireContext(),
                "Failed to request permission: ${ex.message}",
                Toast.LENGTH_LONG
            ).show()
            pendingFavoritePhoto = null
        }
    }

    // Ask for the whole album in one dialog, so the next favourites in it don't prompt again
    private fun albumWriteUris(contentUri: Uri): List<Uri> {
        val albumUris = albumDetailViewModel.currentAlbum.value?.photos.orEmpty()
            .mapNotNull { (it as? DevicePhoto)?.uri }
        return (listOf(contentUri) + albumUris).distinct().take(MAX_WRITE_REQUEST_URIS)
    }

    private suspend fun showFavoriteUpdateError(devicePhoto: DevicePhoto, e: Exception) {
        Log.e("PhotoViewer", "Failed to update IS_FAVORITE for photo ${devicePhoto.id}", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                requireContext(),
                "Failed to update favorite: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingFavoritePhoto = null
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
        // While the details sheet is open, a tap closes it (which restores the previous mode)
        if (::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            hideBottomSheet()
            return
        }
        setFullScreen(!isFullScreen)
    }

    private fun setFullScreen(fullScreen: Boolean) {
        if (isFullScreen == fullScreen) return
        isFullScreen = fullScreen
        updateSystemUiVisibility(fullScreen)
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
            // Swiping down goes straight to hidden instead of settling on the collapsed (peek) state,
            // which for this short sheet sits about as high as expanded and looks like a bounce
            bottomSheetBehavior.skipCollapsed = true
            bottomSheetBehavior.isDraggable = true
            bottomSheetBehavior.addBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    Log.d("photo", "bottom sheet state changed to $newState")
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            isBottomSheetExpanded = false
                            resetParallaxEffect()
                            setFullScreen(wasFullScreenBeforeSheet)
                        }

                        BottomSheetBehavior.STATE_EXPANDED -> {
                            isBottomSheetExpanded = true
                            // Update content when bottom sheet is expanded
                            updateBottomSheetContent(currentPhotoPosition)
                        }
                    }
                    updateViewPagerInputEnabled()
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    applyParallaxEffect(bottomSheet)
                }
            })

            // Going full screen when the sheet opens resizes the layout while the sheet is still
            // animating towards the old expanded position: re-target it, and refit the photo
            binding.root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_SETTLING -> if (!isBottomSheetExpanded) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }

                    BottomSheetBehavior.STATE_EXPANDED -> binding.root.post {
                        applyParallaxEffect(
                            bottomSheet
                        )
                    }
                }
            }
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
        val touchSlop = ViewConfiguration.get(photoView.context).scaledTouchSlop
        photoView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Use raw coordinates: event.y is relative to the view, which moves with the parallax
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialImageY = view.y
                    isSwipingVertically = false
                    swipeDirectionDecided = false

                    // Swipe up is only allowed from the bottom third, when the sheet is hidden
                    // and the photo is not zoomed in (otherwise the drag is a pan)
                    val viewPager = binding.viewPager
                    val location = intArrayOf(0, 0)
                    viewPager.getLocationOnScreen(location)
                    val viewPagerBottom = location[1] + viewPager.height
                    touchStartedInSwipeUpZone = ::bottomSheetBehavior.isInitialized &&
                            bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN &&
                            (view as? ZoomableImageView)?.isZoomed() != true &&
                            event.rawY > viewPagerBottom - (viewPager.height / 3)
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // Decide the direction once, as soon as the finger moved past the touch slop.
                    // ViewPager2 uses the (larger) paging slop, so we decide before it can intercept.
                    if (!swipeDirectionDecided && touchStartedInSwipeUpZone &&
                        (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)
                    ) {
                        swipeDirectionDecided = true
                        if (deltaY < 0 && Math.abs(deltaY) > Math.abs(deltaX)) {
                            isSwipingVertically = true
                            // Prevent ViewPager2 from stealing the rest of the gesture as a page swipe
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            // Cancel the image's own gesture handling so the release isn't seen as a tap
                            val cancel = MotionEvent.obtain(event)
                                .apply { action = MotionEvent.ACTION_CANCEL }
                            view.onTouchEvent(cancel)
                            cancel.recycle()
                        }
                    }

                    if (isSwipingVertically) {
                        // Once the sheet is showing, its onSlide drives the photo position
                        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                            // Apply parallax effect to the photo
                            val parallaxFactor = 0.3f
                            view.y = initialImageY + (Math.min(deltaY, 0f) * parallaxFactor)

                            // If swipe up is significant, show the bottom sheet
                            if (deltaY < -100) {
                                showBottomSheet()
                            }
                        }
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val consumed = isSwipingVertically
                    isSwipingVertically = false
                    swipeDirectionDecided = false
                    // Reset position if bottom sheet wasn't shown
                    if (::bottomSheetBehavior.isInitialized &&
                        bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN
                    ) {
                        view.y = initialImageY
                    }
                    consumed
                }

                else -> false
            }
        }
    }

    private fun applyParallaxEffect(bottomSheet: View) {
        val photoView = currentPhotoView() as? ImageView ?: return
        val parentHeight = (bottomSheet.parent as? View)?.height ?: return
        val viewHeight = photoView.height
        if (parentHeight == 0 || viewHeight == 0) return

        // Keep the photo centred in the space left above the sheet, shrinking it only if it doesn't fit
        val coveredHeight = (parentHeight - bottomSheet.top).coerceIn(0, viewHeight)
        val availableHeight = (viewHeight - coveredHeight).toFloat()
        val contentHeight = displayedImageHeight(photoView)
        val scale = if (contentHeight > availableHeight) availableHeight / contentHeight else 1f

        photoView.scaleX = scale
        photoView.scaleY = scale
        photoView.translationY = -coveredHeight / 2f
    }

    private fun displayedImageHeight(imageView: ImageView): Float {
        val drawable = imageView.drawable ?: return imageView.height.toFloat()
        val values = FloatArray(9)
        imageView.imageMatrix.getValues(values)
        return drawable.intrinsicHeight * values[Matrix.MSCALE_Y]
    }

    private fun resetParallaxEffect() {
        currentPhotoView()?.let { photoView ->
            photoView.translationY = 0f
            photoView.scaleX = 1f
            photoView.scaleY = 1f
        }
    }

    private fun showBottomSheet() {
        if (::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
            // Use the whole screen while the details are shown; remember the mode to restore it on hide
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                wasFullScreenBeforeSheet = isFullScreen
                setFullScreen(true)
            }
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
