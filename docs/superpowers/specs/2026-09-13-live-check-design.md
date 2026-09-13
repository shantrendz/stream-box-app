# Live Check — Design

Date: 2026-09-13
Status: Approved in chat, pending spec review

## Goal

Show whether each channel's stream actually works right now, and let the user hide channels
that don't. Called **Live Check** in the app; the list toggle reads **Live only**.

## Decisions

| Topic | Decision |
|---|---|
| Presentation | Existing status dot on each row/tile shows the result; "Live only" toggle hides non-working |
| Check method | Playlist + first segment probe that mimics the player |
| What gets checked | Channels visible on screen, 4 at a time; whole list while "Live only" is on |
| Result cache | In memory, 30 minutes, keyed by stream URL |

## Status model

```kotlin
enum class LiveStatus { UNCHECKED, CHECKING, WORKING, NOT_WORKING }
```

| Status | Dot |
|---|---|
| UNCHECKED | hollow ring (outline color) |
| CHECKING | grey, pulsing |
| WORKING | green |
| NOT_WORKING | red |

Add a `green` entry to the tuner palette in `Color.kt` (dark and light variants) and a
`TunerGreen` accessor alongside `TunerRed`.

## Probe — `StreamProber` (`data/remote`)

Own OkHttp client: `User-Agent: VLC/3.0.20 LibVLC/3.0.20` (same as `PlayerViewModel`),
follow redirects, connect timeout 5 s, whole-probe timeout 8 s.

1. GET the stream URL, reading at most 64 KB.
2. Non-2xx, network error or timeout → `NOT_WORKING`.
3. Body starts with `#EXTM3U` (HLS):
   - If it contains `#EXT-X-STREAM-INF`, take the first variant URI (resolved against the
     playlist URL) and GET it the same way.
   - Take the first non-comment line of the media playlist as the segment URI, resolve it.
   - GET the segment with `Range: bytes=0-1023`; 200/206 with ≥ 1 byte → `WORKING`, else
     `NOT_WORKING`.
   - A media playlist with no segment lines → `NOT_WORKING`.
4. Not HLS (direct `.ts`/`.mp4`/DASH etc.): 2xx with ≥ 1 byte of body → `WORKING`.

## Scheduler — `LiveCheckRepository`

Process-scoped, held by `TunerApplication`.

- `statuses: StateFlow<Map<String, LiveStatus>>` — keyed by stream URL.
- Cache entries `(status, checkedAt)`; older than 30 minutes → treated as `UNCHECKED` and
  re-checked when requested.
- `Semaphore(4)` limits concurrent probes; in-flight URLs are de-duplicated.
- `requestVisible(urls: List<String>)` — replaces the pending queue with these URLs (newest
  first). Probes already running finish. Queued URLs that scrolled away are dropped.
- `requestAll(urls: List<String>)` — used while "Live only" is on; queues every URL, visible
  ones first.
- `report(url, working: Boolean)` — the player marks a channel `WORKING` when playback reaches
  READY and `NOT_WORKING` on a playback error, so real playback results feed the same cache.
- `clear()` — drops all results.

## UI

- `ChannelList` / grid observe `LazyListState.layoutInfo.visibleItemsInfo` via `snapshotFlow`,
  debounced 300 ms, and call `viewModel.requestLiveChecks(visibleUrls)`.
- `ChannelRow` and `ChannelGridTile` take a `liveStatus` parameter for the dot.
- **Live only** toggle in the channel toolbar, persisted in `AppStateRepository`.
  - When on, `filteredChannels` additionally keeps only `WORKING` channels, and all of the
    current list is queued.
  - A thin progress line shows "Checking 120 / 800" until every channel has a result.
  - Empty state while nothing has passed yet: "Checking channels…"; after all checked with no
    working ones: "No working channels in this list."
- Settings gets a "Live Check" section with a "Clear check results" button.
- Works in every mode (Catalog, Custom, Favorites, History, Kids Mode).

## Error handling

A probe exception of any kind is `NOT_WORKING`; it never crashes or blocks the list. Leaving
the screen does not cancel the repository's scope, so results keep filling the cache.

## Testing

Unit (OkHttp `MockWebServer` — new `testImplementation` dependency, version matched to the
app's OkHttp): master → variant → segment success; segment 403 → not working;
playlist 404 → not working; timeout → not working; relative URI resolution; direct non-HLS
stream. Scheduler: concurrency cap of 4, TTL expiry, de-duplication, `requestVisible` dropping
stale queue entries.
Manual on device: dots update while scrolling, Live only filters and shows progress, a channel
that fails in the player turns red.

## Out of scope

Background checks when the app is closed, persisting results across restarts, codec/DRM
compatibility detection, measuring stream bitrate or latency.
