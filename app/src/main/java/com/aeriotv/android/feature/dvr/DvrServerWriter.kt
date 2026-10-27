package com.aeriotv.android.feature.dvr

import com.aeriotv.android.core.data.capability.Capability
import com.aeriotv.android.core.data.capability.CapabilityCorrections
import com.aeriotv.android.core.data.capability.CapabilityNotice
import com.aeriotv.android.core.data.capability.CapabilityNotices
import com.aeriotv.android.core.data.capability.CapabilityState
import com.aeriotv.android.core.data.capability.deniedMessage
import com.aeriotv.android.core.data.capability.networkRestrictionMessage
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import com.aeriotv.android.core.data.db.entity.allows
import com.aeriotv.android.core.data.db.entity.capabilities
import com.aeriotv.android.core.data.db.entity.isDenied
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.core.network.DispatcharrAuthBroker
import com.aeriotv.android.core.network.DispatcharrClient
import com.aeriotv.android.core.network.DispatcharrError
import com.aeriotv.android.core.network.DispatcharrRecording
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped Dispatcharr DVR writes, shared by [DvrViewModel] and surfaces that
 * must record WITHOUT a DvrViewModel of their own (the reminder banner lives at
 * the navigation root, where a DvrViewModel would keep its 30 s server poll
 * running for the life of the activity). Owns the capability self-correction
 * every mutating DVR call goes through; list state stays in DvrViewModel.
 */
@Singleton
class DvrServerWriter @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val dispatcharrClient: DispatcharrClient,
    private val dispatcharrAuth: DispatcharrAuthBroker,
) {
    /** A created server recording plus what DvrViewModel needs to map it to a row. */
    class Scheduled(
        val recording: DispatcharrRecording,
        val baseUrl: String,
        val playlist: PlaylistEntity,
    )

    /**
     * Run a MUTATING Dispatcharr call that assumed [capability], and
     * self-correct when the server disagrees.
     *
     * On 401 / 403 we re-probe /api/accounts/users/me/, re-derive the
     * capability, and then either
     *  - demote the capability for this session (so the affordance disappears
     *    instead of failing a second time) and surface the specific missing
     *    permission, or
     *  - when the re-derived permissions say it SHOULD be allowed, say it looks
     *    like a network restriction on the account (per-user allowed_networks),
     *    which is the other thing that produces a 403.
     *
     * On SUCCESS, a capability we believed Denied is promoted, so a server-side
     * grant applies immediately rather than at the next TTL expiry.
     *
     * [onCorrected] runs after either correction (with the user-facing message
     * on a denial, null on a promotion) so a caller holding list state can
     * re-read it. Either way the error is returned to the caller: these are
     * never swallowed.
     */
    suspend fun <T> withCapability(
        capability: Capability,
        onCorrected: suspend (errorMessage: String?) -> Unit = {},
        block: suspend () -> T,
    ): Result<T> {
        val playlistId = playlistRepository.activePlaylist()?.id
        val believedDenied = playlistId != null &&
            playlistRepository.activePlaylist()?.isDenied(capability) == true
        val result = runCatching { block() }
        result.onSuccess {
            if (believedDenied && playlistId != null) {
                // We were wrong, and the server just proved it.
                CapabilityCorrections.record(playlistId, capability, CapabilityState.Allowed)
                runCatching { playlistRepository.probeCapabilities(playlistId, force = true) }
                onCorrected(null)
            }
            return result
        }
        val error = result.exceptionOrNull()
        val denied = error is DispatcharrError.Forbidden ||
            error is DispatcharrError.Unauthorized
        if (!denied || playlistId == null) return result

        // Re-probe and re-derive before deciding what to tell the user.
        val probed = runCatching { playlistRepository.probeCapabilities(playlistId, force = true) }
            .getOrDefault(false)
        val fresh = playlistRepository.activePlaylist()
        val stillAllowed = probed && fresh?.allows(capability) == true &&
            fresh.capabilities().isKnownAllowed(capability)
        val message = if (stillAllowed) {
            // Permissions say yes, the server still said no.
            capability.networkRestrictionMessage()
        } else {
            CapabilityCorrections.record(playlistId, capability, CapabilityState.Denied)
            capability.deniedMessage()
        }
        CapabilityNotices.emit(
            CapabilityNotice(
                playlistId = playlistId,
                capability = capability,
                message = message,
                looksLikeNetworkRestriction = stillAllowed,
            ),
        )
        // Re-read so the affordance follows the corrected capability.
        onCorrected(message)
        return Result.failure(DispatcharrError.Forbidden(message))
    }

    /**
     * Schedules a Dispatcharr-server recording for the given channel + program.
     * Caller passes pre-rolled times (start - preRollMin, end + postRollMin
     * already applied).
     */
    suspend fun scheduleServerRecording(
        channelDispatcharrId: Int,
        startMillis: Long,
        endMillis: Long,
        title: String,
        description: String,
        comskip: Boolean,
        onCorrected: suspend (errorMessage: String?) -> Unit = {},
    ): Result<Scheduled> {
        val playlist = playlistRepository.activePlaylist()
            ?: return Result.failure(IllegalStateException("No playlist loaded."))
        if (playlist.apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Active source is not Dispatcharr-backed."))
        }
        val base = playlistRepository.effectiveBaseUrl(playlist)
        return withCapability(Capability.CanManageDvr, onCorrected) {
            val created = dispatcharrAuth.withApiKeyRetry(playlist.id) { key ->
                dispatcharrClient.createRecording(
                    baseUrl = base,
                    apiKey = key,
                    channelId = channelDispatcharrId,
                    startIso = startMillis.toIsoUtc(),
                    endIso = endMillis.toIsoUtc(),
                    title = title,
                    description = description,
                    comskip = comskip,
                )
            }
            Scheduled(created, base, playlist)
        }
    }
}
