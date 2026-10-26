package com.aeriotv.android.core.sync

/**
 * One category of synced data. iOS mirrors the same taxonomy under iCloud
 * Sync > Sync Categories; Dispatcharr Sync rows map onto these
 * ([com.aeriotv.android.core.sync.dsync.DsyncKey.category]) so each toggle
 * gates its rows.
 */
enum class SyncCategory(
    val displayName: String,
    val storageSuffix: String,
) {
    WatchProgress(
        displayName = "Watch Progress",
        storageSuffix = "watch_progress",
    ),
    Reminders(
        displayName = "Reminders",
        storageSuffix = "reminders",
    ),
    Favorites(
        displayName = "Favorites",
        storageSuffix = "favorites",
    ),
    Watchlist(
        displayName = "Watchlist",
        storageSuffix = "watchlist",
    ),
    Preferences(
        displayName = "App Preferences",
        storageSuffix = "preferences",
    );

    fun enabledStorageKey(): String = "syncCategory.$storageSuffix"
}
