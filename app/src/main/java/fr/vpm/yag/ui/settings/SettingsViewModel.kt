package fr.vpm.yag.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcloud.sdk.AuthorizationData
import fr.vpm.yag.network.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is where you can adjust settings"
    }
    val text: LiveData<String> = _text

    fun saveAuthorization(authorizationResult: AuthorizationData) {
        viewModelScope.launch {
            settingsRepository.saveAuthorization(authorizationResult)
        }
    }

    class Factory(private val dataStore: DataStore<Preferences>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass == SettingsViewModel::class.java) {
                val settingsRepository = SettingsRepository(dataStore)
                return SettingsViewModel(settingsRepository) as T
            }
            return super.create(modelClass)
        }
    }

}