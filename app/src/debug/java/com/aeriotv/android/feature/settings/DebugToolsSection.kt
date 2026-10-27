package com.aeriotv.android.feature.settings

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.aeriotv.android.core.data.repository.PlaylistRepository
import com.aeriotv.android.feature.reminders.ReminderBroadcastReceiver
import com.aeriotv.android.ui.settings.SettingsNavRow
import com.aeriotv.android.ui.settings.SettingsSection
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DebugToolsEntryPoint {
    fun playlistRepository(): PlaylistRepository
}

/**
 * DEBUG BUILDS ONLY (src/debug). Release and perf compile the empty stub in
 * their own source sets, so none of this ships.
 *
 * "Test Reminder" fires a fake programme reminder 15 s after the press, to
 * see the reminder UI without waiting on a real EPG slot. It sends
 * [ReminderBroadcastReceiver] the same intent the AlarmManager alarm carries,
 * so what shows is the real receiver's choice: the in-app banner while the
 * app is in the foreground, the system notification once it is backgrounded
 * (press Home inside the 15 s to see that one).
 *
 * The reminder is on a REAL channel of the active playlist (the first with a
 * Dispatcharr id, else the first playable one), so the banner's Watch tunes it
 * and Record schedules a real 30 minute server recording ("Debug Test
 * Programme", starting 5 minutes after the banner fires; delete it from the
 * DVR tab). The window rides on the intent because there is no Room row.
 *
 * Deliberately NOT routed through RemindersViewModel.setReminder: that writes
 * a Room row, which would show as a set reminder in the guide and travel to
 * other devices through Dispatcharr Sync. A main-looper post (not a composable
 * coroutine) so the timer survives leaving Settings or the app.
 */
@Composable
internal fun DebugToolsSection() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    SettingsSection(header = "Debug Tools", footer = "Debug builds only.") {
        SettingsNavRow(
            title = "Test Reminder",
            subtitle = "Fires a reminder for a real channel in 15 seconds: a banner if the app is open, a notification if it is in the background",
            icon = Icons.Filled.Alarm,
            trailingChevron = false,
            onClick = {
                scope.launch {
                    val repo = EntryPointAccessors
                        .fromApplication(context, DebugToolsEntryPoint::class.java)
                        .playlistRepository()
                    val channels = repo.activePlaylist()?.let { repo.loadCachedChannels(it.id) }.orEmpty()
                        .filter { it.url.isNotBlank() }
                    val channel = channels.firstOrNull { it.dispatcharrChannelId != null } ?: channels.firstOrNull()
                    if (channel == null) {
                        Toast.makeText(context, "No playable channel in the active playlist", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    Toast.makeText(context, "Test reminder for ${channel.name} in 15 seconds", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        val start = System.currentTimeMillis() + 5 * 60_000L
                        context.sendBroadcast(
                            Intent(context, ReminderBroadcastReceiver::class.java).apply {
                                putExtra(ReminderBroadcastReceiver.EXTRA_REMINDER_KEY, "debug-test-reminder")
                                putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "Debug Test Programme")
                                putExtra(ReminderBroadcastReceiver.EXTRA_CHANNEL_NAME, channel.name)
                                putExtra(ReminderBroadcastReceiver.EXTRA_CHANNEL_ID, channel.id)
                                putExtra(ReminderBroadcastReceiver.EXTRA_START_MILLIS, start)
                                putExtra(ReminderBroadcastReceiver.EXTRA_END_MILLIS, start + 30 * 60_000L)
                            },
                        )
                    }, 15_000L)
                }
            },
        )
    }
}
