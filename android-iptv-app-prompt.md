# Android App Build Prompt — "TUNER" IPTV Player

Paste everything below into your coding tool of choice (Claude Code, Android Studio's Gemini, Cursor, etc.) as the project brief.

---

## Goal

Build a native Android app called **TUNER** that streams free-to-air IPTV channels sourced from the `iptv-org/iptv` public playlist repository. It should port the functionality and visual identity of an existing web prototype (described in full below) to a native Kotlin/Jetpack Compose app.

## Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3 as a base, fully re-themed — see Design section)
- **Architecture**: MVVM — `ViewModel` + `StateFlow`, single-activity, Repository pattern
- **Networking**: Retrofit or plain OkHttp (playlists are static `.m3u` text files over HTTPS — no auth)
- **Video playback**: **Media3 ExoPlayer** (`androidx.media3:media3-exoplayer-hls`) — native HLS support, no need for an hls.js equivalent
- **DI**: Hilt (or manual DI if you want to keep it lightweight — either is fine)
- **Min SDK**: 24 (Android 7.0) — target latest stable
- **Persistence**: DataStore to remember the last-active source mode and filter text across app restarts, plus a full persisted library of saved custom sources (label + URL pairs) — see Features below

## Data source

All data comes from static playlist files hosted at:
```
https://iptv-org.github.io/iptv/index.m3u                      (full index — large, thousands of channels)
https://iptv-org.github.io/iptv/countries/<code>.m3u           (e.g. us, uk, ca, de, fr, in, ae, br, jp, au)
https://iptv-org.github.io/iptv/categories/<name>.m3u          (e.g. news, sports, movies, music, kids, documentary...)
```
Full category list to support: animation, auto, business, classic, comedy, cooking, culture, documentary, education, entertainment, family, general, kids, legal, lifestyle, movies, music, news, outdoor, relax, religious, science, series, shop, sports, travel, weather.

These are plain-text M3U playlists — GitHub Pages serves them with permissive CORS/no-auth, so a direct OkHttp GET is enough; no proxy or backend is required for playlist fetching (only potentially for individual stream playback — see Error Handling).

### M3U parsing

Each channel entry looks like:
```
#EXTINF:-1 tvg-id="..." tvg-logo="https://..." group-title="News",Channel Name
https://actual-stream-url.example.com/stream.m3u8
```
Parse into a data class:
```kotlin
data class Channel(
    val name: String,
    val logoUrl: String?,
    val group: String,       // group-title, used for category headers within a list
    val streamUrl: String,
    val source: String       // which saved source this channel came from — the source's
                              // label (see "Custom source library" below), or "Region: US" /
                              // "Category: News" for the built-in playlists. Shown in the UI
                              // so channels stay identifiable once multiple sources are merged.
)
```
Write a small parser (regex or line-by-line state machine) — no external M3U library is required, but `iptv-playlist-parser`-style logic is fine if you prefer a dependency.

## Features (port from the web prototype)

1. **Region selector** — dropdown/menu of countries (US, UK, Canada, Germany, France, India, UAE, Brazil, Japan, Australia) plus an **"All channels"** option that loads the full `index.m3u`. Selecting a region loads that playlist and clears any active category selection.
2. **Category selector** — separate dropdown/menu of the ~27 global categories listed above. Selecting a category loads that playlist and switches out of Region/Custom-Sources mode. Region, Category, and Custom Sources are mutually exclusive modes — only one is active at a time.
3. **Custom source library (multiple saved URLs, loaded together)** — a management screen/dialog where the user can add, edit, and remove any number of custom playlist/stream URLs, each saved with a **Source** name (a short label the user gives it, e.g. "My Provider" or "Office IPTV" — auto-suggest the URL's hostname as a default if left blank). This is its own selectable mode, separate from Region and Category (mutually exclusive: choosing Region or Category clears the active custom-source mode and vice versa).
   - When "Custom Sources" mode is active, the app fetches **every saved URL in parallel** and merges the results into a single combined channel list — the user does not pick one saved URL at a time, they all load together.
   - Handle whatever each URL points to independently: an M3U/M3U8 playlist (multiple channels via `#EXTINF` entries) parses normally; a single direct stream URL with no `#EXTINF` metadata becomes one playable channel named after its Source label.
   - Every channel in the merged list carries its originating **Source** label (from the `Channel.source` field) — surface this in the channel row (small tag/pill next to the group name) so channels from different saved URLs stay distinguishable, especially if two sources happen to share channel names.
   - Support filtering/grouping by Source in addition to by group — e.g. an optional "group by Source" toggle alongside the default group-by-category view.
   - **Partial failure handling**: if one saved URL fails to fetch or parse, don't block the rest — load and show channels from every URL that succeeded, and surface a status like "3 of 4 sources loaded" plus which one failed and why (bad URL, network error, empty/unparseable response).
   - Validate each URL is well-formed before saving; on failed fetch, keep the failed entry in the saved library (don't auto-delete it) so the user can retry or fix it later, and offer a manual "retry this source" action per entry.
   - Persist the full library of saved sources (label + URL, ordered by date added) — not just a recency history — so previously added sources are still there and still load next time the app opens.
4. **Search/filter** — a text field that filters the currently loaded channel list by name, case-insensitive, substring match. **Critical**: the filter text must persist across Region/Category/Custom-Sources switches — i.e., if the user has typed "news" and then changes source mode, the newly loaded (or re-merged) channel list is immediately re-filtered by "news" rather than resetting to the full list.
5. **Channel list** — grouped by `group-title`, sorted alphabetically by group, with a numbered index per channel (001, 002...) in a teletext-style row. Tapping a channel starts playback.
6. **Player screen/panel** — Media3 `PlayerView` showing the live HLS stream, with a "now playing" header showing channel name, group, and live/error state. Support both a compact in-list player (tablet/landscape: side-by-side list + player) and a full-screen player mode (phone/portrait: tap channel → full-screen player with a back/collapse control).
7. **Error handling & retry**:
   - Every channel stays in the list regardless of whether its stream is reachable — **never filter out or hide channels based on stream health**. This includes channels known or expected to be geo-restricted or offline; the point of the app is to expose the entire catalog and let playback attempts succeed or fail per-stream.
   - On playback failure, attempt **one silent automatic retry** after a short delay (~600ms) before showing an error state — many failures are slow-loading manifests, not truly dead streams.
   - On repeated failure, show a "signal lost" state with **two manual actions**: "Retry" (retry the direct URL again) and "Retry via proxy" (retry the same stream routed through a CORS/relay proxy, for cases where the failure is a missing-header/network quirk rather than true geo-blocking). Label this honestly: this is best-effort and will **not** reliably defeat IP-based geo-restriction, since that's enforced server-side by the broadcaster; say so directly in the UI copy, don't oversell it.
   - Never silently drop a channel from the list because a previous playback attempt failed.
8. **Persistence**: remember the last-active source mode (Region/Category/Custom Sources), the full custom-source library (label + URL for every saved entry, not just recents), and the last filter text between app launches (DataStore).

## Design language — port the "teletext/CRT tuner" aesthetic

The web prototype's visual identity should carry over, adapted to Material 3 theming (not templated Material defaults):

- **Palette** (dark theme only, no light mode needed):
  - Background: `#0A0A0C`
  - Panel/surface: `#111114`
  - Divider/outline: `#2A2A2F`
  - Primary text: `#E8E6E1`
  - Secondary/dim text: `#7A7A80`
  - Accent (amber, primary interactive color — selected channel, active states): `#FFB000`
  - Secondary accent (cyan, "live" indicator): `#4FD8E0`
  - Error/offline (red): `#FF4D3D`
- **Typography**: a single monospace family throughout (e.g. bundle **IBM Plex Mono**, or fall back to the system monospace) — this is a deliberate choice evoking teletext/EPG data readouts, not a default Material sans. Channel numbers, group headings, and metadata should lean into the monospace/data-readout feel.
- **Channel rows**: numbered index + small "dot" status indicator + channel name, teletext-row style (flat rows with hairline dividers, no cards, no rounded corners, no drop shadows).
- **Active/selected channel**: amber left border accent + tinted background, not a generic Material ripple-only state.
- **Player screen**: near-black frame, minimal chrome, error states rendered as a red-tinted "static" overlay with retry actions — avoid generic Material error snackbars/dialogs for this; keep it in-frame and diegetic to the "TV" metaphor.
- Avoid generic Material defaults: no default purple/teal Material You seed colors, no elevated card stacks, no all-caps labels beyond the group headings (which intentionally mimic EPG category headers).

## Suggested package structure

```
com.example.tuner/
  data/
    model/Channel.kt
    model/CustomSource.kt          // data class: label, url, dateAdded, lastLoadStatus
    remote/PlaylistApi.kt          // Retrofit interface or OkHttp wrapper
    parser/M3UParser.kt
    repository/ChannelRepository.kt
    repository/CustomSourceRepository.kt   // CRUD + persistence for the saved-source library
  domain/
    PlaylistSource.kt              // sealed class: Region(path) / Category(path) / CustomSources(list<CustomSource>)
  ui/
    theme/                         // Color.kt, Type.kt, Theme.kt — teletext theme tokens
    channels/ChannelListScreen.kt
    channels/ChannelListViewModel.kt
    sources/CustomSourceManagerScreen.kt   // add/edit/remove saved URLs, per-entry retry/status
    sources/CustomSourceManagerViewModel.kt
    player/PlayerScreen.kt
    player/PlayerViewModel.kt
    components/                    // ChannelRow (with Source tag), GroupHeader, RegionCategoryToolbar, StaticOverlay
  MainActivity.kt
  TunerApplication.kt
```

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
```
No other permissions needed.

## Build/deliverable expectations

- Provide a complete, buildable Gradle project (Kotlin DSL `build.gradle.kts`) targeting a recent AGP/Compose BOM.
- Include the Media3 HLS dependency and confirm ExoPlayer is configured for adaptive HLS playback with reasonable buffering defaults for live streams (small live-edge buffer, since these are live broadcasts, not VOD).
- Ship with the region/category/parser logic fully implemented and testable independent of the UI (unit tests for the M3U parser are a good addition).
- Note in a README: this app streams publicly available, community-indexed broadcast streams from `iptv-org/iptv`; stream availability, legality of access in a given territory, and geo-restrictions are determined by the original broadcasters, not by this app.

## Out of scope for this build

- No backend/server component — everything runs on-device against the public playlist URLs.
- No user accounts, no offline/VOD caching of stream content.
- No attempt to systematically circumvent geo-blocking beyond the single best-effort CORS-style proxy retry described above.
