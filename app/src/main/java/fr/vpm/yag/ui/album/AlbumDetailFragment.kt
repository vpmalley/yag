package fr.vpm.yag.ui.album

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.vpm.yag.MainActivity
import fr.vpm.yag.databinding.FragmentAlbumDetailBinding
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.isRawPhoto
import fr.vpm.yag.ui.dashboard.ColumnCountGestureListener
import fr.vpm.yag.ui.dashboard.GridSpacingItemDecoration
import fr.vpm.yag.ui.home.DeviceAlbumViewModel

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    // Share DeviceAlbumViewModel with other fragments
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()

    // Column count for the photo grid (1-6, default 3)
    private var columnCount = 3

    // Header collapse state
    private var isHeaderCollapsed = false
    private var totalScrollY = 0
    private val scrollThreshold = 20 // pixels to scroll before collapsing

    // Filter state
    private var isFilterEnabled = false
    private var allPhotos: List<fr.vpm.yag.device.model.Photo> = emptyList()

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
            setupAlbumHeader(album)
            setupRecyclerView(album)
            setupPinchToZoom()
            setupActionButtons()
            // Reset scroll state when album changes
            resetScrollState()
        }
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

    private fun setupRecyclerView(album: Album) {
        val recyclerView = binding.recyclerViewPhotos
        val albumId = album.id ?: return

        // Store all photos for filtering
        allPhotos = album.photos

        recyclerView.layoutManager = GridLayoutManager(requireContext(), columnCount)
        val adapter = PhotoGridAdapter(onPhotoClick = { position ->
            navigateToPhotoViewer(albumId, position)
        }).apply {
            submitList(getFilteredPhotos())
        }
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            GridSpacingItemDecoration(requireContext(), columnCount, 1)
        )
        
        // Set up scroll listener to collapse/expand header
        setupHeaderScrollListener(recyclerView)
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
        val action = AlbumDetailFragmentDirections.actionAlbumDetailFragmentToPhotoViewerFragment(
            albumId,
            photoPosition,
            isFilterEnabled
        )
        findNavController().navigate(action)
    }

    private fun setupPinchToZoom() {
        val scaleGestureDetector = ColumnCountGestureListener.create(
            context = requireContext(),
            minColumns = 1,
            maxColumns = 6,
            initialColumns = columnCount,
            onColumnCountChanged = { newColumns ->
                columnCount = newColumns
                updateGridLayout()
            }
        )

        binding.recyclerViewPhotos.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            // Also allow RecyclerView to handle touch events for scrolling
            false
        }
    }

    private fun setupActionButtons() {
        binding.filterButton.setOnClickListener {
            toggleRawFilter()
        }

        binding.shareButton.setOnClickListener {
            Toast.makeText(requireContext(), "Share", Toast.LENGTH_SHORT).show()
        }

        binding.deleteButton.setOnClickListener {
            Toast.makeText(requireContext(), "Delete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRawFilter() {
        isFilterEnabled = !isFilterEnabled
        updatePhotoList()
        val message = if (isFilterEnabled) {
            "RAW photos hidden"
        } else {
            "All photos shown"
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updatePhotoList() {
        val adapter = binding.recyclerViewPhotos.adapter as? PhotoGridAdapter
        adapter?.submitList(getFilteredPhotos())
    }

    private fun getFilteredPhotos(): List<fr.vpm.yag.device.model.Photo> {
        return if (isFilterEnabled) {
            allPhotos.filterNot { photo ->
                photo.isRawPhoto()
            }
        } else {
            allPhotos
        }
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
        // Clear toolbar title when fragment is destroyed
        val activity = requireActivity() as? MainActivity
        activity?.setToolbarTitle(null)
        _binding = null
    }
}
