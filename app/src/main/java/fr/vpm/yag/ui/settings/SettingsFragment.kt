package fr.vpm.yag.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pcloud.sdk.AuthorizationActivity
import com.pcloud.sdk.AuthorizationRequest
import fr.vpm.yag.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collect

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val settingsViewModel: SettingsViewModel by activityViewModels()
    private val listFolderViewModel: ListFolderViewModel by activityViewModels()

    private val getAuthorization =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("bg-login", "Received result from login activity")
            it.data?.let { resultIntent ->
                val authorization = AuthorizationActivity.getResult(resultIntent)
                context?.let { ctx -> settingsViewModel.saveAuthorization(ctx, authorization) }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textNotifications
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pcloudLogin.setOnClickListener {
            val intent = AuthorizationActivity.createIntent(
                requireContext(),
                AuthorizationRequest.create().setType(AuthorizationRequest.Type.TOKEN)
                    .setClientId("Ri8SOlM0Csz").build()
            )
            Log.d("bg-login", "Created intent to launch with $getAuthorization")
            getAuthorization.launch(intent)

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}