package fr.vpm.yag

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import fr.vpm.yag.databinding.ActivityMainBinding
import fr.vpm.yag.ui.home.DeviceAlbumViewModel
import fr.vpm.yag.ui.settings.ListFolderViewModel
import fr.vpm.yag.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val Context.yagDataStore: DataStore<Preferences> by preferencesDataStore(name = "yagsettings")

    fun getSettingsViewModelFactory() = SettingsViewModel.Factory(yagDataStore)
    fun getListFolderViewModelFactory() = ListFolderViewModel.Factory(yagDataStore)

    private val settingsViewModel: SettingsViewModel by viewModels { getSettingsViewModelFactory() }
    private val deviceAlbumViewModel: DeviceAlbumViewModel by viewModels()

    fun setToolbarVisibility(visible: Boolean) {
        binding.toolbar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setNavBarVisibility(visible: Boolean) {
        binding.navView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setToolbarTitle(title: String?) {
        binding.toolbar.title = title
    }

    fun setToolbarSubtitle(subtitle: String?) {
        binding.toolbar.subtitle = subtitle
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Set up Toolbar with Navigation Component
        // Set up AppBarConfiguration with top-level destinations
        // The up button will automatically navigate up the back stack for nested fragments
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        
        // Scan device for photos at app startup
        scanDeviceAlbums()
    }

    private fun scanDeviceAlbums() {
        lifecycleScope.launch {
            deviceAlbumViewModel.fetchDeviceAlbums(applicationContext)
        }
    }
}