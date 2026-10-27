package com.aeriotv.android.feature.reminders

import android.content.Context
import android.widget.Toast
import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.data.db.dao.ReminderDao
import com.aeriotv.android.core.data.db.entity.canRecordToServer
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.core.preferences.AppPreferences
import com.aeriotv.android.feature.dvr.DvrServerWriter
import com.aeriotv.android.ui.scale.ProvideAppTextScale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Thin VM exposing the app-scoped [ReminderBannerBus] to the navigation root,
 * plus the banner's Record action. Activity-scoped, so a Record started from
 * the banner finishes (and toasts) after the banner itself has closed.
 */
@HiltViewModel
class ReminderBannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bus: ReminderBannerBus,
    private val reminderDao: ReminderDao,
    private val playlistRepository: PlaylistRepository,
    private val serverWriter: DvrServerWriter,
    private val prefs: AppPreferences,
) : ViewModel() {
    val banner: StateFlow<ReminderBannerData?> = bus.banner
    fun dismiss() = bus.dismiss()

    /** Whether the active source can create Dispatcharr server recordings. */
    suspend fun canRecordToServer(): Boolean =
        playlistRepository.activePlaylist()?.canRecordToServer() == true

    /**
     * Schedules a server recording of the reminded programme, with the user's
     * default pre / post-roll and no Comskip (the Record sheet's defaults).
     * The window comes from the banner when the alarm carried it, else from
     * the reminder's Room row.
     */
    fun record(banner: ReminderBannerData, dispatcharrChannelId: Int) {
        viewModelScope.launch {
            val window = if (banner.startMillis != null && banner.endMillis != null) {
                banner.startMillis to banner.endMillis
            } else {
                reminderDao.getOnce(banner.reminderKey)?.let { it.startMillis to it.endMillis }
            }
            if (window == null) {
                toast("Couldn't find this program's time slot.")
                return@launch
            }
            val preRoll = prefs.dvrDefaultPreRollMins.first()
            val postRoll = prefs.dvrDefaultPostRollMins.first()
            val result = serverWriter.scheduleServerRecording(
                channelDispatcharrId = dispatcharrChannelId,
                startMillis = window.first - preRoll * 60_000L,
                endMillis = window.second + postRoll * 60_000L,
                title = banner.programTitle,
                description = "",
                comskip = false,
            )
            toast(
                result.fold(
                    onSuccess = { "Recording scheduled: ${banner.programTitle}" },
                    onFailure = { t -> "Recording failed: ${t.message ?: t::class.simpleName}" },
                ),
            )
        }
    }

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Seconds the banner stays up without input; the Close button counts them down. */
private const val AUTO_CLOSE_SECONDS = 5

/**
 * In-app reminder banner overlay. Mirrors iOS ReminderBannerView: when a
 * reminder fires while the app is foregrounded, a banner drops in at the top
 * of the screen with three actions:
 *  - Watch: tunes the reminded channel ([onWatch]);
 *  - Record: schedules a Dispatcharr server recording of the programme. Shown
 *    only when the active source can record to the server and the channel has
 *    a Dispatcharr id;
 *  - Close (N): closes the banner. It also counts down from
 *    [AUTO_CLOSE_SECONDS] and closes the banner by itself at zero.
 *
 * The banner is a FOCUSABLE popup window: it takes the D-pad while it is up
 * (Watch is focused), Back closes it, and closing it hands focus straight
 * back to whatever had it in the main window, because the main window's
 * focus state is untouched while the popup holds window focus.
 *
 * [findChannel] resolves the reminder's channel by id, falling back to its
 * name (alarms restored after an app update carry no channel id).
 */
@Composable
fun ReminderBannerHost(
    findChannel: (channelId: String, channelName: String) -> M3UChannel?,
    onWatch: (channelId: String) -> Unit,
    vm: ReminderBannerViewModel = hiltViewModel(),
) {
    val banner by vm.banner.collectAsStateWithLifecycle()
    // key(): a new reminder arriving while one is up restarts the countdown
    // and the focus request.
    val shown = banner ?: return
    key(shown) {
        ReminderBannerPopup(shown, findChannel, onWatch, vm)
    }
}

@Composable
private fun ReminderBannerPopup(
    banner: ReminderBannerData,
    findChannel: (channelId: String, channelName: String) -> M3UChannel?,
    onWatch: (channelId: String) -> Unit,
    vm: ReminderBannerViewModel,
) {
    val channel = remember { findChannel(banner.channelId, banner.channelName) }
    val canWatch = channel != null && channel.url.isNotBlank()
    val canRecord by produceState(false) { value = vm.canRecordToServer() }
    val recordChannelId = channel?.dispatcharrChannelId?.takeIf { canRecord }

    var secondsLeft by remember { mutableIntStateOf(AUTO_CLOSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1_000L)
            secondsLeft--
        }
        vm.dismiss()
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The popup window attaches a frame after composition; retry briefly
        // (same pattern as the update prompt).
        repeat(10) {
            if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            kotlinx.coroutines.delay(16L)
        }
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, 0),
        onDismissRequest = vm::dismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ProvideAppTextScale {
            Row(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = banner.programTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (banner.channelName.isNotBlank())
                            "Starting soon on ${banner.channelName}"
                        else
                            "Starting soon",
                        style = MaterialTheme.typography.bodySmall.subtext(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Focus lands on the first action shown: Watch, else Close.
                    if (canWatch && channel != null) {
                        Button(
                            onClick = {
                                vm.dismiss()
                                onWatch(channel.id)
                            },
                            modifier = Modifier.focusRequester(firstFocus),
                        ) { Text("Watch") }
                    }
                    if (recordChannelId != null) {
                        OutlinedButton(onClick = {
                            vm.dismiss()
                            vm.record(banner, recordChannelId)
                        }) { Text("Record") }
                    }
                    TextButton(
                        onClick = vm::dismiss,
                        modifier = if (canWatch) Modifier else Modifier.focusRequester(firstFocus),
                    ) { Text("Close ($secondsLeft)") }
                }
            }
        }
    }
}
