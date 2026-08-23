package fr.vpm.yag.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.vpm.yag.databinding.FragmentDashboardBinding
import fr.vpm.yag.ui.home.DeviceAlbumViewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    // Share DeviceAlbumViewModel with HomeFragment
    private val deviceAlbumViewModel: DeviceAlbumViewModel by activityViewModels()
    
    // Configurable column count (1-6, default 3)
    private var columnCount = 3

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?, 
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupColumnCountSelector()
        observeAlbums()
    }

    private fun setupRecyclerView() {
        val recyclerView = binding.recyclerViewAlbums
        recyclerView.layoutManager = GridLayoutManager(requireContext(), columnCount)
        recyclerView.adapter = AlbumGridAdapter()
        recyclerView.addItemDecoration(
            GridSpacingItemDecoration(requireContext(), columnCount, 1)
        )
    }

    private fun setupColumnCountSelector() {
        binding.columnCountSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    // Progress is 0-5, map to 1-6 columns
                    columnCount = progress + 1
                    binding.recyclerViewAlbums.layoutManager = 
                        GridLayoutManager(requireContext(), columnCount)
                    binding.recyclerViewAlbums.addItemDecoration(
                        GridSpacingItemDecoration(requireContext(), columnCount, 1)
                    )
                    binding.columnCountText.text = "Columns: $columnCount"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            }
        )
        // Initialize
        binding.columnCountSeekBar.progress = 2 // Default to 3 columns
        binding.columnCountText.text = "Columns: $columnCount"
    }

    private fun observeAlbums() {
        deviceAlbumViewModel.albums.observe(viewLifecycleOwner) { albums ->
            (binding.recyclerViewAlbums.adapter as AlbumGridAdapter).submitList(
                albums.toList()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}