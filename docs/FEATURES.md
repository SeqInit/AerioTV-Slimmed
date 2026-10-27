# AerioTV Slimmed: feature map

Every user-facing feature, grouped by the subsystem it belongs to, with the
code that owns it and the other features it depends on. Paths are relative to
`app/src/main/java/com/aeriotv/android/`.

Source-type support is noted where it varies: **D** = Dispatcharr, **X** =
Xtream Codes, **M** = M3U (+ optional XMLTV).

```
Sources ─► PlaylistRepository ─► Room / DataStore
               │
     ┌─────────┼──────────────┬──────────────┐
  Live TV &  Movies & TV     DVR        Profiles / Sync
   Guide       (VOD)          │               │
     │           │            │               │
     └─────► Playback engine (one ExoPlayer) ◄┘
               │
     Fullscreen player ◄─► Mini player ◄─► Radio Mode
               │
     Live Rewind / Catch-up / Keep Recent Channels Live
```

---

## 1. Sources and data

The foundation. Every other category reads channels, EPG and VOD from here.

| Feature | Owner | Notes |
|---|---|---|
| Dispatcharr (admin login or API key) | `core/network/DispatcharrClient`, `DispatcharrAuthBroker`, `DispatcharrTokenStore`, `DispatcharrLoginGate` | Richest source: server DVR, comskip, failover, TMDB VOD, bulk EPG (`/api/epg/grid/`). |
| Xtream Codes | `core/network/XtreamCodesApi` | Live + VOD; provider-supplied metadata. |
| M3U + XMLTV | `core/network/PlaylistFetcher`, `core/parser/M3UParser`, `XMLTVParser` | Live only. |
| Central data hub | `core/data/repository/PlaylistRepository` | Owns channels, EPG and VOD per playlist; persists to Room (`core/data/db`, v37, hand-written migrations in `core/di/DatabaseModule`) and DataStore (`core/preferences/AppPreferences`). |
| Background refresh | `core/data/sync/PlaylistRefreshWorker` (WorkManager), `EpgSweepGate` | The quiet EPG sweep holds off until a tune reaches its first frame (see *Playback*). |
| Per-user capabilities | `core/data/capability/DispatcharrCapability` | Reads `/api/accounts/users/me/` `custom_properties` (DVR access, VOD, catch-up…). Gates DVR, VOD and catch-up UI. Unknown means enabled; a 403 corrects it. |
| LAN / WAN switching | `core/network/LanReachability` | HEAD probe of the per-playlist LAN URL; re-checked on network change. Live, catch-up and DVR URLs all follow the chosen host. |
| Custom User-Agent, external XMLTV override, per-playlist VOD toggle | `feature/settings/EditPlaylistScreen` → `PlaylistRepository` | User-Agent applies to API calls and playback (`core/network/PlaybackHeaders`). |
| Credential encryption | `core/security/CredentialCipher`, `core/data/db/dao/EncryptingPlaylistDao` | Decrypted off the main thread. |
| TMDB enrichment / art cache | `core/network/TMDBService`, `TmdbArtCache` | Feeds VOD detail and guide preview art. |

**Onboarding** (`feature/onboarding/`): Welcome → choose source type →
configure source → (Dispatcharr) Dispatcharr Sync step → add profiles →
setting up. Writes the first playlist through `PlaylistRepository`.

---

## 2. Live TV and Guide

Entry: the Live TV tab in the side menu (`feature/livetv/LiveTVTabContent`).

