package fr.vpm.yag.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcloud.sdk.ApiClient
import com.pcloud.sdk.RemoteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Inject `apiClient` from the activity
 */
class ListFolderViewModel(private val apiClient: ApiClient) : ViewModel() {
    
    fun fetchListFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            val call = apiClient.listFolder(RemoteFolder.ROOT_FOLDER_ID.toLong())
            val remoteFolder = try {
                val result = call.execute()
                Log.d(
                    "bg-api",
                    "Received a remote folder response ${result.folderId()} with children ${
                        result.children().joinToString { it.name() }
                    }"
                )
                result
            } catch (t: Throwable) {
                Log.d("bg-api", "Received an exception $t")
                null
            }
        }
    }
}