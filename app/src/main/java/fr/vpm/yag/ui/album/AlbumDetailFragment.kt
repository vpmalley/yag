package fr.vpm.yag.ui.album

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import fr.vpm.yag.MainActivity
import fr.vpm.yag.R
import fr.vpm.yag.databinding.FragmentAlbumDetailBinding
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto
import fr.vpm.yag.device.model.Photo
import fr.vpm.yag.device.model.isRawPhoto
import fr.vpm.yag.ui.dashboard.GridSpacingItemDecoration
import fr.vpm.yag.ui.home.DeviceAlbumViewModel
import java.io.File
import kotlin.math.max
import kotlin.math.min

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    // Share DeviceAlbumViewModel with other fragments for album list
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()

    // Share AlbumDetailViewModel with PhotoViewerFragment for sorting/filtering
    private val albumDetailViewModel: AlbumDetailViewModel by activityViewModels()

    // Column count for the photo grid (1-6, default 3)
    private var columnCount = 3

    // Header collapse state
    private var isHeaderCollapsed = false
    private var totalScrollY = 0
    private val scrollThreshold = 20 // pixels to scroll before collapsing
    
    // Touch state
    private var isDraggingForSelection = false
    private var lastSelectedPosition = -1
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var downTime = 0L
    
    // Gesture detectors
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Receive album ID as argument
    private val args: AlbumDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as? MainActivity
        activity?.let { ctx ->
            // Setup pinch-to-zoom
            scaleGestureDetector = ScaleGestureDetector(
                ctx,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val scaleFactor = detector.scaleFactor
                        // Invert scale: pinch out (zoom in) = decrease columns, pinch in (zoom out) = increase columns
                        if (scaleFactor > 1.01f) {
                            // Zoom out gesture (pinch in) - increase columns
                            columnCount = kotlin.math.min(columnCount + 1, 6)
                            updateGridLayout()
                        } else if (scaleFactor < 0.99f) {
                            // Zoom in gesture (pinch out) - decrease columns
                            columnCount = kotlin.math.max(columnCount - 1, 1)
                            updateGridLayout()
                        }
                        return true
                    }
                }
            )
        }

        val albumId = args.albumId
        val album = findAlbumById(albumId)

        if (album != null) {
            // Set the album in the AlbumDetailViewModel
            albumDetailViewModel.setAlbum(album)
            
            // Sync filter state if coming from PhotoViewer
            val currentFilterState = albumDetailViewModel.isFilterEnabled()
            albumDetailViewModel.setFilterEnabled(currentFilterState)
            
            setupAlbumHeader(album)
            setupRecyclerView()
            setupActionButtons()
            // Reset scroll state when album changes
            resetScrollState()
        }
        
        // Setup back press callback to handle back button when in selection mode
        setupBackPressHandler()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (albumDetailViewModel.isSelectionModeActive()) {
                        // In selection mode - just exit selection mode, don't navigate back
                        albumDetailViewModel.setSelectionMode(false)
                    } else {
                        // Not in selection mode - let default back behavior proceed
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun resetScrollState() {
        totalScrollY = 0
        isHeaderCollapsed = false
        // Ensure header is visible
        binding.actionButtonsHeader.visibility = View.VISIBLE
    }

    private fun findAlbumById(albumId: String): Album? {
        return deviceAlbumViewModel.albums.value?.firstOrNull { it.id == albumId }
    }

    private fun setupAlbumHeader(album: Album) {
        // Set album name in the toolbar
        val activity = requireActivity() as? MainActivity
        activity?.setToolbarTitle(album.name ?: "Unknown Album")
    }

    private fun setupRecyclerView() {
        val recyclerView = binding.recyclerViewPhotos

        val adapter = PhotoGridAdapter(
            onPhotoClick = { position ->
                handlePhotoClick(position)
            },
            onPhotoLongClick = { position ->
                handlePhotoLongClick(position)
            },
            isSelectionMode = { albumDetailViewModel.isSelectionModeActive() },
            isPhotoSelected = { photoId ->
                albumDetailViewModel.isPhotoSelected(photoId)
            }
        ).apply {
            submitList(albumDetailViewModel.getDisplayPhotos())
        }
        
        recyclerView.layoutManager = GridLayoutManager(requireContext(), columnCount)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            GridSpacingItemDecoration(requireContext(), columnCount, 1)
        )
        
        // Set up touch listener for pinch-to-zoom (always active)
        setupPinchToZoom(recyclerView)
        
        // Set up scroll listener to collapse/expand header
        setupHeaderScrollListener(recyclerView)
        
        // Observe changes to display photos and update adapter
        albumDetailViewModel.displayPhotos.observe(viewLifecycleOwner) { photos ->
            adapter.submitList(photos)
        }
        
        // Observe selection state changes to refresh adapter
        albumDetailViewModel.selectedPhotoIds.observe(viewLifecycleOwner) { _ ->
            adapter.notifyDataSetChanged()
            updateSelectionCountText()
        }
        
        // Observe selection mode changes
        albumDetailViewModel.isSelectionMode.observe(viewLifecycleOwner) { isSelectionMode ->
            updateSelectionModeUI(isSelectionMode)
        }
    }

    private fun setupPinchToZoom(recyclerView: RecyclerView) {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        
        recyclerView.setOnTouchListener { _, event ->
            // Let ScaleGestureDetector handle pinch gestures
            val scaleHandled = scaleGestureDetector.onTouchEvent(event)
            if (scaleHandled) {
                return@setOnTouchListener true
            }
            
            // Handle touch events for selection mode drag and photo taps/long-presses
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Check if we're over a photo
                    val view = recyclerView.findChildViewUnder(event.x, event.y)
                    if (view != null) {
                        initialTouchX = event.x
                        initialTouchY = event.y
                        lastSelectedPosition = recyclerView.getChildAdapterPosition(view)
                        downTime = event.eventTime
                    } else {
                        lastSelectedPosition = -1
                        downTime = 0L
                    }
                    false
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (lastSelectedPosition != -1) {
                        val dx = Math.abs(event.x - initialTouchX)
                        val dy = Math.abs(event.y - initialTouchY)
                        val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                        
                        if (albumDetailViewModel.isSelectionModeActive() && distance > touchSlop) {
                            // Drag selection
                            isDraggingForSelection = true
                            val view = recyclerView.findChildViewUnder(event.x, event.y)
                            if (view != null) {
                                val position = recyclerView.getChildAdapterPosition(view)
                                if (position != RecyclerView.NO_POSITION && position != lastSelectedPosition) {
                                    lastSelectedPosition = position
                                    val photos = albumDetailViewModel.getDisplayPhotos()
                                    if (position < photos.size) {
                                        val photo = photos[position] as? DevicePhoto
                                        photo?.id?.let { photoId ->
                                            albumDetailViewModel.selectPhoto(photoId)
                                        }
                                    }
                                }
                            }
                            true
                        }
                        // Allow RecyclerView to handle scrolling
                        false
                    } else {
                        false
                    }
                }
                
                MotionEvent.ACTION_UP -> {
                    if (lastSelectedPosition != -1 && downTime > 0L) {
                        val dx = Math.abs(event.x - initialTouchX)
                        val dy = Math.abs(event.y - initialTouchY)
                        val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
                        
                        if (distance <= touchSlop) {
                            val pressDuration = event.eventTime - downTime
                            if (pressDuration >= 500) {
                                handlePhotoLongClick(lastSelectedPosition)
                            } else {
                                handlePhotoClick(lastSelectedPosition)
                            }
                        }
                    }
                    lastSelectedPosition = -1
                    downTime = 0L
                    isDraggingForSelection = false
                    false
                }
                
                else -> false
            }
        }
    }

    private fun setupHeaderScrollListener(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                totalScrollY += dy
                
                if (dy > 0 && !isHeaderCollapsed) {
                    // Scrolling down - accumulate scroll distance
                    if (totalScrollY > scrollThreshold) {
                        collapseHeader()
                        isHeaderCollapsed = true
                    }
                } else if (dy < 0 && isHeaderCollapsed) {
                    // Scrolling up - immediately expand
                    expandHeader()
                    isHeaderCollapsed = false
                    totalScrollY = 0
                }
                
                // Reset if we're at the top
                if (totalScrollY < 0) {
                    totalScrollY = 0
                }
            }
        })
    }

    private fun collapseHeader() {
        binding.actionButtonsHeader.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.actionButtonsHeader.visibility = View.GONE
            }
            .start()
    }

    private fun expandHeader() {
        binding.actionButtonsHeader.visibility = View.VISIBLE
        binding.actionButtonsHeader.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun navigateToPhotoViewer(albumId: String, photoPosition: Int) {
        // Set the current position in the ViewModel before navigating
        albumDetailViewModel.setCurrentPhotoPosition(photoPosition)
        
        // We only need to pass the album ID now, the ViewModel will have the rest
        val action = AlbumDetailFragmentDirections.actionAlbumDetailFragmentToPhotoViewerFragment(
            albumId,
            photoPosition,
            albumDetailViewModel.isFilterEnabled()
        )
        findNavController().navigate(action)
    }

    private fun handlePhotoClick(position: Int) {
        val photos = albumDetailViewModel.getDisplayPhotos()
        if (position >= 0 && position < photos.size) {
            val photo = photos[position] as? DevicePhoto ?: return
            
            if (albumDetailViewModel.isSelectionModeActive()) {
                // In selection mode - toggle photo selection
                albumDetailViewModel.togglePhotoSelection(photo.id)
            } else {
                // Normal mode - navigate to photo viewer
                navigateToPhotoViewer(args.albumId, position)
            }
        }
    }

    private fun handlePhotoLongClick(position: Int) {
        val photos = albumDetailViewModel.getDisplayPhotos()
        if (position >= 0 && position < photos.size) {
            val photo = photos[position] as? DevicePhoto ?: return
            
            // Enter selection mode and select this photo
            albumDetailViewModel.setSelectionMode(true)
            albumDetailViewModel.selectPhoto(photo.id)
        }
    }

    private fun updateSelectionModeUI(isSelectionMode: Boolean) {
        // Show/hide selection-related UI
        binding.actionButtonsHeader.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        binding.selectionActionBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        
        // Update title to show selection count when in selection mode
        val activity = requireActivity() as? MainActivity
        if (isSelectionMode) {
            updateSelectionCountText()
            activity?.setToolbarTitle("Select photos")
        } else {
            val album = findAlbumById(args.albumId)
            activity?.setToolbarTitle(album?.name ?: "Unknown Album")
        }
    }

    private fun updateSelectionCountText() {
        val selectedCount = albumDetailViewModel.getSelectedCount()
        binding.selectionCountText.text = "$selectedCount selected"
    }



    private fun setupActionButtons() {
        binding.filterButton.setOnClickListener {
            toggleRawFilter()
        }

        binding.shareButton.setOnClickListener {
            showShareOptionsBottomSheet()
        }

        binding.deleteButton.setOnClickListener {
            showDeleteOptionsBottomSheet()
        }

        // Setup selection action bar
        setupSelectionActionBar()
    }

    private fun setupSelectionActionBar() {
        binding.selectAllButton.setOnClickListener {
            albumDetailViewModel.selectAllPhotos()
        }

        binding.shareSelectedButton.setOnClickListener {
            shareSelectedPhotos()
        }

        binding.cancelSelectionButton.setOnClickListener {
            albumDetailViewModel.setSelectionMode(false)
        }
    }

    private fun shareSelectedPhotos() {
        val selectedPhotos = albumDetailViewModel.getSelectedPhotos()
        val photoUris = selectedPhotos.mapNotNull { photo ->
            (photo as? DevicePhoto)?.uri
        }
        
        if (photoUris.isEmpty()) {
            Toast.makeText(requireContext(), "No photos selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        sharePhotos(photoUris)
    }

    private fun showDeleteOptionsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val bottomSheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_delete_options, null)
        
        bottomSheetDialog.setContentView(bottomSheetView as View)
        
        bottomSheetView.findViewById<View>(R.id.deleteNonFavoritesButton)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            deleteNonFavoritePhotos()
        }
        
        bottomSheetDialog.show()
    }

    private fun deleteNonFavoritePhotos() {
        val allPhotos = albumDetailViewModel.getDisplayPhotos()
        val nonFavoritePhotos = allPhotos.filter { photo ->
            (photo as? DevicePhoto)?.isFavorite != true
        }
        
        if (nonFavoritePhotos.isEmpty()) {
            Toast.makeText(requireContext(), "No non-favorite photos to delete", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Count how many will be deleted
        val count = nonFavoritePhotos.size
        
        // Move to recycle bin/trash
        movePhotosToTrash(nonFavoritePhotos)
        
        Toast.makeText(requireContext(), "Moved $count non-favorite photos to recycle bin", Toast.LENGTH_SHORT).show()
    }

    private fun movePhotosToTrash(photos: List<Photo>) {
        val uris = photos.mapNotNull { photo ->
            (photo as? DevicePhoto)?.uri
        }
        
        if (uris.isEmpty()) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 31+ - use MediaStore trash API
            // createTrashRequest was introduced in API 31
            val trashUris = uris.mapNotNull { uri ->
                val id = try {
                    ContentUris.parseId(uri)
                } catch (e: NumberFormatException) {
                    null
                } ?: return@mapNotNull null
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
            
            // Use MediaStore to create trash request
            val trashRequest = MediaStore.createTrashRequest(
                requireContext().contentResolver,
                trashUris,
                true  // allow partial deletion
            )
            
            try {
                trashRequest.send()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to move to trash: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun showShareOptionsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val bottomSheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_share_options, null)
        
        bottomSheetDialog.setContentView(bottomSheetView as View)
        
        // Set up buttons
        bottomSheetView.findViewById<View>(R.id.shareAllButton)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            shareAllPhotos()
        }
        
        bottomSheetView.findViewById<View>(R.id.shareFavoritesButton)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            shareFavoritePhotos()
        }
        
        bottomSheetView.findViewById<View>(R.id.shareFavoritesWithRawButton)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            shareFavoritePhotosWithRaw()
        }
        
        bottomSheetDialog.show()
    }

    private fun shareAllPhotos() {
        val photosToShare = getAllPhotosUris(albumDetailViewModel.getDisplayPhotos())
        
        if (photosToShare.isEmpty()) {
            Toast.makeText(requireContext(), "No photos to share", Toast.LENGTH_SHORT).show()
            return
        }
        
        sharePhotos(photosToShare)
    }

    private fun shareFavoritePhotos() {
        val photosToShare = getFavoritePhotosUris(albumDetailViewModel.getDisplayPhotos())
        
        if (photosToShare.isEmpty()) {
            Toast.makeText(requireContext(), "No favorite photos to share", Toast.LENGTH_SHORT).show()
            return
        }
        
        sharePhotos(photosToShare)
    }

    private fun shareFavoritePhotosWithRaw() {
        val allPhotos = albumDetailViewModel.getDisplayPhotos()
        val photosToShare = getFavoritePhotosWithRawMatches(allPhotos)
        
        if (photosToShare.isEmpty()) {
            Toast.makeText(requireContext(), "No favorite photos to share", Toast.LENGTH_SHORT).show()
            return
        }
        
        sharePhotos(photosToShare)
    }

    private fun getAllPhotosUris(photos: List<Photo>): List<Uri> {
        return photos.mapNotNull { photo ->
            (photo as? DevicePhoto)?.uri
        }
    }

    private fun getFavoritePhotosUris(photos: List<Photo>): List<Uri> {
        return photos.filter { photo ->
            (photo as? DevicePhoto)?.isFavorite == true
        }.mapNotNull { photo ->
            (photo as? DevicePhoto)?.uri
        }
    }

    private fun getFavoritePhotosWithRawMatches(allPhotos: List<Photo>): List<Uri> {
        val favoritePhotos = allPhotos.filter { photo ->
            (photo as? DevicePhoto)?.isFavorite == true
        }
        
        val photosToShare = ArrayList<Uri>()
        val processedFileNames = mutableSetOf<String>()
        
        for (favPhoto in favoritePhotos) {
            val devicePhoto = favPhoto as? DevicePhoto ?: continue
            val path = devicePhoto.path ?: continue
            
            // Extract base filename without extension
            val baseName = File(path).nameWithoutExtension
            
            // Skip if we've already processed this file
            if (processedFileNames.contains(baseName)) {
                continue
            }
            processedFileNames.add(baseName)
            
            // Find all photos with the same base filename (favorite or not, RAW or not)
            for (photo in allPhotos) {
                val devicePhotoInner = photo as? DevicePhoto ?: continue
                val innerPath = devicePhotoInner.path ?: continue
                val innerBaseName = File(innerPath).nameWithoutExtension
                
                if (innerBaseName == baseName) {
                    devicePhotoInner.uri?.let { uri ->
                        photosToShare.add(uri)
                    }
                }
            }
        }
        
        return photosToShare
    }

    private fun sharePhotos(photoUris: List<Uri>) {
        if (photoUris.isEmpty()) return
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(photoUris))
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Photos"))
    }

    private fun toggleRawFilter() {
        albumDetailViewModel.toggleFilter()
        val message = if (albumDetailViewModel.isFilterEnabled()) {
            "RAW photos hidden"
        } else {
            "All photos shown"
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateGridLayout() {
        binding.recyclerViewPhotos.layoutManager = GridLayoutManager(requireContext(), columnCount)
        // Remove existing decorations before adding new one
        while (binding.recyclerViewPhotos.itemDecorationCount > 0) {
            binding.recyclerViewPhotos.removeItemDecorationAt(0)
        }
        binding.recyclerViewPhotos.addItemDecoration(
            GridSpacingItemDecoration(
                requireContext(),
                columnCount,
                1
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Exit selection mode when fragment is destroyed
        albumDetailViewModel.setSelectionMode(false)
        // Clear toolbar title when fragment is destroyed
        val activity = requireActivity() as? MainActivity
        activity?.setToolbarTitle(null)
        _binding = null
    }
}