| Feature | Owner | Integration |
|---|---|---|
| EPG guide grid | `feature/livetv/grid/GuideScreen`, `GuideGrid`, `GuideGridState`, `GuideGridRows` | Canvas-drawn rows; data from `core/guide` (below). OK on a cell tunes the channel → *Player*. |
| Guide identity / merge rules | `core/guide/GuideIdentity`, `GuideMerge`, `GuideIndex`, `GuideCatalog` | Spec: `docs/guide-semantics.md` (must match Apple). One canonical id per row (`disp:<uuid>` / `m3u:<hash>`); tvg-id matching is one-to-many. |
| Sidebar guide header | `feature/livetv/grid/SidebarGuideHeader` | Shows the focused program; its preview slot is where the **mini player docks** (publishes `MiniPlayerChrome.dockRectPx`). |
| Groups column / group filtering | `feature/livetv/GroupSidebar`, `GroupTokens`, `DisplayChannels` | Filter and sort run off the main thread. |
| Manage groups (hide / reorder) | `feature/livetv/ManageGroupsSheet` | Stored in prefs; synced as App Preferences. |
| Channel Collections | `feature/collections/ChannelCollectionsUi` | User-defined named groupings shown as filter pills. |
| Favorites | `feature/favorites/` (Room `FavoriteChannelEntity`) | Favorites pill and tab; feeds the launcher's Top Channels row; synced. |
| Program Info sheet | `feature/livetv/ProgramInfoSheet`, `core/network/ProgramDetailResolver` | Entry point to **Record** and **Remind**. |
| Jump to day / now | `feature/livetv/GuideJumpSheet` | Also bound to remote actions. |
| Channel-number entry | `feature/livetv/ChannelNumberEntry` | Works in the guide and the player. |
| Category colours / EPG badges | `core/category/`, `core/ui/EpgBadges` | Shared with the player chrome and the Program Info sheet. |
| Reminders | `feature/reminders/` (Room `ReminderEntity`, AlarmManager, `ReminderBroadcastReceiver`, `ReminderBannerBus`) | Set from the guide's long-press menu or Program Info; fires as an in-app banner and a notification (`core/system/NotificationPermissionGate`). Synced. |
| Search | `feature/search/` | Searches channels, programs and VOD; results tune or open detail. |
| Catch-up entry | Guide cell on an aired program | Hands off to *Catch-up* (Playback). |

---

## 3. Playback engine (live)

One app-scoped ExoPlayer serves fullscreen, the mini player and Radio Mode, so
moving between them never re-tunes.

| Feature | Owner | Integration |
|---|---|---|
| Persistent player | `core/playback/AerioExoPlayerHolder` (`@Singleton`) | `acquireOrCreate` / `detach` / `destroy`; never released by a composable. |
| Persistent video window | `feature/player/PersistentExoWindow`, `ExoWindowState` | One `PlayerView` at the activity root; Hidden / Fullscreen / Mini only resize it. |
| Custom TS extractors | `SafeTsExtractor`, `ContinuousTsExtractor`, `UnboundedLengthDataSource` | Robust MPEG-TS (incl. OTA / HDHomeRun MPEG-2). |
| Audio fallback decoders | `AerioRenderers` + `app/libs/media3-decoder-ffmpeg.aar` | AC-3 / E-AC-3 / MP2 when the device lacks a decoder. |
| Stream failover | `LiveStreamFailover` (**D**) | Silent-start deadline walks member streams via `change_stream`. |
| Dispatcharr 503 / connection limit | `Dispatcharr503`, `DispatcharrConnectionLimit`, `Live503LoadErrorPolicy`, `feature/player/ConnectionLimitCard` | Shows the limit card; works with Keep Recent Channels Live (retained connections count). |
| Switch Stream | `feature/player/SwitchStreamReorder`, `SwitchSkipMediaSource` (**D**) | Manual stream pick from Options; keeps one connection. |
| Learned start gate | `GateSwitchingLoadControl`, `core/preferences/LiveFirstByteLearner` | Per-channel learned buffering before start. |
| Force HLS (developer) | `ForceHls` (**D**) | Requests `output_format=hls`. |
| Audio-only channels | `AudioOnlyChannelMemory`, `AudioOnlyTrackGuard`, `AudioOnlyGraceRule`, `DeclaredRadioChannels`, `feature/player/AudioOnlyArtwork` | Remembers audio-only channels so they start fast; feeds *Radio Mode*. |
| Refresh-rate / resolution matching | `feature/player/DisplayFrameRateMatcher`, `DisplayModeSwitchSignal`, `ContentFpsMeter` | Attached in `PersistentExoWindow`; handles Shield display-mode recovery. |
| Media session / notification | `AerioMediaPlaybackService` | Started with every live tune; keeps Radio Mode alive in the background. |
| Leave-app stops playback | `MainActivity.onStop` / `onUserLeaveHint` | |

