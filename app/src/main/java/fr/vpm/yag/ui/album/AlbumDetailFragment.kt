package fr.vpm.yag.ui.album

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
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
    private val collapseThreshold = 20 // pixels to scroll before collapsing
    private val expandThreshold = 10 // pixels from top before expanding

    private lateinit var photoGridAdapter: PhotoGridAdapter
    private var gridSpacingDecoration: GridSpacingItemDecoration? = null

    // Long-press then drag to select a range of photos
    private lateinit var dragSelectTouchListener: DragSelectTouchListener
    // Selection when the drag started, so dragging back over photos un-selects only the ones added by the drag
    private var selectionBeforeDrag: Set<Long> = emptySet()

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

        val albumId = args.albumId
        val album = findAlbumById(albumId)

        if (album != null) {
            // Set the album in the AlbumDetailViewModel
            albumDetailViewModel.setAlbum(album)
            
            // Sync filter state if coming from PhotoViewer
            val currentFilterMode = albumDetailViewModel.getFilterMode()
            albumDetailViewModel.setFilterMode(currentFilterMode)
            
            setupAlbumHeader(album)
            setupRecyclerView()
            setupFilterChips()
            observeMediaStoreRefresh()
            // Reset scroll state when album changes
            resetScrollState()

            // Setup back press callback to handle back button when in selection mode
            setupBackPressHandler()
        } else {
            // The library has not been scanned yet (e.g. the process was recreated while
            // the app was in the background) or the album no longer exists. Redirect to
            // Home, which reflects the ongoing library scan.
            navigateToHome()
        }
    }

    private fun navigateToHome() {
        Toast.makeText(requireContext(), R.string.album_not_found, Toast.LENGTH_SHORT).show()
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.navigation_home, true)
            .build()
        findNavController().navigate(R.id.navigation_home, null, navOptions)
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
        // Ensure header is visible with animation
        if (binding.filterChipsContainer.visibility != View.VISIBLE || binding.filterChipsContainer.alpha < 1f) {
            binding.filterChipsContainer.visibility = View.VISIBLE
            binding.filterChipsContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun findAlbumById(albumId: String): Album? {
        return deviceAlbumViewModel.albums.value?.firstOrNull { it.id == albumId }
    }

    private fun setupAlbumHeader(album: Album) {
        // Set album name in the toolbar
        val activity = requireActivity() as? MainActivity
        activity?.setToolbarTitle(album.name ?: getString(R.string.unknown_album))
        
        // Set photo count as subtitle
        val photoCount = album.photos.size
        activity?.setToolbarSubtitle(getString(R.string.photo_count, photoCount))
    }

    private fun setupRecyclerView() {
        val recyclerView = binding.recyclerViewPhotos

        photoGridAdapter = PhotoGridAdapter(
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
        recyclerView.adapter = photoGridAdapter
        updateGridSpacing()
        
        // Pinch to change the column count (only kicks in with two fingers)
        recyclerView.addOnItemTouchListener(
            PinchToResizeTouchListener(
                requireContext(),
                minColumns = MIN_COLUMNS,
                maxColumns = MAX_COLUMNS,
                getColumnCount = { columnCount },
                onColumnCountChanged = { newCount ->
                    columnCount = newCount
                    updateGridLayout()
                }
            )
        )
        
        // Long-press then drag to select a range of photos
        dragSelectTouchListener = DragSelectTouchListener(recyclerView) { anchor, current ->
            selectRange(anchor, current)
        }
        recyclerView.addOnItemTouchListener(dragSelectTouchListener)
        
        // Set up scroll listener to collapse/expand header
        setupHeaderScrollListener(recyclerView)
        
        // Observe changes to display photos and update adapter
        albumDetailViewModel.displayPhotos.observe(viewLifecycleOwner) { photos ->
            photoGridAdapter.submitList(photos)
            // Show empty state if no photos match the filter
            val emptyStateLayout = binding.emptyStateLayout
            val recyclerView = binding.recyclerViewPhotos
            if (photos.isEmpty()) {
                emptyStateLayout.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyStateLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
        
        // Observe selection state changes to refresh the selection overlays
        albumDetailViewModel.selectedPhotoIds.observe(viewLifecycleOwner) { _ ->
            photoGridAdapter.notifySelectionChanged()
            updateSelectionCountText()
        }
        
        // Observe selection mode changes
        albumDetailViewModel.isSelectionMode.observe(viewLifecycleOwner) { isSelectionMode ->
            updateSelectionModeUI(isSelectionMode)
        }
    }

    private fun selectRange(fromPosition: Int, toPosition: Int) {
        val photos = albumDetailViewModel.getDisplayPhotos()
        val rangeIds = (min(fromPosition, toPosition)..max(fromPosition, toPosition)).mapNotNull { position ->
            (photos.getOrNull(position) as? DevicePhoto)?.id
        }
        albumDetailViewModel.setSelectedPhotos(selectionBeforeDrag + rangeIds)
    }

    private fun setupHeaderScrollListener(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // Don't process scroll events for header when in selection mode
                // The selection action bar is always visible in selection mode
                if (albumDetailViewModel.isSelectionModeActive()) {
                    return
                }
                
                // Track scroll position
                totalScrollY += dy
                
                // Clamp scroll position
                if (totalScrollY < 0) {
                    totalScrollY = 0
                }
                
                // Collapse when scrolled down past threshold
                if (!isHeaderCollapsed && totalScrollY > collapseThreshold) {
                    collapseHeader()
                    isHeaderCollapsed = true
                }
                // Expand when scrolled back up near top (with hysteresis)
                else if (isHeaderCollapsed && totalScrollY < expandThreshold) {
                    expandHeader()
                    isHeaderCollapsed = false
                }
                
                // Also check if we can scroll up no more (at the top)
                if (!recyclerView.canScrollVertically(-1) && isHeaderCollapsed) {
                    expandHeader()
                    isHeaderCollapsed = false
                    totalScrollY = 0
                }
            }
        })
    }

    private fun collapseHeader() {
        binding.filterChipsContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.filterChipsContainer.visibility = View.GONE
            }
            .start()
    }

    private fun expandHeader() {
        binding.filterChipsContainer.visibility = View.VISIBLE
        binding.filterChipsContainer.animate()
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
            albumDetailViewModel.getFilterMode().ordinal
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
            
            // Keep following the finger to select every photo it drags over
            selectionBeforeDrag = albumDetailViewModel.selectedPhotoIds.value ?: emptySet()
            dragSelectTouchListener.startDragSelection(position)
        }
    }

    private fun updateSelectionModeUI(isSelectionMode: Boolean) {
        // Update title and subtitle when in selection mode
        val activity = requireActivity() as? MainActivity
        if (isSelectionMode) {
            updateSelectionCountText()
            activity?.setToolbarTitle(getString(R.string.select_photos))
            activity?.setToolbarSubtitle(null)
            // Reset scroll state when entering selection mode
            // to ensure the header is fully visible when we exit
            isHeaderCollapsed = false
            totalScrollY = 0
        } else {
            val album = findAlbumById(args.albumId)
            activity?.setToolbarTitle(album?.name ?: getString(R.string.unknown_album))
            // Restore photo count subtitle
            val photoCount = album?.photos?.size ?: 0
            activity?.setToolbarSubtitle(getString(R.string.photo_count, photoCount))
        }
        
        // Animate the transition between filter chips and selection bar
        val duration = 200L
        if (isSelectionMode) {
            // Hide filter chips, show selection bar
            if (binding.filterChipsContainer.visibility == View.VISIBLE) {
                binding.filterChipsContainer.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .withEndAction {
                        binding.filterChipsContainer.visibility = View.GONE
                    }
                    .start()
            } else {
                binding.filterChipsContainer.visibility = View.GONE
            }
            
            binding.selectionActionBar.visibility = View.VISIBLE
            binding.selectionActionBar.alpha = 0f
            binding.selectionActionBar.animate()
                .alpha(1f)
                .setDuration(duration)
                .start()
        } else {
            // Hide selection bar, show filter chips
            if (binding.selectionActionBar.visibility == View.VISIBLE) {
                binding.selectionActionBar.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .withEndAction {
                        binding.selectionActionBar.visibility = View.GONE
                    }
                    .start()
            } else {
                binding.selectionActionBar.visibility = View.GONE
            }
            
            binding.filterChipsContainer.visibility = View.VISIBLE
            binding.filterChipsContainer.alpha = 0f
            binding.filterChipsContainer.animate()
                .alpha(1f)
                .setDuration(duration)
                .start()
        }
    }

    private fun updateSelectionCountText() {
        val selectedCount = albumDetailViewModel.getSelectedCount()
        binding.selectionCountText.text = getString(R.string.selected_count, selectedCount)
    }



    private fun setupFilterChips() {
        // Setup filter chip click listeners
        binding.noRawChip.setOnClickListener {
            val currentMode = albumDetailViewModel.getFilterMode()
            val newMode = if (currentMode == AlbumDetailViewModel.FilterMode.NO_RAW) {
                AlbumDetailViewModel.FilterMode.NONE
            } else {
                AlbumDetailViewModel.FilterMode.NO_RAW
            }
            setFilterMode(newMode)
        }

        binding.favesChip.setOnClickListener {
            val currentMode = albumDetailViewModel.getFilterMode()
            val newMode = if (currentMode == AlbumDetailViewModel.FilterMode.FAVOURITES_ONLY) {
                AlbumDetailViewModel.FilterMode.NONE
            } else {
                AlbumDetailViewModel.FilterMode.FAVOURITES_ONLY
            }
            setFilterMode(newMode)
        }

        binding.favesWithRawChip.setOnClickListener {
            val currentMode = albumDetailViewModel.getFilterMode()
            val newMode = if (currentMode == AlbumDetailViewModel.FilterMode.FAVOURITES_WITH_RAW) {
                AlbumDetailViewModel.FilterMode.NONE
            } else {
                AlbumDetailViewModel.FilterMode.FAVOURITES_WITH_RAW
            }
            setFilterMode(newMode)
        }

        binding.nonFavesChip.setOnClickListener {
            val currentMode = albumDetailViewModel.getFilterMode()
            val newMode = if (currentMode == AlbumDetailViewModel.FilterMode.NON_FAVOURITES_ONLY) {
                AlbumDetailViewModel.FilterMode.NONE
            } else {
                AlbumDetailViewModel.FilterMode.NON_FAVOURITES_ONLY
            }
            setFilterMode(newMode)
        }

        // Initialize chip selection state based on current filter mode
        updateFilterChipSelection()

        // Observe filter mode changes to update chip selection
        albumDetailViewModel.filterMode.observe(viewLifecycleOwner) {
            updateFilterChipSelection()
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

        binding.deleteSelectedButton.setOnClickListener {
            showDeleteConfirmationDialog()
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

    private fun showDeleteConfirmationDialog() {
        val selectedCount = albumDetailViewModel.getSelectedCount()
        
        if (selectedCount == 0) {
            Toast.makeText(requireContext(), getString(R.string.no_photos_selected), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Determine plural form for the message
        val photoSuffix = if (selectedCount > 1) "s" else ""
        val message = getString(R.string.delete_photos_confirmation, selectedCount, photoSuffix)
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_photos_title)
            .setMessage(message)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                deleteSelectedPhotos()
            }
            .setNegativeButton(R.string.cancel_action, null)
            .show()
    }

    private fun deleteSelectedPhotos() {
        val selectedPhotos = albumDetailViewModel.getSelectedPhotos()
        val photosToDelete = selectedPhotos.mapNotNull { photo ->
            photo as? DevicePhoto
        }
        
        if (photosToDelete.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_photos_to_delete), Toast.LENGTH_SHORT).show()
            return
        }
        
        val count = photosToDelete.size
        movePhotosToTrash(photosToDelete)
        // Filter the deleted photos out of the collection and re-scan the album
        onPhotosDeleted(photosToDelete)

        // Exit selection mode after deletion
        albumDetailViewModel.setSelectionMode(false)
        
        // Show toast with proper pluralization
        val photoSuffix = if (count > 1) "s" else ""
        Toast.makeText(requireContext(), getString(R.string.moved_to_recycle_bin, count, photoSuffix), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), getString(R.string.no_non_favorite_photos_to_delete), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Count how many will be deleted
        val count = nonFavoritePhotos.size
        
        // Move to recycle bin/trash
        movePhotosToTrash(nonFavoritePhotos)
        // Filter the deleted photos out of the collection and re-scan the album
        onPhotosDeleted(nonFavoritePhotos)

        Toast.makeText(requireContext(), getString(R.string.moved_non_favorites_to_recycle_bin, count), Toast.LENGTH_SHORT).show()
    }

    /**
     * Called after photos have been moved to the trash: filters them out of the
     * displayed collection right away, then re-scans the album from the Media Store.
     * The adapter is updated through the displayPhotos observer, immediately by the
     * filter and again when the fresh MediaStore scan completes.
     */
    private fun onPhotosDeleted(deletedPhotos: List<Photo>) {
        val deletedIds = deletedPhotos.mapNotNull { photo ->
            (photo as? DevicePhoto)?.id
        }
        if (deletedIds.isEmpty()) return
        albumDetailViewModel.removePhotos(deletedIds)
        deviceAlbumViewModel.refreshAlbums(requireContext().applicationContext)
    }

    /**
     * Observe MediaStore re-scans: when a fresh scan contains the current album,
     * update the collection and the adapter accordingly.
     */
    private fun observeMediaStoreRefresh() {
        deviceAlbumViewModel.albums.observe(viewLifecycleOwner) { albums ->
            val freshAlbum = albums.firstOrNull { it.id == args.albumId } ?: return@observe
            albumDetailViewModel.setAlbum(freshAlbum)
            if (!albumDetailViewModel.isSelectionModeActive()) {
                val activity = requireActivity() as? MainActivity
                activity?.setToolbarSubtitle(getString(R.string.photo_count, freshAlbum.photos.size))
            }
        }
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
                Toast.makeText(requireContext(), getString(R.string.failed_to_move_to_trash, e.message), Toast.LENGTH_LONG).show()
            }
        } else {
            // For older Android versions, show a message that trash is not supported
            Toast.makeText(
                requireContext(),
                getString(R.string.recycle_bin_unavailable),
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
            Toast.makeText(requireContext(), getString(R.string.no_photos_to_share), Toast.LENGTH_SHORT).show()
            return
        }
        
        sharePhotos(photosToShare)
    }

    private fun shareFavoritePhotos() {
        val photosToShare = getFavoritePhotosUris(albumDetailViewModel.getDisplayPhotos())
        
        if (photosToShare.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_favorite_photos_to_share), Toast.LENGTH_SHORT).show()
            return
        }
        
        sharePhotos(photosToShare)
    }

    private fun shareFavoritePhotosWithRaw() {
        val allPhotos = albumDetailViewModel.getDisplayPhotos()
        val photosToShare = getFavoritePhotosWithRawMatches(allPhotos)
        
        if (photosToShare.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_favorite_photos_to_share), Toast.LENGTH_SHORT).show()
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
        
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_photos)))
    }

    private fun setFilterMode(mode: AlbumDetailViewModel.FilterMode) {
        albumDetailViewModel.setFilterMode(mode)
        val message = when (mode) {
            AlbumDetailViewModel.FilterMode.NO_RAW -> getString(R.string.filter_no_raw_applied)
            AlbumDetailViewModel.FilterMode.FAVOURITES_ONLY -> getString(R.string.filter_favourites_only)
            AlbumDetailViewModel.FilterMode.FAVOURITES_WITH_RAW -> getString(R.string.filter_favourites_with_raw)
            AlbumDetailViewModel.FilterMode.NON_FAVOURITES_ONLY -> getString(R.string.filter_non_favourites_only)
            AlbumDetailViewModel.FilterMode.NONE -> getString(R.string.filter_all_photos)
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        
        // Update chip selection state
        updateFilterChipSelection()
    }

    private fun updateFilterChipSelection() {
        val currentMode = albumDetailViewModel.getFilterMode()
        binding.noRawChip.isChecked = currentMode == AlbumDetailViewModel.FilterMode.NO_RAW
        binding.favesChip.isChecked = currentMode == AlbumDetailViewModel.FilterMode.FAVOURITES_ONLY
        binding.favesWithRawChip.isChecked = currentMode == AlbumDetailViewModel.FilterMode.FAVOURITES_WITH_RAW
        binding.nonFavesChip.isChecked = currentMode == AlbumDetailViewModel.FilterMode.NON_FAVOURITES_ONLY
    }

    private fun toggleRawFilter() {
        albumDetailViewModel.toggleFilter()
        val message = if (albumDetailViewModel.isFilterEnabled()) {
            getString(R.string.filter_raw_hidden)
        } else {
            getString(R.string.filter_all_shown)
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateGridLayout() {
        // Changing the span count in place keeps the scroll position
        (binding.recyclerViewPhotos.layoutManager as? GridLayoutManager)?.spanCount = columnCount
        updateGridSpacing()
    }

    private fun updateGridSpacing() {
        val recyclerView = binding.recyclerViewPhotos
        gridSpacingDecoration?.let { recyclerView.removeItemDecoration(it) }
        gridSpacingDecoration = GridSpacingItemDecoration(requireContext(), columnCount, 1).also {
            recyclerView.addItemDecoration(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Exit selection mode when fragment is destroyed
        albumDetailViewModel.setSelectionMode(false)
        // Clear toolbar title and subtitle when fragment is destroyed
        val activity = requireActivity() as? MainActivity
        activity?.setToolbarTitle(null)
        activity?.setToolbarSubtitle(null)
        _binding = null
    }

    companion object {
        private const val MIN_COLUMNS = 1
        private const val MAX_COLUMNS = 6
    }
}
