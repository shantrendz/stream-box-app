# Kids Mode + Parental Control — Design

Date: 2026-09-13
Status: Approved in chat, pending spec review

## Goal

Let a parent put StreamBox into a restricted **Kids Mode** that only shows child-appropriate
channels, protected by a 4-digit PIN. Parents verify themselves with a math problem when
setting up (or resetting) the PIN, and can hide or approve individual channels.

## Decisions

| Topic | Decision |
|---|---|
| Kids channel list | iptv-org `kids` + `animation` category playlists, plus parent-approved channels, minus parent-hidden channels |
| What kids can do | Browse the kids list, search within it, star favorites, watch (incl. Cast) |
| What is locked away | Region/Category/Language filters, Custom sources, History, Settings |
| Passkey | 4-digit PIN, stored as salted hash |
| First-time parent check | Two-step math problem before PIN setup |
| Forgot PIN | Harder math problem, then set a new PIN |
| Entry point | Settings → Parental Control; per-channel shield button while parent is unlocked |
| Kids Mode on | No PIN needed |
| Kids Mode off / Settings in Kids Mode | PIN required |

## Data — `ParentalControlRepository`

New DataStore (`parental_control`), held by `TunerApplication` like the other repositories.

Persisted:
- `pinHash` + `pinSalt` — PBKDF2WithHmacSHA1, 16-byte random salt, 10,000 iterations,
  hex-encoded. (minSdk 24 lacks `PBKDF2WithHmacSHA256` and `java.util.Base64`. A 4-digit PIN
  is brute-forceable offline regardless; hashing just avoids storing it in plain text.)
- `kidsModeEnabled: Boolean`
- `hiddenUrls: Set<String>` — stream URLs hidden from kids.
- `approvedChannels: List<ChannelSnapshot>` — full snapshots (name, logo, group, streamUrl,
  source) so approved channels render without re-fetching the playlist they came from.
- `failedAttempts: Int`, `lockoutUntil: Long` — 5 wrong PINs → 30 s lockout. Persisted so
  killing the app doesn't reset it.

Channel identity is the **stream URL alone**, not `favoriteKey()` (`source|streamUrl`):
the same channel carries different source labels in "All channels", "Category: Kids", etc.,
and a hide/approve must follow the stream everywhere.

`hiddenUrls` and `approvedChannels` are mutually exclusive: approving removes the URL from
hidden; hiding removes it from approved.

In-memory (not persisted):
- `parentUnlocked: StateFlow<Boolean>` — set on successful PIN entry. Relocks when
  `MainActivity.onStop` fires (app backgrounded) or 10 minutes after unlock.

API sketch:
```kotlin
val state: Flow<ParentalState>        // hasPin, kidsModeEnabled, hiddenUrls, approvedChannels, lockoutUntil
val parentUnlocked: StateFlow<Boolean>
suspend fun setPin(pin: String)
suspend fun verifyPin(pin: String): PinResult   // Ok, Wrong(attemptsLeft), LockedOut(untilMillis)
fun lock()
suspend fun setKidsMode(enabled: Boolean)       // disabling requires parentUnlocked
suspend fun hideFromKids(channel: Channel)
suspend fun allowForKids(channel: Channel)
suspend fun disableParentalControl()            // requires parentUnlocked; clears PIN, turns Kids Mode off, keeps lists
```

## Kids channel list

`ChannelRepository.loadKidsCatalog()` fetches `categories/kids.m3u` and
`categories/animation.m3u` in parallel, de-duplicates by stream URL, source label `"Kids"`.
If one playlist fails the other is used; if both fail → `SingleLoadResult.Failure`.

Visible kids channels (pure function, unit-tested):
```
visible = (kidsCatalog ∪ approvedChannels) − hiddenUrls     // de-duplicated by streamUrl
```

## Kids Mode UI

`ChannelListUiState` gains `kidsMode: Boolean`, `kidsTab: KidsTab { CHANNELS, FAVORITES }`,
`parentUnlocked: Boolean`, `hiddenUrls`, `approvedUrls`.

When `kidsMode` is true:
- Title bar shows a small "Kids" badge; tapping the "StreamBox" title returns to the kids list.
- `RegionCategoryToolbar` is replaced by two tabs: **Kids channels** and **Favorites**.
- Search filters within the current kids tab.
- Favorites tab = the shared favorites store filtered to `visible` (by stream URL). If the
  kids catalog failed to load, only approved favorites show.
- Starring in Kids Mode writes to the same favorites store as normal mode.
- The saved `TopMode` is ignored while Kids Mode is on and restored when it is turned off.
- Tapping Settings opens the PIN pad first.
- Turning Kids Mode on stops playback if the current channel isn't in `visible`.
- Kids Mode survives app restart.

## Parental Control in Settings

New "Parental Control" section in `SettingsScreen`:

- **No PIN set:** "Set up parental control" button → math challenge → enter PIN → confirm PIN
  → unlocked, Kids Mode switch appears.
- **PIN set, locked:** "Unlock" button → PIN pad (with "Forgot PIN?" link).
- **PIN set, unlocked:** Kids Mode switch, "Change PIN", "Turn off parental control", "Lock now".

### Math challenge (`MathChallenge`, pure, unit-tested)

- Setup difficulty: `a × b + c`, a ∈ 6..9, b ∈ 11..19, c ∈ 10..99 (e.g. `7 × 13 + 18`).
- Recovery difficulty: `a × b − c`, a ∈ 12..49, b ∈ 6..9, c ∈ 100..199, answer always positive
  (e.g. `47 × 8 − 129`).
- Numeric keypad input. After 3 wrong answers a new problem is generated.

### PIN pad

Reusable `PinPadDialog` composable: 4 dot indicators, 0–9 + backspace grid, shows attempts left
and a lockout countdown. Works with touch and D-pad.

## Per-channel shield button

Shown on `ChannelRow` and `ChannelGridTile` only when `parentUnlocked` is true.

| Where | Channel state | Icon | Tap |
|---|---|---|---|
| Kids Mode list | visible | shield | Hide from kids |
| Normal mode | approved | filled shield | Remove approval |
| Normal mode | hidden | slashed shield | Allow for kids |
| Normal mode | neither | outline shield | Allow for kids |

## Error handling

- Kids catalog load failure → existing error message UI, with approved channels still listed.
- Corrupt DataStore JSON → treated as empty lists (same pattern as `FavoritesRepository`).
  A corrupt/missing PIN hash with parental control on → falls through to "Forgot PIN" recovery.

## Testing

Unit: `MathChallenge` ranges/answers, PIN hash/verify, lockout timing, `visible` set
computation, hide/allow mutual exclusivity.
Manual on device: setup flow, wrong-PIN lockout survives restart, Kids Mode survives restart,
background → relock, search and favorites limited to kids channels, Settings gated.

## Out of scope

Viewing-time limits, per-child profiles, remote/parent-device management, content ratings.