### 3a. Live Rewind (timeshift)

`core/timeshift/TimeshiftController`, `TimeshiftBufferStore`, `TimeshiftDataSources`

- The holder's tee data source mirrors raw MPEG-TS bytes into the active
  writer. HLS / DASH can't be buffered, so their seek bar is grey.
- Started by fullscreen `PlayerScreen`. It **keeps rolling behind the mini
  player** and resumes on expand; a channel switch drops it.
- First-use prompt: `feature/player/LiveRewindPrompt`.
- Drives the player chrome's seek bar, skip back/forward (`core/ui/SkipIntervals`,
  `SkipIntervalsPlayer`), Restart Program and Go Live.

### 3b. Keep Recent Channels Live

Part of `TimeshiftController` (`retained` writers, `retainedWriterFor`).

- On a channel flip or close, the outgoing channel's connection and buffer
  are kept open, so flipping back plays its live edge with no new server request.
- Not applied on minimize, on HLS sessions or on failed streams.
- Indicator: `feature/livetv/RetainedChannelsIndicator`.

### 3c. Catch-up (**D**, **X**)

`core/playback/CatchupPlaybackResolver`, `CatchupUrlBuilder`, `CatchupResumeStore`

- Opened from an aired guide cell. Builds the `/timeshift/` URL with the
  right credentials and timezone, then plays in `PlayerScreen`'s catch-up mode.
- Catch-up replays never become the mini player or a recent channel, and
  Back returns to the guide.
- Gated by the `catchup_enabled` capability on Dispatcharr.

---

## 4. Player UI

| Feature | Owner | Integration |
|---|---|---|
| Fullscreen live player | `feature/player/PlayerScreen` | Hosts every live player feature; publishes channel-flip / remote hooks on `ExoWindowState`. |
| Player chrome (sidebar style) | `feature/player/SidebarPlayerChrome`, `PlayerChromeOverlay` | Seek bar (Live Rewind), transport, tiles row (guide, history, recents, options). |
| Options menu | `PlayerScreen` pills | Audio track, subtitles, aspect / video scale (`VideoScale`), sleep timer, Stream Info, Switch Stream, Record. |
| Stream Info | `ExoPlayerReaders.captureStreamInfo` | Live decoder / codec / frame-drop readout. |
| Channel flip | `MainActivity.dispatchKeyEvent` → `ExoWindowState.onLiveChannelFlip` | D-pad Up/Down beats Compose focus while chrome is hidden. |
| Last-channel zap | `ExoWindowState.recordTune` | One-deep, in memory only. |
| Recent Channels overlay | `feature/player/RecentChannelsOverlay` | Recents list; feeds the launcher row. |
| Channel list overlay | `feature/player/ChannelListOverlay` | In-player guide / list. |
| Back model | `PlayerDoubleBack` | Back hides chrome → Back minimizes → double-Back stops. |
| Loading line / screen-on | `LoadingDetailLine`, `KeepScreenOn` | |

### 4a. Mini player

`feature/miniplayer/MiniPlayerSession` (Hidden / Pending / Active),
`ExoWindowState.Mode.Mini`, `MiniPlayerChrome`

- Minimizing only resizes the persistent window; the same stream keeps playing.
  Docks into the guide header's preview slot, otherwise sits in the top-right corner.
- Slides off screen while Settings is open (`settingsStashed`, `stashFully`).
- Resume: Back, hold-Back or a remote action → `MiniPlayerSession.resumeRequests`
  → Navigation re-pushes the player route (no re-tune).
