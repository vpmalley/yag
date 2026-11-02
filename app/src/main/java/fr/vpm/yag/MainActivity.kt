package fr.vpm.yag

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import fr.vpm.yag.cache.AlbumCacheRepository
import fr.vpm.yag.cache.AlbumDatabase
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

    private val albumCacheRepository: AlbumCacheRepository by lazy {
        AlbumCacheRepository(AlbumDatabase.getInstance(applicationContext))
    }

    private val deviceAlbumViewModel: DeviceAlbumViewModel by viewModels {
        DeviceAlbumViewModel.Factory(albumCacheRepository)
    }

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

        // Edge-to-edge: draw behind the status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

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

        // Cache-first loading: display cached albums immediately, then background refresh
        lifecycleScope.launch {
            deviceAlbumViewModel.loadAlbums(applicationContext)
        }

        // Start periodic refresh while app is active
        deviceAlbumViewModel.startPeriodicRefresh(applicationContext)

        // Refresh on app resume if last refresh was more than 15 minutes ago
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                deviceAlbumViewModel.refreshOnResume(applicationContext)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        deviceAlbumViewModel.stopPeriodicRefresh()
    }

    override fun onResume() {
        super.onResume()
        deviceAlbumViewModel.startPeriodicRefresh(applicationContext)
    }

    private fun applySystemBarInsets() {
        // The BottomNavigationView pads itself for the navigation bar.
        // Margins on gone views are ignored, so the photo viewer still goes full screen.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = bars.top
                leftMargin = bars.left
                rightMargin = bars.right
            }
            binding.navHostFragmentActivityMain.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = bars.left
                rightMargin = bars.right
            }
            insets
        }
    }
}
