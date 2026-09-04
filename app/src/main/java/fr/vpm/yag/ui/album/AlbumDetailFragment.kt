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
import fr.vpm.yag.databinding.FragmentAlbumDetailBinding
import fr.vpm.yag.device.model.Album
import fr.vpm.yag.device.model.DevicePhoto
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
        }
    }

    private fun findAlbumById(albumId: String): Album? {
        return deviceAlbumViewModel.albums.value?.firstOrNull { it.id == albumId }
    }

    private fun setupAlbumHeader(album: Album) {
        binding.albumName.text = album.name ?: "Unknown Album"
    }

    private fun setupRecyclerView(album: Album) {
        val recyclerView = binding.recyclerViewPhotos
        val albumId = album.id ?: return

        recyclerView.layoutManager = GridLayoutManager(requireContext(), columnCount)
        recyclerView.adapter = PhotoGridAdapter(onPhotoClick = { position ->
            navigateToPhotoViewer(albumId, position)
        }).apply {
            submitList(album.photos)
        }
        recyclerView.addItemDecoration(
            GridSpacingItemDecoration(requireContext(), columnCount, 1)
        )
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
            Toast.makeText(requireContext(), "Filter", Toast.LENGTH_SHORT).show()
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
                isRawPhoto(photo)
            }
        } else {
            allPhotos
        }
    }

    private fun isRawPhoto(photo: fr.vpm.yag.device.model.Photo): Boolean {
        val devicePhoto = photo as? DevicePhoto
        val path = devicePhoto?.path?.lowercase() ?: return false
        return path.endsWith(".arw", ignoreCase = true) ||
                path.endsWith(".cr2", ignoreCase = true) ||
                path.endsWith(".cr3", ignoreCase = true) ||
                path.endsWith(".nef", ignoreCase = true) ||
                path.endsWith(".raf", ignoreCase = true) ||
                path.endsWith(".dng", ignoreCase = true) ||
                path.endsWith(".rw2", ignoreCase = true) ||
                path.endsWith(".raw", ignoreCase = true)
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
        _binding = null
    }
}