- "Play Channels In: Mini player" tunes straight into the mini (`Routes.player(…, mini = true)`).

### 4b. Radio Mode

`feature/player/RadioMode`, `RadioMenu`, `RadioTvBanner`

- Turns off the video track on the shared holder and hides the window; the
  media service keeps audio playing in the background.
- A dock card in the guide; "Return to Channel" adopts the running stream.
- Mark as Radio Channel (**D**, admin) PATCHes the channel on the server.

---

## 5. Movies and TV Shows (VOD) (**D**, **X**)

| Feature | Owner | Integration |
|---|---|---|
| VOD library tabs | `feature/movies/` (`TvMediaPage`, `TvMediaTab`, `MediaLibrary`), `feature/ondemand/OnDemandViewModel` | Catalog in `core/data/vod/VodCatalogStore` (Room `VodCatalogEntities`); full-library pagination. |
| Movie / series detail | `feature/ondemand/MovieDetailScreen`, `SeriesDetailScreen`, `VodDetailTvParts`, `PersonBioDialog` | TMDB-rich on Dispatcharr. |
| VOD versions | `VodVersionSheet`, `core/preferences/VodVersionSelectionStore`, `VodLearnedStreamStore` | Pick between quality variants; remembers the choice. |
| VOD player | `feature/player/VODPlayerScreen` | Owns its own `PlayerView` (not the persistent live window). Speed 0.5x–2x. |
| Continue Watching / auto next episode | `feature/watchprogress/` (Room `WatchProgressEntity`) | Feeds the launcher's Watch Next row; synced. |
| Watchlist | `feature/movies/WatchlistViewModel`, `core/preferences/WatchlistStore` | Synced. |
| Hidden titles | `core/preferences/HiddenTitlesStore` | |
| Library snapshot | `core/preferences/VodLibrarySnapshotStore` | Fast cold start of the tabs. |

---

## 6. DVR

Entry: the DVR tab (`feature/dvr/DvrTabContent`, `DvrMediaTabContent`, `DvrViewModel`)
and Record from the guide / Program Info / player (`feature/livetv/RecordProgramSheet`).

| Feature | Owner | Integration |
|---|---|---|
| Server-side recording (**D**) | `DispatcharrClient` recording endpoints | Runs on the server even with the app closed. Gated by the `dvr_access` capability. |
| **Comskip / Remove Commercials** (**D**, server recordings only) | `RecordProgramSheet` (toggle at schedule time), `DvrViewModel` / `DvrMediaTabContent` (run after recording), `DispatcharrClient` | Runs on the Dispatcharr server. |
| Pre-roll / post-roll | `RecordProgramSheet`, `feature/settings/DvrSettingsScreen` | Same pickers for server and local recordings. |
| Auto-discovery (**D**) | `DvrViewModel` polling, `DvrRecordingsCache` | Recordings scheduled in the Dispatcharr web UI show up in the tab. |
| Local recording (**D**, **X**, **M**) | `feature/dvr/LocalRecordingService` (foreground service; Room `LocalRecordingEntity`) | Writes the stream to app storage; keep-device-awake option. |
| Edit recording | `EditRecordingSheet` | |
| Playback / save to device | DVR tab → VOD player | Server recordings stream directly. |

---

## 7. Profiles and access

`core/profiles/` (`ProfileManager`, `ProfileRole`, `ProfilePin`, `ProfileScope`,
`DispatcharrProfile`) and `feature/settings/ProfilesUi`, `ProfileRoleUi`, `ProfilePinUi`.

- AerioTV profiles map to Dispatcharr users. Roles are **Admin / User / Child**,
  each with an optional PIN.
- Much of the data is profile-scoped (`ProfileScope`): favorites, progress,
  prefs and so on.
- Admin-only actions (Mark as Radio Channel, channel-profile assignment)
  check the role and the server capability.
- A profile switch blocks input until the new profile loads (`MainActivity`).

