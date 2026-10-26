package com.aeriotv.android.core.update

import kotlinx.serialization.Serializable

/** A newer GitHub release the app can update to. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
)

sealed interface UpdateState {
    /** Nothing known (not yet checked, or updater disabled). */
    data object Idle : UpdateState

    /** Last check found no newer release. */
    data class UpToDate(val checkedAtMs: Long) : UpdateState

    data class Available(val info: UpdateInfo) : UpdateState

    data class Downloading(val info: UpdateInfo, val progressPercent: Int) : UpdateState

    data class Verifying(val info: UpdateInfo) : UpdateState

    /** Downloaded + verified; waiting for the user to tap Install. */
    data class ReadyToInstall(val info: UpdateInfo) : UpdateState

    /** Bounced to the "Install unknown apps" Settings toggle; the grant can
     *  kill this process, so a persisted PendingUpdate backs this state. */
    data class AwaitingInstallPermission(val info: UpdateInfo) : UpdateState

    /** Session committed; the system installer dialog owns the screen. */
    data class Installing(val info: UpdateInfo) : UpdateState

    data class Error(val message: String, val info: UpdateInfo?) : UpdateState
}

/**
 * Persisted across the two process-killing steps of the install flow
 * (unknown-sources grant, session commit) so the next launch can resume.
 * Stored as JSON in AppPreferences.updatePendingJson.
 */
@Serializable
data class PendingUpdate(
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val notes: String = "",
    val apkSizeBytes: Long = 0L,
)
