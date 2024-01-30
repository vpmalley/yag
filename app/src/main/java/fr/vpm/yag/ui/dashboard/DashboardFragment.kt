package fr.vpm.yag.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.pcloud.sdk.RemoteEntry
import com.pcloud.sdk.RemoteFolder
import fr.vpm.yag.MainActivity
import fr.vpm.yag.databinding.FragmentDashboardBinding
import fr.vpm.yag.ui.settings.ListFolderViewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val listFolderViewModel: ListFolderViewModel by activityViewModels { (activity as MainActivity).getListFolderViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textDashboard
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listFolderViewModel.getPCloudRootFolder()
            .observe(viewLifecycleOwner, Observer(::onRootFolderFetched))
        refreshData()
    }

    private fun onRootFolderFetched(remoteFolder: RemoteFolder?) {
        val rootName = remoteFolder?.asFolder()?.name()
        val rootChildrenCount = remoteFolder?.children()?.size
        val children = remoteFolder?.children()?.joinToString {
            logForFile(it)
        }
        binding.rootDescription.text = "Root `$rootName` has $rootChildrenCount children. $children"
    }

    private fun logForFile(it: RemoteEntry) = if (it.isFolder) {
        "`${it.name()}` with ${it.asFolder().children().size} children"
    } else {
        it.name()
    }

    private fun refreshData() {
        listFolderViewModel.fetchAllContentRecursively()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}