---

## 8. Dispatcharr Sync (**D**)

`core/sync/dsync/` (`DispatcharrSyncManager`, `DsyncSchema`, `DsyncV2`,
`DsyncSettingsValidator`), `core/sync/SyncCategory`, `feature/settings/SyncSettingsScreen`.

- Cross-device sync through the server's per-user app data. The schema,
  `docs/DispatcharrSyncSchema.md`, must stay byte-identical with the Apple app.
- Categories (`SyncCategory`): **Watch Progress, Reminders, Favorites,
  Watchlist, App Preferences**. The remote-control map is also carried.
- Only the settings in `SETTINGS_OWNED` are written; other devices' keys pass
  through unchanged.

---

## 9. TV shell and navigation

| Feature | Owner | Integration |
|---|---|---|
| Side menu shell | `feature/main/MainScaffold`, `TvSideMenu`, `AppTab`, `TabReselect` | The only interface. Tabs (`visibleTabs`): Live TV, then Favorites / DVR / Movies / TV Shows only when they have content and the user has access, then Settings. Search is a separate entry, not a tab. |
| Navigation | `Navigation.kt` (`Routes`) | bootstrap → onboarding → MAIN → player / VOD player. Deep links: `aeriotv://channel/<id>`, `aeriotv://vodplay/…`. |
| D-pad focus system | `ui/tv/` (`TvFocus`, `TvGridFocus`, `TvColdStartFocus`, `TvFocusTrace`) | |
| Remote-control map | `core/remote/RemoteControlMap`, `LearnedButtons`, `RemoteControlHints`, `feature/settings/RemoteControlSettingsScreen` | Short and long press per button → player or guide actions; dispatched from `MainActivity`; synced. |
| Launcher integration | `core/tv/HomeChannelsPublisher`, `HomeChannelsInitReceiver` | "Top Channels" row (recents + favorites) and Watch Next (VOD progress). |
| Appearance | `ui/theme/`, `ui/scale/`, `feature/settings/AppearanceSettingsScreen` | Theme, text scale, contrast. |

---

## 10. Settings, maintenance and diagnostics

| Feature | Owner |
|---|---|
| Settings panel | `feature/settings/SettingsHost`, `SettingsSidebarPanel`, per-area screens (General, Live TV, Player, Movies & TV, DVR, Remote, Sync, Playlists, Appearance, Developer) |
| Retired preferences | `RETIRED_PREF_KEYS` in `AppPreferences` |
| In-app updater | `core/update/UpdateManager`, `UpdateChecker`; `feature/update/` (enabled only on builds signed with this fork's key) |
| Logging | `core/debug/Logger` (`debugLog`), `LogSanitizer`, `feature/settings/LogViewerScreen` |
| Log share via QR | `core/debug/LogShareServer`, `feature/settings/TvLogShareDialog` |
| Crash reports / telemetry | `core/debug/CrashReporter`, `ResourceTelemetry`, `MemoryPressureBus` |
| Licenses | `feature/settings/LicensesScreen` |

---

## Cross-cutting dependencies at a glance

| If you change… | …check these |
|---|---|
| `AerioExoPlayerHolder` | Player, mini player, Radio Mode, Live Rewind, Keep Recent Channels Live, failover, media service |
| `TimeshiftController` | Live Rewind, Keep Recent Channels Live, connection-limit handling, mini-player minimize |
| `core/guide` | Guide grid, favorites/collections matching, search, Program Info, reminders, launcher row |
| `DispatcharrCapability` | DVR, comskip, VOD tabs, catch-up, admin-only actions |
| Profiles / `ProfileScope` | Favorites, watch progress, prefs, Dispatcharr Sync |
| A synced setting | `SETTINGS_OWNED`, `DsyncSettingsValidator`, `docs/DispatcharrSyncSchema.md` (Apple parity) |
| A new Dispatcharr endpoint | README's API path table (reverse-proxy rules) |
