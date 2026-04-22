package fr.vpm.yag.ui.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.pcloud.sdk.RemoteEntry
import com.pcloud.sdk.RemoteFolder
import fr.vpm.yag.network.PCloudClient
import fr.vpm.yag.network.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SearchCriteria(
    val query: String = "",
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val isFile: Boolean? = null
)

/**
 * Inject `apiClient` from the activity
 */
class ListFolderViewModel(private val pClient: PCloudClient?) : ViewModel() {

    private val fetchTrigger = MutableStateFlow(Unit)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val remoteFolderFlow: Flow<RemoteFolder?> = fetchTrigger.flatMapLatest {
        pClient?.getPCloudClientAsFlow()?.map { apiClient ->
            apiClient.listFolder(RemoteFolder.ROOT_FOLDER_ID.toLong()).execute()
        }?.catch { t: Throwable ->
            Log.d("bg-api", "Received exception $t")
            emit(null)
        } ?: flowOf(null)
    }.flowOn(Dispatchers.IO)

    fun getPCloudRootFolder() = remoteFolderFlow.asLiveData()

    private val searchCriteria = MutableStateFlow(SearchCriteria())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResultsFlow: Flow<List<RemoteEntry>> = searchCriteria.flatMapLatest { criteria ->
        if (criteria.query.isBlank() && criteria.minSize == null && criteria.maxSize == null && criteria.isFile == null) {
            flowOf(emptyList())
        } else {
            pClient?.getPCloudClientAsFlow()?.map { apiClient ->
                val root = apiClient.listFolder(RemoteFolder.ROOT_FOLDER_ID.toLong(), true).execute()
                searchRecursively(root, criteria)
            }?.catch { t: Throwable ->
                Log.d("bg-api", "Received exception during search $t")
                emit(emptyList())
            } ?: flowOf(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    fun getSearchResults() = searchResultsFlow.asLiveData()

    fun searchFiles(query: String, minSize: Long? = null, maxSize: Long? = null, isFile: Boolean? = null) {
        searchCriteria.value = SearchCriteria(query, minSize, maxSize, isFile)
    }

    private fun searchRecursively(folder: RemoteFolder, criteria: SearchCriteria): List<RemoteEntry> {
        val results = mutableListOf<RemoteEntry>()
        for (child in folder.children()) {
            if (matchesCriteria(child, criteria)) {
                results.add(child)
            }
            if (child.isFolder) {
                results.addAll(searchRecursively(child.asFolder(), criteria))
            }
        }
        return results
    }

    private fun matchesCriteria(entry: RemoteEntry, criteria: SearchCriteria): Boolean {
        if (criteria.query.isNotBlank() && !entry.name().contains(criteria.query, ignoreCase = true)) {
            return false
        }
        if (criteria.isFile != null && entry.isFile != criteria.isFile) {
            return false
        }
        if (entry.isFile) {
            val file = entry.asFile()
            if (criteria.minSize != null && file.size() < criteria.minSize) {
                return false
            }
            if (criteria.maxSize != null && file.size() > criteria.maxSize) {
                return false
            }
        } else if (criteria.minSize != null || criteria.maxSize != null) {
            // Folders don't have a simple size attribute in this context
            return false
        }
        return true
    }

    fun fetchAllContentRecursively() {
        fetchTrigger.value = Unit
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
