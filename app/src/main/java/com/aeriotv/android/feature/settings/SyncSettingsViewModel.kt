package com.aeriotv.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeriotv.android.core.data.db.entity.isDispatcharrDirectConnect
import com.aeriotv.android.core.preferences.AppPreferences
import com.aeriotv.android.core.sync.SyncCategory
import com.aeriotv.android.core.sync.dsync.DispatcharrSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Backs SyncSettingsScreen and SyncCategoriesScreen (Dispatcharr Sync). */
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val dsync: DispatcharrSyncManager,
    playlistDao: com.aeriotv.android.core.data.db.dao.PlaylistDao,
) : ViewModel() {

    /** Dispatcharr Sync is on (the stored provider is "dispatcharr"). */
    val dispatcharrEnabled: Flow<Boolean> =
        prefs.syncProvider.map { it == DispatcharrSyncManager.PROVIDER }

    /** Dispatcharr is offered only while the active playlist is Dispatcharr Direct Connect. */
    val dispatcharrAvailable: Flow<Boolean> = playlistDao.observeActive().map { rows ->
        rows.firstOrNull()?.isDispatcharrDirectConnect() == true
    }

    fun setDispatcharrEnabled(value: Boolean) {
        viewModelScope.launch {
            prefs.setSyncProvider(if (value) DispatcharrSyncManager.PROVIDER else "off")
        }
    }

    val dispatcharrUi: StateFlow<DispatcharrSyncManager.Ui> = dsync.ui
    fun dispatcharrSyncNow() = dsync.syncNow()

    fun categoryEnabled(category: SyncCategory): Flow<Boolean> =
        prefs.syncCategoryEnabled(category)

    fun setCategoryEnabled(category: SyncCategory, value: Boolean) {
        viewModelScope.launch { prefs.setSyncCategoryEnabled(category, value) }
    }

    /** Slice of App Preferences, not a category of its own - see
     *  AppPreferences.syncRemoteControlMap for why. Per-device. */
    val syncRemoteControlMap: Flow<Boolean> = prefs.syncRemoteControlMap
    fun setSyncRemoteControlMap(value: Boolean) {
        viewModelScope.launch { prefs.setSyncRemoteControlMap(value) }
    }

    /** Radio Mode by Default slice of App Preferences. Default off, per-device. */
    val syncRadioModeByDefault: Flow<Boolean> = prefs.syncRadioModeByDefault
    fun setSyncRadioModeByDefault(value: Boolean) {
        viewModelScope.launch { prefs.setSyncRadioModeByDefault(value) }
    }
}
