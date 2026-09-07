# StreamBox — Android IPTV Player

A native Android app (Kotlin + Jetpack Compose) that streams free-to-air IPTV channels
sourced from the public [`iptv-org/iptv`](https://github.com/iptv-org/iptv) playlist
repository. Ported from an earlier web prototype, carrying over its "teletext/CRT tuner"
visual identity.

## Notice

This app streams **publicly available, community-indexed broadcast streams** curated by the
`iptv-org/iptv` project. StreamBox does not host, proxy, or modify any stream content — it only
fetches and parses publicly hosted `.m3u` playlist files and hands the listed URLs to the
Android media player.

**Stream availability, the legality of accessing a given stream in your territory, and any
geo-restrictions are determined entirely by the original broadcasters, not by this app.**
Some channels in the catalog may be slow, offline, or blocked in your region — that is
expected, and channels are intentionally never removed from the list on that basis (see
Error Handling below).

## Tech stack

- Kotlin, Jetpack Compose (Material 3, fully re-themed)
- MVVM: `ViewModel` + `StateFlow`, Repository pattern, manual DI (no Hilt — the dependency
  graph is small enough that a lightweight `Application`-scoped container was simpler and
  keeps a build step out of the loop)
- OkHttp for playlist fetching, Media3 ExoPlayer (`media3-exoplayer-hls`) for playback
- DataStore Preferences for persistence, `kotlinx.serialization` for the custom-source library
- Min SDK 24, target/compile SDK 35

## Project layout

```
app/src/main/java/com/example/tuner/
  data/model/            Channel, CustomSource
  data/remote/           PlaylistApi (OkHttp wrapper)
  data/parser/           M3UParser (line-by-line state machine, unit tested)
  data/repository/       ChannelRepository, CustomSourceRepository, AppStateRepository
  domain/                PlaylistSource (Region / Category / CustomSources)
  ui/theme/               Color.kt, Type.kt, Theme.kt — teletext theme tokens
  ui/channels/            ChannelListScreen + ChannelListViewModel
  ui/sources/             CustomSourceManagerScreen (add/edit/remove/retry saved sources)
  ui/player/              PlayerScreen + PlayerViewModel (Media3 ExoPlayer)
  ui/components/          ChannelRow, GroupHeader, StaticOverlay, RegionCategoryToolbar
  MainActivity.kt
  TunerApplication.kt
app/src/test/java/com/example/tuner/
  M3UParserTest.kt       Unit tests for the parser, independent of the UI
```

`CustomSourceManagerScreen` takes the shared `ChannelListViewModel` directly (rather than a
separate `CustomSourceManagerViewModel`) — CRUD on the source library and the merged-channel
reload it triggers are tightly coupled in this app, and a second ViewModel would only have
forwarded calls to the same repository. If the source library grows real independent logic,
splitting it out is a clean follow-up.

## Building

This project uses the standard Gradle wrapper:

```
./gradlew assembleDebug     # or gradlew.bat on Windows
./gradlew test              # runs the M3U parser unit tests
```

You'll need a local Android SDK (`local.properties` with `sdk.dir=...`, or the
`ANDROID_HOME` environment variable set) and a JDK 17+. Android Studio (Koala or newer) will
configure both automatically on first import.

> **Build status:** `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both pass —
> the project compiles clean (no warnings) and produces a debug APK, and all 7 `M3UParser`
> unit tests pass. Verified with AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.9 against Android SDK
> platform 35 and JDK 21.

## Design language

Dark-only "teletext/CRT tuner" theme — no light mode. Amber (`#FFB000`) is the primary
interactive accent, cyan (`#4FD8E0`) marks live status, red (`#FF4D3D`) marks errors/offline.
A single monospace family is used throughout (falls back to the system monospace; bundle
IBM Plex Mono as a font resource for a closer match to the original prototype). Channel rows
are flat, hairline-divided, teletext-style — no cards, no rounded corners, no drop shadows.

## Error handling

- Channels are never hidden or removed because a stream is unreachable, geo-restricted, or
  offline — the point of the app is to expose the full catalog.
- On playback failure, one silent automatic retry fires after ~600ms (many failures are
  slow-loading manifests, not dead streams).
- On repeated failure, an in-frame red "SIGNAL LOST" overlay offers two manual actions:
  **Retry** (direct URL again) and **Retry via proxy** (same stream through a CORS/relay
  proxy). The proxy retry is explicitly labeled best-effort in the UI — it will not
  reliably defeat IP-based geo-restriction, since that's enforced server-side by the
  broadcaster.
