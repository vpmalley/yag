package fr.vpm.yag.ui.settings

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.pcloud.sdk.AuthorizationData
import fr.vpm.yag.network.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val settingsRepository = SettingsRepository()

    private val _text = MutableLiveData<String>().apply {
        value = "This is where you can adjust settings"
    }
    val text: LiveData<String> = _text

    fun saveAuthorization(context: Context, authorizationResult: AuthorizationData) {
        viewModelScope.launch {
            settingsRepository.saveAuthorization(context, authorizationResult)
        }
    }
}