package fr.vpm.yag.ui.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.pcloud.sdk.RemoteFolder
import fr.vpm.yag.network.PCloudClient
import fr.vpm.yag.network.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Inject `apiClient` from the activity
 */
class ListFolderViewModel(private val pClient: PCloudClient?) : ViewModel() {

    private var remoteFolderFlow: Flow<RemoteFolder?> = emptyFlow()

    fun getPCloudRootFolder() = remoteFolderFlow.asLiveData()

    fun fetchAllContentRecursively() {
        viewModelScope.launch {
            val currentClient = pClient ?: return@launch
            remoteFolderFlow = currentClient.getPCloudClientAsFlow().map { apiClient ->
                val call = apiClient.listFolder(RemoteFolder.ROOT_FOLDER_ID.toLong())
                call.execute()
            }.catch { t: Throwable ->
                Log.d("bg-api", "Received exception $t")
                // TODO emit something, a kind of error probably
            }.flowOn(Dispatchers.IO)
        }
    }

    class Factory(private val dataStore: DataStore<Preferences>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass == ListFolderViewModel::class.java) {
                val settingsRepository = SettingsRepository(dataStore)
                val client = PCloudClient(settingsRepository)
                return ListFolderViewModel(client) as T
            }
            return super.create(modelClass)
        }
    }
}