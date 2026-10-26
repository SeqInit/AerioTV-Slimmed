# AerioTV Slimmed

AerioTV Slimmed is a TV-only fork of [AerioTV for Android](https://github.com/jonzey231/AerioTV-Android): a native IPTV client for Google TV / Android TV, built with Jetpack Compose. It connects to Dispatcharr (admin login or API key), Xtream Codes, and M3U playlist servers for live TV, movies, and series, with a full electronic program guide (EPG) when the source supplies one.

The fork keeps the TV experience and removes everything else:

- **TV only.** The phone, tablet and foldable layouts, touch gestures and Picture-in-Picture are gone; the manifest requires `android.software.leanback`.
- **One TV interface.** The sidebar layout (side menu, Settings as a panel, guide with a groups column) is the only shell.
- **Removed features:** Google Cast and the phone-as-remote companion, Multiview, Android Auto, Google Drive sync, and the What's New screen. Dispatcharr Sync remains the cross-device sync.
- **Single sideload build** with its own application id (`com.seqinit.aeriotv`), so it installs beside upstream AerioTV. The in-app updater follows this repository's releases.

## Install

Download the latest `.apk` from [Releases](https://github.com/SeqInit/AerioTV-Slimmed/releases) and sideload it (for example with `adb install`, or a file manager with "install unknown apps" allowed). The in-app updater only enables itself on builds signed with this fork's release key.

## Features

<details>
<summary><strong>Live TV & Guide</strong> Media3 / ExoPlayer playback, EPG grid, reminders.</summary>

- Hardware-accelerated playback on Media3 / ExoPlayer (HLS, DASH, and progressive streams; hardware decode including HEVC HDR)
- A full EPG guide grid with program titles, descriptions, and time slots
- Channel info card identifies the channel and program when a stream starts
- Sort channels by number, name, or favorites; reorder favorites
- Manage favorites and per-group visibility from the channel menu
- Long-press an upcoming program to set a reminder or schedule a recording
- EPG reminders fire before the program starts, as an in-app banner and a notification
- EPG window configurable from 6 hours up to the full available window (Settings, Network)
- Guide display scale and a jump-to-now control
- Channels without guide data are still selectable from the grid
- Per-playlist Refresh EPG

</details>

<details>
<summary><strong>DVR Recording</strong> server-side on Dispatcharr, local on every server type.</summary>

- Schedule live or upcoming programs from the guide
- **Dispatcharr server-side recording** continues even when AerioTV is closed (the server runs the recording), ideal for unattended recordings
- **Local recording on this device**, available for Dispatcharr, Xtream Codes, and M3U
- Per-recording pre-roll (start early) and post-roll (end late) buffers: None / 5 / 10 / 15 / 30 / 60 min or custom
- **Remove Commercials (Comskip)** runs server-side on Dispatcharr; toggle it at schedule time or run it after recording
- Auto-discovery of recordings scheduled outside the app (Dispatcharr web UI)
- Play completed recordings in the app, and save server-side recordings to the device
- Keep-device-awake toggle so a local recording is not interrupted by display sleep

</details>

<details>
<summary><strong>Movies & TV Shows</strong> (Dispatcharr & Xtream Codes) VOD library, Continue Watching, TMDB-rich metadata.</summary>

- Browse and filter on-demand content by category
- TMDB-rich metadata on Dispatcharr (backdrop, plot, cast, director, year, rating, runtime; per-episode air dates, ratings, IMDB IDs, and per-episode artwork)
- Provider-supplied metadata on Xtream Codes (poster, title, one-line plot)
- Continue Watching resumes movies and episodes, and advances automatically to the next episode in a series
- Per-playlist VOD toggle in Settings, Edit Playlist
- Full-library pagination walks past the first page on large libraries

</details>

<details>
<summary><strong>Player</strong> Options menu, Stream Info, Sleep Timer, refresh-rate matching, channel-flip.</summary>

- Options pills for audio track, subtitles, speed, sleep timer, aspect ratio and stream info
- Playback speed (0.5x to 2x) for on-demand content; not available on live streams
- Audio Only mode and Radio Mode
- **Stream Info overlay** reads codec, resolution, frame rate, decoder (hardware or software), dropped frames, and audio details live from the player, so you see what the device is actually decoding rather than what the upstream server reports
- **Channel-flip** with D-pad up or down during live playback, plus direct channel-number entry
- Catch-up and live rewind (timeshift)
- Display refresh-rate and resolution matching for smooth live playback (for example, 50fps content on a 60Hz panel)
- Leaving the app stops playback, as on Apple TV

</details>

<details>
<summary><strong>Android TV</strong> D-pad focus model, mini-player, launcher integration.</summary>

- Full D-pad navigation with a tvOS-style focus model (focus cards and focus scaling)
- A corner mini-player keeps the current stream alive while you browse the guide or settings
- A configurable remote-control button map
- Home-screen channel row and Watch Next on the Android TV launcher
- Profiles mapped to Dispatcharr users, with roles and PINs
- LAN / WAN switching: a per-playlist LAN URL is used when the server is reachable on the local network

</details>

## Supported Server Types

AerioTV connects to three different playlist types. Each unlocks a different set of features. If you are choosing between them, the short version is below.

Click each type for the full breakdown:

<details>
<summary><strong>Dispatcharr Direct Connect</strong> <em>(Recommended)</em> admin username and password, or an API key. <em>Most full-featured option.</em></summary>

[Dispatcharr](https://github.com/Dispatcharr/Dispatcharr) is a self-hosted IPTV middleware that gives users control over their IPTV services. See the linked GitHub.

**What Dispatcharr is uniquely good at**

- **Server-side DVR**: schedule recordings that keep running even when AerioTV is closed or the device is asleep. (Local recording to the device works on every server type, but Dispatcharr is the only one where the server itself runs the recording.)
- **Comskip (commercial-skip)**: toggle it when scheduling a server-side recording, or run it after the fact from the DVR tab. Comskip runs on the Dispatcharr server, so it is only available for server-side recordings.
- **Server-side recording playback**: stream completed server recordings directly, no auth headers needed.
- **Server-side stream failover via channel UUID**: when a primary stream dies mid-playback, Dispatcharr swaps to a backup provider transparently.
- **TMDB-enriched VOD metadata**: backdrops, plots, cast, director, year, rating, runtime, plus per-episode air dates, TMDB ratings, IMDB IDs, and per-episode artwork, pulled in by Dispatcharr's TMDB scraper. (Xtream Codes also has VOD, but its metadata is whatever the provider supplies, usually poster, title, and a one-line plot.)
- **Bulk EPG fetch**: one network call returns the grid for every channel via `/api/epg/grid/`. Xtream Codes uses per-stream EPG calls; M3U pulls XMLTV from a separate URL.
- **Per-server custom User-Agent override**: set it in Settings, Edit Playlist. It is sent on Dispatcharr API requests and on playback, so the value shows up in Dispatcharr's admin Stats panel. Not currently exposed for Xtream Codes or M3U.
- **External XMLTV URL override** <em>(advanced)</em>: point the EPG at a third-party XMLTV source while keeping channels from Dispatcharr's API.
- **Auto-discovery of recordings scheduled outside the app**: recordings you scheduled from the Dispatcharr web UI appear in AerioTV's DVR tab within a couple of minutes. Xtream Codes and M3U have no server-side scheduler to discover from.
- **Dispatcharr Sync**: settings, favorites and watch progress sync between AerioTV devices through the server's per-user app data.

</details>

<details>
<summary><strong>Xtream Codes</strong> source URL plus username and password. <em>No self-hosting required.</em></summary>

The Xtream Codes API is what most IPTV providers natively expose. You log in with the username and password your provider gave you, and AerioTV pulls live TV, movies, and series.

**Benefits**

- **No self-hosting.** Most IPTV providers expose an Xtream Codes endpoint by default. Paste the URL, type your credentials, done.
- **Live TV plus a VOD library.** Movies and series come from the same login as live channels, with no separate EPG or VOD URLs to manage.
- **Per-playlist VOD toggle.** Settings, Edit Playlist exposes the same "fetch VOD from this playlist" switch Dispatcharr has, so you can keep a secondary playlist's Live TV without loading its VOD library every launch.
- **Local DVR with pre-roll and post-roll.** Recordings on this device, with the same buffer pickers (None / 5 / 10 / 15 / 30 / 60 min or custom) Dispatcharr server-side recordings get. Recordings continue while the app stays running.
- **EPG via TVG ID matching.** Most provider EPGs key to Xtream's `tvg_id` field automatically. You can also point an external XMLTV URL at it.
- **Familiar auth model.** A single username and password.

**Drawbacks**

- **VOD metadata is provider-supplied, not TMDB-enriched.** You typically get the poster, title, and a one-line plot. Per-episode air dates, TMDB ratings, and per-episode artwork are not available.
- **No server-side DVR.** Recordings are local only, so AerioTV has to keep running for the recording duration. No comskip (Dispatcharr only).
- **EPG depth varies by provider.** Some give you a few days, some give you a few hours.

</details>

<details>
<summary><strong>M3U Playlist</strong> direct URL plus an optional XMLTV EPG. <em>Universal compatibility, simplest setup.</em></summary>

A plain M3U playlist is just a list of stream URLs in a text file. It works with any IPTV provider that gives you a playlist URL, including providers that do not support the Xtream Codes API.

**Benefits**

- **Universal compatibility.** If a provider hands you any URL ending in `.m3u` or `.m3u8`, AerioTV can play it.
- **Fastest setup.** Paste the URL and you are done. No credentials, no server, no scrape time.
- **No self-hosting.**
- **Local DVR with pre-roll and post-roll.** Recordings on this device, with the same buffer pickers Xtream Codes and Dispatcharr recordings get. Recordings continue while the app stays running.
- **Optional separate XMLTV URL** for EPG. Bring your own EPG source if your provider does not include one.

**Drawbacks**

- **No VOD library.** M3U is live TV only, no movies or series. If your provider has VOD, you will need their Xtream Codes endpoint (or a Dispatcharr instance pointed at them) to access it.
- **No server-side DVR.** Recordings are local only, so AerioTV has to be running for the recording to capture. No comskip (Dispatcharr only).
- **EPG depends on a separate XMLTV source.** You set the URL yourself, and EPG quality is whatever that source provides.
- **No stream failover.** When a stream URL stops working, the provider has to fix the playlist. There is no server-side swap like Dispatcharr has.
- **Large M3U files can be slow to parse** on first launch. A playlist with tens of thousands of channels takes a noticeable beat to ingest.

</details>

## Networking

AerioTV talks to your server directly from the device. If the server sits
behind a reverse proxy with single sign-on (SSO), the app's requests have no
browser session, so the paths it uses need to pass through to the server's
own authentication instead. Each bypassed path is still guarded by the
server: the Dispatcharr API by an API key or token, the Xtream Codes paths
by the account's username and password, and the stream proxy by the stream
UUID in the URL.

<details>
<summary><strong>Pangolin access rules for Dispatcharr</strong> (click to expand)</summary>

Add these rules to the Dispatcharr resource in this order. The three
"Pass to Auth" rules keep the API documentation behind SSO; the rest let
AerioTV reach the server. Rules 6 through 11 are only needed when a
Dispatcharr user connects through an Xtream Codes playlist instead of
Direct Connect. Direct Connect never touches `/output/`, so there is no
rule for it; see the note below the table.

| # | Action | Match | Path | Needed for |
|---|--------|-------|------|------------|
| 1 | Pass to Auth | Path | `/api/swagger/*` | keeps the API docs behind SSO |
| 2 | Pass to Auth | Path | `/api/redoc/*` | keeps the API docs behind SSO |
| 3 | Pass to Auth | Path | `/api/schema/*` | keeps the API docs behind SSO |
| 4 | Bypass Auth | Path | `/api/*` | channels, EPG, VOD, recordings, logos, native Catch-Up sessions, Mark as Radio Channel (admin only: `PATCH /api/channels/channels/<id>/`), Channel Profiles for a profile (admin only: `PATCH /api/accounts/users/<id>/`) |
| 5 | Bypass Auth | Path | `/proxy/*` | live streams, VOD streams, Catch-Up playback, stream switching |
| 6 | Bypass Auth | Path | `/player_api.php` | Xtream Codes only |
| 7 | Bypass Auth | Path | `/xmltv.php` | Xtream Codes only |
| 8 | Bypass Auth | Path | `/get.php` | Xtream Codes only |
| 9 | Bypass Auth | Path | `/live/*` | Xtream Codes only |
| 10 | Bypass Auth | Path | `/movie/*` | Xtream Codes only |
| 11 | Bypass Auth | Path | `/series/*` | Xtream Codes only |
| 12 | Bypass Auth | Path | `/timeshift/*` | Xtream Codes Catch-Up; also the Direct Connect Catch-Up fallback on Dispatcharr builds without native catch-up sessions |

Anyone who knows a stream UUID can play that stream through rule 5, which
is how Dispatcharr's proxy works with or without a reverse proxy. A geo or
IP rule on the resource narrows that further if you want it.

`/output/*` (the M3U playlist and XMLTV export) is deliberately not in the
list. Direct Connect reads channels and the guide through `/api/*`, so
bypassing SSO there would only publish an unauthenticated playlist. Add a
`/output/*` bypass only if you point an M3U playlist at Dispatcharr, and
then limit it with Dispatcharr's own network access settings.

</details>

<details>
<summary><strong>Every Dispatcharr API path AerioTV uses</strong> (for a narrower set of rules than <code>/api/*</code>)</summary>

If you would rather not bypass SSO for all of `/api/*`, these are the paths
the app requests, taken from the code of both AerioTV platforms. `<id>` is a
numeric id or session token; reverse proxies usually need a wildcard there.

| # | Area | Path | Used for |
|---|------|------|----------|
| 1 | Login and account | `/api/accounts/token/` | username and password login, returns the JWT pair |
| 2 | Login and account | `/api/accounts/token/refresh/` | renews the access token so you stay signed in |
| 3 | Login and account | `/api/accounts/users/me/` | your user record, including the API key and the Catch-Up (XC) password |
| 4 | Login and account | `/api/accounts/users/` | admin check; a 2xx here proves the account is an admin (unlocks Switch Stream and server-side features); the user list for Add Profile and Channel Profiles (admin only) |
| 5 | Login and account | `/api/accounts/users/<id>/` | `PATCH` with `channel_profiles` to assign a profile's Channel Profiles (admin only) |
| 6 | Login and account | `/api/accounts/users/me/app-data/` | Lists AerioTV's synced data keys and sizes for the signed-in profile (Dispatcharr Sync) |
| 7 | Login and account | `/api/accounts/users/me/app-data/<key>/` | Reads, saves, and clears one Dispatcharr Sync category (keys start with `app.molinete.aerio.`) |
| 8 | Server | `/api/core/version/` | server version, also the connectivity and auth test at setup |
| 9 | Server | `/api/core/settings/` | server-wide switches such as whether Catch-Up is enabled |
| 10 | Channels and guide | `/api/channels/channels/` | the channel list for the playlist |
| 11 | Channels and guide | `/api/channels/channels/<id>/` | one channel's details and stream order (Switch Stream reorder); `PATCH` with `is_radio` for Mark as Radio Channel and Unmark Radio Channel (admin only) |
| 12 | Channels and guide | `/api/channels/channels/<id>/streams/` | a channel's member streams for Switch Stream |
| 13 | Channels and guide | `/api/channels/groups/` | channel groups for the group pills and sidebar |
| 14 | Channels and guide | `/api/channels/profiles/` | channel profiles for the Edit Playlist picker and the admin Channel Profiles assignment |
| 15 | Channels and guide | `/api/channels/profiles/<id>/` | the channels inside the chosen profile |
| 16 | Channels and guide | `/api/channels/logos/<id>/cache/` | channel logo images |
| 17 | Channels and guide | `/api/channels/summary/` | lightweight counts used to verify the connection |
| 18 | Channels and guide | `/api/epg/grid/` | the bulk EPG window (about -1 h to +24 h) that fills the guide |
| 19 | Channels and guide | `/api/epg/programs/` | program search and per-channel program lists beyond the grid window |
| 20 | Channels and guide | `/api/epg/programs/<id>/` | one program's full detail (categories, rating) for tinted cards and the info sheet |
| 21 | Channels and guide | `/api/epg/current-programs/` | what is airing now on a set of channels (Live TV cards, Top Shelf) |
| 22 | Channels and guide | `/api/epg/epgdata/` | the EPG channel rows that map tvg ids to EPG sources |
| 23 | Channels and guide | `/api/epg/sources/` | the server's EPG sources, layered onto the guide |
| 24 | Channels and guide | `/api/m3u/accounts/` | source account names shown next to streams and in the VOD version picker |
| 25 | DVR | `/api/channels/recordings/` | list and schedule recordings |
| 26 | DVR | `/api/channels/recordings/<id>/` | one recording's details, edit and delete |
| 27 | DVR | `/api/channels/recordings/<id>/file/` | plays a completed recording |
| 28 | DVR | `/api/channels/recordings/<id>/stop/` | stops a recording in progress |
| 29 | DVR | `/api/channels/recordings/<id>/comskip/` | runs commercial removal on a recording |
| 30 | DVR | `/api/channels/series-rules/` | series recording rules |
| 31 | DVR | `/api/channels/series-rules/evaluate/` | schedules the episodes a new series rule matches |
| 32 | VOD | `/api/vod/categories/` | movie and series categories |
| 33 | VOD | `/api/vod/movies/` | movie library pages, search and Recently Added |
| 34 | VOD | `/api/vod/movies/<id>/providers/` | the copies of a movie across source accounts (version picker) |
| 35 | VOD | `/api/vod/movies/<id>/provider-info/` | TMDB-enriched detail for a movie |
| 36 | VOD | `/api/vod/series/` | series library pages, search and Recently Added |
| 37 | VOD | `/api/vod/series/<id>/` | one series by id, for Continue Watching and Watchlist rows |
| 38 | VOD | `/api/vod/series/<id>/providers/` | the copies of a series across source accounts |
| 39 | VOD | `/api/vod/series/<id>/provider-info/` | TMDB-enriched detail for a series |
| 40 | VOD | `/api/vod/episodes/` | the episodes of a series |
| 41 | Playback sessions | `/api/proxy/hls/sessions/` | opens a Force HLS session for a channel |
| 42 | Playback sessions | `/api/proxy/hls/sessions/<token>/` | closes that session when playback stops |
| 43 | Playback sessions | `/api/catchup/sessions/` | opens a native Catch-Up session for a past program |
| 44 | Playback sessions | `/api/catchup/sessions/<id>/` | closes the Catch-Up session |
| 45 | Playback sessions | `/api/catchup/sessions/<id>/position/` | reports the playback position so the session can be resumed |

**Collapsed to prefixes (10 rules cover all 45 paths):**
`/api/accounts/*`, `/api/core/*`, `/api/channels/*`, `/api/epg/*`, `/api/m3u/*`,
`/api/vod/*`, `/api/proxy/*`, `/api/catchup/*`, plus `/proxy/*` and
`/timeshift/*`.

New Dispatcharr features will add paths over time, which is the trade against
the single `/api/*` rule.

</details>

## OTA / HDHomeRun Channels (MPEG-2)

Over-the-air broadcast channels (ATSC tuners such as HDHomeRun) carry
MPEG-2 video. Android TV boxes generally include an MPEG-2 hardware
decoder, but not all do, so these channels may fail to play on some
boxes.

For Dispatcharr users the fix is a one-time server-side transcode:
create an ffmpeg [Stream Profile](https://dispatcharr.github.io/Dispatcharr-Docs/system/?h=stream+profiles#stream-profiles)
and assign it to your OTA channels. Command: `ffmpeg`. Parameters
(paste exactly this, and do NOT include the word `ffmpeg` at the start
of the parameters field):

```
-fflags +discardcorrupt+genpts -user_agent {userAgent} -i {streamUrl} -map 0:v:0 -map 0:a? -vf yadif -c:v libx264 -preset veryfast -tune zerolatency -crf 21 -maxrate 12M -bufsize 24M -g 60 -keyint_min 60 -sc_threshold 0 -c:a ac3 -mpegts_flags +pat_pmt_at_frames+resend_headers+initial_discontinuity -f mpegts pipe:1
```

This deinterlaces the broadcast and re-encodes it to H.264 with AC-3
audio, which every device plays natively. Expect roughly one CPU core
per concurrently watched OTA channel; if your server has a hardware
encoder, substitute `-c:v h264_qsv` or `-c:v h264_nvenc` for
`-c:v libx264 -crf 21`. Remember to assign the profile to the OTA
channels themselves: editing a profile does not move any channel onto
it.

If your HDHomeRun is a transcode-capable model (EXTEND class), enabling
its built-in H.264 transcoder is an alternative that needs no server
CPU at all.

## Tech Stack

- Kotlin 2.3 + Jetpack Compose (Material 3), with D-pad focus handling for Google TV / Android TV
- Media3 / ExoPlayer for playback (HLS, DASH, progressive; hardware decode including HEVC HDR), plus a bundled FFmpeg audio decoder
- Room (relational data) + DataStore (preferences)
- Ktor + kotlinx.serialization (Dispatcharr, Xtream Codes, M3U, XMLTV)
- Hilt (DI), Coroutines + Flow (async), Navigation Compose (routing)
- Coil (image loading)

## Development

Requirements: Android Studio (latest stable), JDK 17 or newer, Android SDK 36 (compile and target), minSdk 26 (Android 8.0). Test on an Android TV device or the Android TV emulator image.

```
git clone https://github.com/SeqInit/AerioTV-Slimmed.git
cd AerioTV-Slimmed
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:assemblePerf           # release settings, debug key: for on-device perf measurements
./gradlew :app:assembleRelease        # release APK (R8 + resource shrinking)
```

Release signing reads a gitignored `keystore.properties`; without it the release APK is unsigned.

### Project Structure

```
app/src/main/java/com/aeriotv/android/
    MainActivity.kt, Navigation.kt, AerioTVApplication.kt
    core/
        network/        Ktor clients (Dispatcharr, Xtream Codes, M3U)
        parser/         M3U + XMLTV parsing
        data/           Room database, DAOs, PlaylistRepository
        guide/          guide identity and merge rules
        playback/       ExoPlayer holder, tuning, refresh-rate matching
        timeshift/      live rewind and kept channels
        preferences/    DataStore settings
        sync/           Dispatcharr Sync
        profiles/       AerioTV profiles mapped to Dispatcharr users
        update/         in-app updater (GitHub releases)
        tv/             launcher home channels and Watch Next
        app/  category/  debug/  di/  pip/  remote/  security/  system/  ui/
    feature/
        livetv/  player/  dvr/  ondemand/  movies/  channels/  collections/
        onboarding/  settings/  miniplayer/  splash/  favorites/  search/
        reminders/  watchprogress/  playlist/  update/  main/
    ui/
        adaptive/  scale/  search/  settings/  textfield/  theme/  tv/
```

## License

Copyright (C) 2026 Logan Jones

AerioTV for Android is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any
later version. See [LICENSE](LICENSE). This fork is distributed under the same
license.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

Upstream grants an additional permission under GPL section 7 for linking with
the proprietary Google Play services client libraries; see
[LICENSE-EXCEPTIONS.md](LICENSE-EXCEPTIONS.md). This fork links none of them.

Releases up to and including v0.4.20 were published under the MIT License. That
grant is not revoked: any copy obtained under MIT keeps its MIT rights to that
snapshot in perpetuity. Everything from the relicensing commit forward is
GPL-3.0-or-later.

### Third-party components

AerioTV bundles an FFmpeg build (LGPL-2.1-or-later) and depends on a number of
Apache-2.0 libraries. The FFmpeg build is configured without `--enable-gpl` and
enables exactly ten audio decoders: `aac`, `ac3`, `eac3`, `dca`, `truehd`,
`mlp`, `mp2`, `mp3`, `flac` and `alac`. Anything outside that set relies on the
device's own hardware decoder. See
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for the full list, the exact
FFmpeg build configuration, and where to obtain the corresponding source. The
same information is in the app under Settings > About > Open Source Licenses.

The Apache-2.0 components include AndroidX and Jetpack Compose, Media3 /
ExoPlayer, Kotlin with kotlinx.coroutines and kotlinx.serialization, Ktor,
Dagger and Hilt, OkHttp, Okio (Square), Coil, ZXing Core and Reorderable. They
are used under the Apache License, Version 2.0, whose full text is at
https://www.apache.org/licenses/LICENSE-2.0 and is also bundled in the app.

FFmpeg is loaded as a dynamically linked JNI shared object (`libffmpegJNI.so`)
inside `app/libs/media3-decoder-ffmpeg.aar`, and can be replaced with a modified
build, as section 6 of the LGPL requires. Build steps are in
[app/libs/README.md](app/libs/README.md). To receive the corresponding FFmpeg
source directly, open an issue at
[github.com/SeqInit/AerioTV-Slimmed/issues](https://github.com/SeqInit/AerioTV-Slimmed/issues).

### Patents

AerioTV includes independent implementations of audio codecs, supplied by
FFmpeg, that may be covered by patents in some countries. AerioTV is free
software under GPL-3.0-or-later and grants no patent license of any kind; users
and redistributors are responsible for compliance with the patent law that
applies where they live.

Video decoding uses only the codecs provided by the operating system
(MediaCodec), which are licensed by Google or the device manufacturer for use
on those devices; AerioTV grants no additional patent license for H.264/AVC or
HEVC/H.265 decoding.

Dolby, DTS and other names that appear in this project are trademarks of their
respective owners, used only to identify the audio formats concerned. AerioTV is
not affiliated with, endorsed by, or certified by any of them.

### TMDB attribution

This product uses the TMDB API but is not endorsed or certified by TMDB.

TMDB data and images are used only after a TMDB API key is configured in
Settings > App Behaviors.

## Support

This is an unofficial fork. Report issues with it at
[github.com/SeqInit/AerioTV-Slimmed/issues](https://github.com/SeqInit/AerioTV-Slimmed/issues),
not to upstream AerioTV.
