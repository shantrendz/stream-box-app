# Kids Mode + Parental Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a PIN-protected Kids Mode that limits StreamBox to kids channels, with a math-problem parent check and per-channel hide/allow controls.

**Architecture:** Pure, unit-tested logic (`MathChallenge`, `PinHasher`, `LockoutPolicy`, `KidsVisibility`, `KidsLists`) lives in a new `com.example.tuner.parental` package. A DataStore-backed `ParentalControlRepository` (process-scoped in `TunerApplication`) persists PIN hash, Kids Mode flag and channel lists, and holds the in-memory "parent unlocked" state. `ChannelListViewModel` derives the kids list from that state; Compose dialogs in `ui/parental` drive PIN setup/unlock/recovery.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01, Material3), DataStore Preferences 1.1.1, kotlinx.serialization, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-13-kids-mode-parental-control-design.md`

## Global Constraints

- minSdk 24: no `java.util.Base64`, no `PBKDF2WithHmacSHA256`. Use `PBKDF2WithHmacSHA1`, hex encoding.
- PIN: exactly 4 digits. Salt 16 bytes, 10,000 iterations.
- Lockout: 5 wrong PINs → 30,000 ms lockout, persisted.
- Parent unlock relocks on `MainActivity.onStop` or 10 minutes after unlock.
- Channel identity for hide/approve = `streamUrl` only.
- Kids catalog = iptv-org `categories/kids.m3u` + `categories/animation.m3u`, source label `"Kids"`.
- Math: setup `a × b + c` (a 6..9, b 11..19, c 10..99); recovery `a × b − c` (a 12..49, b 6..9, c 100..199, answer > 0); new problem after 3 wrong answers.
- No new Gradle dependencies.
- Unit test command: `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"` (PowerShell, repo root). Build check: `.\gradlew.bat :app:assembleDebug`.

## File Structure

| File | Responsibility |
|---|---|
| Create `app/src/main/java/com/example/tuner/parental/MathChallenge.kt` | Generate/check parent math problems |
| Create `app/src/main/java/com/example/tuner/parental/PinHasher.kt` | PBKDF2 hash/verify, hex, PIN format |
| Create `app/src/main/java/com/example/tuner/parental/LockoutPolicy.kt` | Wrong-PIN counting and lockout timing |
| Create `app/src/main/java/com/example/tuner/parental/KidsVisibility.kt` | Visible kids list, playlist merge, `KidsLists` hide/allow, `KidsShieldState` |
| Create `app/src/main/java/com/example/tuner/data/repository/ParentalControlRepository.kt` | DataStore persistence + unlock session |
| Create `app/src/main/java/com/example/tuner/ui/parental/PinPadDialog.kt` | Dialog frame, numeric keypad, PIN pad |
| Create `app/src/main/java/com/example/tuner/ui/parental/MathChallengeDialog.kt` | Math problem dialog |
| Create `app/src/main/java/com/example/tuner/ui/parental/ParentalAuthFlow.kt` | Setup / unlock / recovery / change-PIN step machine |
| Create `app/src/main/java/com/example/tuner/ui/parental/ParentalControlSection.kt` | Settings section |
| Create `app/src/main/java/com/example/tuner/ui/parental/KidsTabsToolbar.kt` | Kids channels / Favorites tabs |
| Create `app/src/main/java/com/example/tuner/ui/parental/KidsShieldButton.kt` | Per-channel shield icon button |
| Modify `app/src/main/java/com/example/tuner/data/repository/ChannelRepository.kt` | `loadKidsCatalog()` |
| Modify `app/src/main/java/com/example/tuner/ui/channels/ChannelListViewModel.kt` | Kids state, tabs, parental actions |
| Modify `app/src/main/java/com/example/tuner/TunerApplication.kt` | Hold repository |
| Modify `app/src/main/java/com/example/tuner/MainActivity.kt` | Factory arg, `onStop` lock, screen redirect |
| Modify `app/src/main/java/com/example/tuner/ui/settings/SettingsScreen.kt` | Insert section |
| Modify `app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt` | Badge, tabs, settings gate, shields |
| Modify `app/src/main/java/com/example/tuner/ui/components/ChannelRow.kt` | Shield slot |
| Modify `app/src/main/java/com/example/tuner/ui/components/ChannelGridTile.kt` | Shield slot |
| Tests in `app/src/test/java/com/example/tuner/parental/` | Unit tests for pure logic |

---

### Task 1: Math challenge

**Files:**
- Create: `app/src/main/java/com/example/tuner/parental/MathChallenge.kt`
- Test: `app/src/test/java/com/example/tuner/parental/MathChallengeTest.kt`

**Interfaces:**
- Produces: `enum class ChallengeDifficulty { SETUP, RECOVERY }`; `data class MathChallenge(val question: String, val answer: Int)` with `fun isCorrect(input: String): Boolean` and `MathChallenge.generate(difficulty: ChallengeDifficulty, random: Random = Random.Default): MathChallenge`. Question format: `"a × b + c"` or `"a × b − c"` (spaces around `×`, `+`, `−` U+2212).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.tuner.parental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MathChallengeTest {

    private fun operands(question: String): List<String> = question.split(" ")

    @Test
    fun `setup problems stay in range and answer matches the question`() {
        val random = Random(42)
        repeat(1000) {
            val c = MathChallenge.generate(ChallengeDifficulty.SETUP, random)
            val (a, times, b, plus, addend) = operands(c.question)
            assertEquals("×", times)
            assertEquals("+", plus)
            assertTrue(a.toInt() in 6..9)
            assertTrue(b.toInt() in 11..19)
            assertTrue(addend.toInt() in 10..99)
            assertEquals(a.toInt() * b.toInt() + addend.toInt(), c.answer)
        }
    }

    @Test
    fun `recovery problems stay in range and are always positive`() {
        val random = Random(7)
        repeat(1000) {
            val c = MathChallenge.generate(ChallengeDifficulty.RECOVERY, random)
            val (a, times, b, minus, subtrahend) = operands(c.question)
            assertEquals("×", times)
            assertEquals("−", minus)
            assertTrue(a.toInt() in 12..49)
            assertTrue(b.toInt() in 6..9)
            assertTrue(subtrahend.toInt() in 100..199)
            assertEquals(a.toInt() * b.toInt() - subtrahend.toInt(), c.answer)
            assertTrue(c.answer > 0)
        }
    }

    @Test
    fun `isCorrect trims input and rejects non-numbers`() {
        val c = MathChallenge("7 × 13 + 18", 109)
        assertTrue(c.isCorrect("109"))
        assertTrue(c.isCorrect(" 109 "))
        assertFalse(c.isCorrect("108"))
        assertFalse(c.isCorrect(""))
        assertFalse(c.isCorrect("abc"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.parental.MathChallengeTest"`
Expected: FAIL — compilation error, `Unresolved reference: MathChallenge`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.tuner.parental

import kotlin.random.Random

enum class ChallengeDifficulty { SETUP, RECOVERY }

/**
 * A mental-arithmetic problem used as a lightweight "is this a parent?" check before a PIN can
 * be created or reset. Not a security boundary — just harder than a young child will solve.
 */
data class MathChallenge(val question: String, val answer: Int) {

    fun isCorrect(input: String): Boolean = input.trim().toIntOrNull() == answer

    companion object {
        fun generate(difficulty: ChallengeDifficulty, random: Random = Random.Default): MathChallenge =
            when (difficulty) {
                ChallengeDifficulty.SETUP -> {
                    val a = random.nextInt(6, 10)
                    val b = random.nextInt(11, 20)
                    val c = random.nextInt(10, 100)
                    MathChallenge("$a × $b + $c", a * b + c)
                }
                ChallengeDifficulty.RECOVERY -> {
                    var a: Int
                    var b: Int
                    var c: Int
                    do {
                        a = random.nextInt(12, 50)
                        b = random.nextInt(6, 10)
                        c = random.nextInt(100, 200)
                    } while (a * b - c <= 0)
                    MathChallenge("$a × $b − $c", a * b - c)
                }
            }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.parental.MathChallengeTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/tuner/parental/MathChallenge.kt app/src/test/java/com/example/tuner/parental/MathChallengeTest.kt
git commit -m "feat(parental): add math challenge generator"
```

---

### Task 2: PIN hashing and lockout policy

**Files:**
- Create: `app/src/main/java/com/example/tuner/parental/PinHasher.kt`
- Create: `app/src/main/java/com/example/tuner/parental/LockoutPolicy.kt`
- Test: `app/src/test/java/com/example/tuner/parental/PinHasherTest.kt`
- Test: `app/src/test/java/com/example/tuner/parental/LockoutPolicyTest.kt`

**Interfaces:**
- Produces:
  - `object PinHasher { fun isValidPin(pin: String): Boolean; fun newSalt(): ByteArray; fun hash(pin: String, salt: ByteArray): ByteArray; fun verify(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean; fun toHex(bytes: ByteArray): String; fun fromHex(hex: String): ByteArray }`
  - `object LockoutPolicy { const val MAX_ATTEMPTS = 5; const val LOCKOUT_MILLIS = 30_000L; data class Outcome(val failedAttempts: Int, val lockoutUntil: Long); fun onWrongPin(failedAttempts: Int, now: Long): Outcome; fun isLockedOut(lockoutUntil: Long, now: Long): Boolean }`

- [ ] **Step 1: Write the failing tests**

`PinHasherTest.kt`:

```kotlin
package com.example.tuner.parental

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `valid pins are exactly four digits`() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertTrue(PinHasher.isValidPin("4821"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12345"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    @Test
    fun `verify accepts the right pin and rejects others`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.verify("4821", salt, hash))
        assertFalse(PinHasher.verify("4822", salt, hash))
    }

    @Test
    fun `same pin with different salts gives different hashes`() {
        val a = PinHasher.hash("4821", PinHasher.newSalt())
        val b = PinHasher.hash("4821", PinHasher.newSalt())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `salt is 16 bytes`() {
        assertEquals(16, PinHasher.newSalt().size)
    }

    @Test
    fun `hex round trips`() {
        val bytes = byteArrayOf(0, 1, 127, -128, -1)
        assertEquals("00017f80ff", PinHasher.toHex(bytes))
        assertArrayEquals(bytes, PinHasher.fromHex("00017f80ff"))
    }
}
```

`LockoutPolicyTest.kt`:

```kotlin
package com.example.tuner.parental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    @Test
    fun `first four wrong pins only count up`() {
        var failed = 0
        repeat(4) {
            val outcome = LockoutPolicy.onWrongPin(failed, now = 1_000L)
            assertEquals(0L, outcome.lockoutUntil)
            failed = outcome.failedAttempts
        }
        assertEquals(4, failed)
    }

    @Test
    fun `fifth wrong pin locks out for thirty seconds and resets the counter`() {
        val outcome = LockoutPolicy.onWrongPin(failedAttempts = 4, now = 1_000L)
        assertEquals(0, outcome.failedAttempts)
        assertEquals(31_000L, outcome.lockoutUntil)
    }

    @Test
    fun `locked out only before the deadline`() {
        assertTrue(LockoutPolicy.isLockedOut(lockoutUntil = 31_000L, now = 30_999L))
        assertFalse(LockoutPolicy.isLockedOut(lockoutUntil = 31_000L, now = 31_000L))
        assertFalse(LockoutPolicy.isLockedOut(lockoutUntil = 0L, now = 5L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.parental.PinHasherTest" --tests "com.example.tuner.parental.LockoutPolicyTest"`
Expected: FAIL — `Unresolved reference: PinHasher` / `LockoutPolicy`.

- [ ] **Step 3: Write minimal implementation**

`PinHasher.kt`:

```kotlin
package com.example.tuner.parental

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the 4-digit parent PIN. A 4-digit PIN can be brute-forced offline
 * no matter what — this only keeps it out of plain text on disk. HMAC-SHA1 and hex encoding
 * because minSdk 24 has neither PBKDF2WithHmacSHA256 nor java.util.Base64.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private val secureRandom = SecureRandom()

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expectedHash)

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
```

`LockoutPolicy.kt`:

```kotlin
package com.example.tuner.parental

/** Wrong-PIN throttling: every [MAX_ATTEMPTS]th consecutive miss blocks entry for [LOCKOUT_MILLIS]. */
object LockoutPolicy {

    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MILLIS = 30_000L

    data class Outcome(val failedAttempts: Int, val lockoutUntil: Long)

    fun onWrongPin(failedAttempts: Int, now: Long): Outcome {
        val next = failedAttempts + 1
        return if (next >= MAX_ATTEMPTS) Outcome(0, now + LOCKOUT_MILLIS) else Outcome(next, 0L)
    }

    fun isLockedOut(lockoutUntil: Long, now: Long): Boolean = now < lockoutUntil
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.parental.PinHasherTest" --tests "com.example.tuner.parental.LockoutPolicyTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/tuner/parental/PinHasher.kt app/src/main/java/com/example/tuner/parental/LockoutPolicy.kt app/src/test/java/com/example/tuner/parental/PinHasherTest.kt app/src/test/java/com/example/tuner/parental/LockoutPolicyTest.kt
git commit -m "feat(parental): add PIN hashing and lockout policy"
```

---

### Task 3: Kids visibility, playlist merge, hide/allow lists

**Files:**
- Create: `app/src/main/java/com/example/tuner/parental/KidsVisibility.kt`
- Test: `app/src/test/java/com/example/tuner/parental/KidsVisibilityTest.kt`

**Interfaces:**
- Consumes: `com.example.tuner.data.model.Channel(name, logoUrl, group, streamUrl, source)`; `com.example.tuner.data.repository.SingleLoadResult` (`Success(channels)`, `Failure(message)`).
- Produces:
  - `enum class KidsShieldState { VISIBLE_IN_KIDS, APPROVED, HIDDEN, NEUTRAL }`
  - `object KidsVisibility { const val KIDS_SOURCE_LABEL = "Kids"; fun visibleChannels(kidsCatalog: List<Channel>, approved: List<Channel>, hiddenUrls: Set<String>): List<Channel>; fun mergeKidsPlaylists(kids: SingleLoadResult, animation: SingleLoadResult): SingleLoadResult }`
  - `data class KidsLists(val hiddenUrls: Set<String>, val approved: List<Channel>) { fun hide(channel: Channel): KidsLists; fun allow(channel: Channel): KidsLists; fun removeApproval(channel: Channel): KidsLists }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.tuner.parental

import com.example.tuner.data.model.Channel
import com.example.tuner.data.repository.SingleLoadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsVisibilityTest {

    private fun ch(name: String, url: String, source: String = "Kids") =
        Channel(name = name, logoUrl = null, group = "Kids", streamUrl = url, source = source)

    @Test
    fun `visible is catalog plus approved minus hidden, de-duplicated by url`() {
        val catalog = listOf(ch("A", "u/a"), ch("B", "u/b"))
        val approved = listOf(ch("B again", "u/b", source = "All channels"), ch("C", "u/c", source = "All channels"))
        val visible = KidsVisibility.visibleChannels(catalog, approved, hiddenUrls = setOf("u/a"))
        assertEquals(listOf("u/b", "u/c"), visible.map { it.streamUrl })
    }

    @Test
    fun `merge combines both playlists without duplicates`() {
        val merged = KidsVisibility.mergeKidsPlaylists(
            SingleLoadResult.Success(listOf(ch("A", "u/a"), ch("B", "u/b"))),
            SingleLoadResult.Success(listOf(ch("B", "u/b"), ch("C", "u/c")))
        )
        assertEquals(listOf("u/a", "u/b", "u/c"), (merged as SingleLoadResult.Success).channels.map { it.streamUrl })
    }

    @Test
    fun `merge uses the playlist that loaded when the other fails`() {
        val merged = KidsVisibility.mergeKidsPlaylists(
            SingleLoadResult.Failure("HTTP 500"),
            SingleLoadResult.Success(listOf(ch("C", "u/c")))
        )
        assertEquals(listOf("u/c"), (merged as SingleLoadResult.Success).channels.map { it.streamUrl })
    }

    @Test
    fun `merge fails only when both fail`() {
        val merged = KidsVisibility.mergeKidsPlaylists(
            SingleLoadResult.Failure("HTTP 500"),
            SingleLoadResult.Failure("timeout")
        )
        assertTrue(merged is SingleLoadResult.Failure)
    }

    @Test
    fun `hide removes approval and allow removes hidden`() {
        val c = ch("C", "u/c", source = "All channels")
        val hidden = KidsLists(hiddenUrls = emptySet(), approved = listOf(c)).hide(c)
        assertEquals(setOf("u/c"), hidden.hiddenUrls)
        assertTrue(hidden.approved.isEmpty())

        val allowed = hidden.allow(c)
        assertTrue(allowed.hiddenUrls.isEmpty())
        assertEquals(listOf("u/c"), allowed.approved.map { it.streamUrl })
    }

    @Test
    fun `allowing twice does not duplicate and removeApproval clears it`() {
        val c = ch("C", "u/c")
        val lists = KidsLists(emptySet(), emptyList()).allow(c).allow(c)
        assertEquals(1, lists.approved.size)
        assertTrue(lists.removeApproval(c).approved.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.parental.KidsVisibilityTest"`
Expected: FAIL — `Unresolved reference: KidsVisibility`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.tuner.parental

import com.example.tuner.data.model.Channel
import com.example.tuner.data.repository.SingleLoadResult

/** What the per-channel shield button shows and does while a parent is unlocked. */
enum class KidsShieldState { VISIBLE_IN_KIDS, APPROVED, HIDDEN, NEUTRAL }

/**
 * Kids Mode shows the iptv-org Kids + Animation playlists, plus channels a parent approved
 * from anywhere else, minus channels a parent hid. Channels are matched by stream URL alone —
 * the same stream carries different source labels depending on which playlist it came from.
 */
object KidsVisibility {

    const val KIDS_SOURCE_LABEL = "Kids"

    fun visibleChannels(
        kidsCatalog: List<Channel>,
        approved: List<Channel>,
        hiddenUrls: Set<String>
    ): List<Channel> = (kidsCatalog + approved)
        .distinctBy { it.streamUrl }
        .filterNot { it.streamUrl in hiddenUrls }

    fun mergeKidsPlaylists(kids: SingleLoadResult, animation: SingleLoadResult): SingleLoadResult {
        val loaded = listOf(kids, animation).filterIsInstance<SingleLoadResult.Success>()
        if (loaded.isEmpty()) {
            val message = (kids as SingleLoadResult.Failure).message
            return SingleLoadResult.Failure("Kids channels: $message")
        }
        return SingleLoadResult.Success(loaded.flatMap { it.channels }.distinctBy { it.streamUrl })
    }
}

/** Parent-managed overrides. Hidden and approved are mutually exclusive per stream URL. */
data class KidsLists(val hiddenUrls: Set<String>, val approved: List<Channel>) {

    fun hide(channel: Channel): KidsLists = KidsLists(
        hiddenUrls = hiddenUrls + channel.streamUrl,
        approved = approved.filterNot { it.streamUrl == channel.streamUrl }
    )

    fun allow(channel: Channel): KidsLists = KidsLists(
        hiddenUrls = hiddenUrls - channel.streamUrl,
        approved = approved.filterNot { it.streamUrl == channel.streamUrl } + channel
    )

    fun removeApproval(channel: Channel): KidsLists =
        copy(approved = approved.filterNot { it.streamUrl == channel.streamUrl })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.tuner.parental.KidsVisibilityTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/tuner/parental/KidsVisibility.kt app/src/test/java/com/example/tuner/parental/KidsVisibilityTest.kt
git commit -m "feat(parental): add kids visibility rules and hide/allow lists"
```

---

### Task 4: ParentalControlRepository and app wiring

**Files:**
- Create: `app/src/main/java/com/example/tuner/data/repository/ParentalControlRepository.kt`
- Modify: `app/src/main/java/com/example/tuner/TunerApplication.kt`
- Modify: `app/src/main/java/com/example/tuner/MainActivity.kt` (add `onStop`)

**Interfaces:**
- Consumes: `PinHasher`, `LockoutPolicy`, `KidsLists` (Tasks 2–3); `Channel`.
- Produces:
  - `data class ParentalState(val hasPin: Boolean = false, val kidsModeEnabled: Boolean = false, val hiddenUrls: Set<String> = emptySet(), val approvedChannels: List<Channel> = emptyList(), val failedAttempts: Int = 0, val lockoutUntil: Long = 0L)`
  - `sealed class PinResult { data object Ok; data class Wrong(val attemptsLeft: Int); data class LockedOut(val untilMillis: Long) }`
  - `class ParentalControlRepository(context: Context, clock: () -> Long = System::currentTimeMillis, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default))` with `val state: Flow<ParentalState>`, `val parentUnlocked: StateFlow<Boolean>`, `suspend fun setPin(pin: String)`, `suspend fun verifyPin(pin: String): PinResult`, `fun lock()`, `suspend fun setKidsMode(enabled: Boolean)`, `suspend fun hideFromKids(channel: Channel)`, `suspend fun allowForKids(channel: Channel)`, `suspend fun removeKidsApproval(channel: Channel)`, `suspend fun disableParentalControl()`.
  - `TunerApplication.parentalControlRepository: ParentalControlRepository`

- [ ] **Step 1: Create the repository**

```kotlin
package com.example.tuner.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tuner.data.model.Channel
import com.example.tuner.parental.KidsLists
import com.example.tuner.parental.LockoutPolicy
import com.example.tuner.parental.PinHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.parentalDataStore by preferencesDataStore(name = "parental_control")

@Serializable
private data class KidsChannelDto(
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val source: String
)

data class ParentalState(
    val hasPin: Boolean = false,
    val kidsModeEnabled: Boolean = false,
    val hiddenUrls: Set<String> = emptySet(),
    val approvedChannels: List<Channel> = emptyList(),
    val failedAttempts: Int = 0,
    val lockoutUntil: Long = 0L
)

sealed class PinResult {
    data object Ok : PinResult()
    data class Wrong(val attemptsLeft: Int) : PinResult()
    data class LockedOut(val untilMillis: Long) : PinResult()
}

/**
 * Parent PIN, Kids Mode flag, and the parent's hide/approve overrides, persisted on-device.
 * "Parent unlocked" is deliberately in-memory only: it ends when the app is backgrounded
 * ([lock] from MainActivity.onStop) or [UNLOCK_WINDOW_MILLIS] after unlocking.
 */
class ParentalControlRepository(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    companion object {
        const val UNLOCK_WINDOW_MILLIS = 10 * 60_000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val pinSaltKey = stringPreferencesKey("pin_salt")
    private val kidsModeKey = booleanPreferencesKey("kids_mode")
    private val hiddenUrlsKey = stringSetPreferencesKey("hidden_urls")
    private val approvedKey = stringPreferencesKey("approved_json")
    private val failedAttemptsKey = intPreferencesKey("failed_attempts")
    private val lockoutUntilKey = longPreferencesKey("lockout_until")

    private val _parentUnlocked = MutableStateFlow(false)
    val parentUnlocked: StateFlow<Boolean> = _parentUnlocked.asStateFlow()
    private var relockJob: Job? = null

    val state: Flow<ParentalState> = context.parentalDataStore.data.map { prefs ->
        ParentalState(
            hasPin = prefs[pinHashKey] != null && prefs[pinSaltKey] != null,
            kidsModeEnabled = prefs[kidsModeKey] ?: false,
            hiddenUrls = prefs[hiddenUrlsKey] ?: emptySet(),
            approvedChannels = decodeApproved(prefs[approvedKey]),
            failedAttempts = prefs[failedAttemptsKey] ?: 0,
            lockoutUntil = prefs[lockoutUntilKey] ?: 0L
        )
    }

    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin)) { "PIN must be 4 digits" }
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        context.parentalDataStore.edit { prefs ->
            prefs[pinSaltKey] = PinHasher.toHex(salt)
            prefs[pinHashKey] = PinHasher.toHex(hash)
            prefs[failedAttemptsKey] = 0
            prefs[lockoutUntilKey] = 0L
        }
        unlock()
    }

    suspend fun verifyPin(pin: String): PinResult {
        val prefs = context.parentalDataStore.data.first()
        val now = clock()
        val lockoutUntil = prefs[lockoutUntilKey] ?: 0L
        if (LockoutPolicy.isLockedOut(lockoutUntil, now)) return PinResult.LockedOut(lockoutUntil)

        val salt = prefs[pinSaltKey]?.let { runCatching { PinHasher.fromHex(it) }.getOrNull() }
        val hash = prefs[pinHashKey]?.let { runCatching { PinHasher.fromHex(it) }.getOrNull() }
        val matches = salt != null && hash != null &&
            withContext(Dispatchers.Default) { PinHasher.verify(pin, salt, hash) }

        if (matches) {
            context.parentalDataStore.edit { it[failedAttemptsKey] = 0; it[lockoutUntilKey] = 0L }
            unlock()
            return PinResult.Ok
        }

        val outcome = LockoutPolicy.onWrongPin(prefs[failedAttemptsKey] ?: 0, now)
        context.parentalDataStore.edit {
            it[failedAttemptsKey] = outcome.failedAttempts
            it[lockoutUntilKey] = outcome.lockoutUntil
        }
        return if (outcome.lockoutUntil > 0L) {
            PinResult.LockedOut(outcome.lockoutUntil)
        } else {
            PinResult.Wrong(LockoutPolicy.MAX_ATTEMPTS - outcome.failedAttempts)
        }
    }

    fun lock() {
        relockJob?.cancel()
        relockJob = null
        _parentUnlocked.value = false
    }

    /** Turning Kids Mode on needs a PIN to exist; turning it off needs the parent unlocked. */
    suspend fun setKidsMode(enabled: Boolean) {
        if (enabled && !state.first().hasPin) return
        if (!enabled && !_parentUnlocked.value) return
        context.parentalDataStore.edit { it[kidsModeKey] = enabled }
    }

    suspend fun hideFromKids(channel: Channel) = updateKidsLists { it.hide(channel) }

    suspend fun allowForKids(channel: Channel) = updateKidsLists { it.allow(channel) }

    suspend fun removeKidsApproval(channel: Channel) = updateKidsLists { it.removeApproval(channel) }

    /** Removes the PIN and turns Kids Mode off; the hide/approve lists are kept for next time. */
    suspend fun disableParentalControl() {
        if (!_parentUnlocked.value) return
        context.parentalDataStore.edit { prefs ->
            prefs.remove(pinHashKey)
            prefs.remove(pinSaltKey)
            prefs[kidsModeKey] = false
            prefs[failedAttemptsKey] = 0
            prefs[lockoutUntilKey] = 0L
        }
        lock()
    }

    private fun unlock() {
        _parentUnlocked.value = true
        relockJob?.cancel()
        relockJob = scope.launch {
            delay(UNLOCK_WINDOW_MILLIS)
            lock()
        }
    }

    private suspend fun updateKidsLists(transform: (KidsLists) -> KidsLists) {
        if (!_parentUnlocked.value) return
        context.parentalDataStore.edit { prefs ->
            val current = KidsLists(prefs[hiddenUrlsKey] ?: emptySet(), decodeApproved(prefs[approvedKey]))
            val updated = transform(current)
            prefs[hiddenUrlsKey] = updated.hiddenUrls
            prefs[approvedKey] = json.encodeToString(
                updated.approved.map { KidsChannelDto(it.name, it.logoUrl, it.group, it.streamUrl, it.source) }
            )
        }
    }

    private fun decodeApproved(raw: String?): List<Channel> {
        if (raw == null) return emptyList()
        return runCatching {
            json.decodeFromString<List<KidsChannelDto>>(raw)
                .map { Channel(it.name, it.logoUrl, it.group, it.streamUrl, it.source) }
        }.getOrDefault(emptyList())
    }
}
```

- [ ] **Step 2: Hold it in `TunerApplication`**

In `TunerApplication.kt`, add the import `import com.example.tuner.data.repository.ParentalControlRepository`, then add after the `castSessionManager` property:

```kotlin
    lateinit var parentalControlRepository: ParentalControlRepository
        private set
```

and at the end of `onCreate()`:

```kotlin
        parentalControlRepository = ParentalControlRepository(applicationContext)
```

- [ ] **Step 3: Relock when the app is backgrounded**

In `MainActivity.kt`, add inside `class MainActivity` after `onCreate`:

```kotlin
    // Parent unlock is meant for "the parent is holding the phone right now" — once the app
    // leaves the foreground, the next person to open it has to enter the PIN again.
    override fun onStop() {
        super.onStop()
        (application as TunerApplication).parentalControlRepository.lock()
    }
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/tuner/data/repository/ParentalControlRepository.kt app/src/main/java/com/example/tuner/TunerApplication.kt app/src/main/java/com/example/tuner/MainActivity.kt
git commit -m "feat(parental): persist PIN, Kids Mode and channel overrides"
```

---

### Task 5: Kids catalog loading and ViewModel state

**Files:**
- Modify: `app/src/main/java/com/example/tuner/data/repository/ChannelRepository.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/channels/ChannelListViewModel.kt`
- Modify: `app/src/main/java/com/example/tuner/MainActivity.kt` (factory argument)

**Interfaces:**
- Consumes: `ParentalControlRepository`, `ParentalState`, `PinResult` (Task 4); `KidsVisibility`, `KidsShieldState` (Task 3).
- Produces:
  - `ChannelRepository.loadKidsCatalog(): SingleLoadResult`
  - `enum class KidsTab { CHANNELS, FAVORITES }` (in `ChannelListViewModel.kt`)
  - `ChannelListUiState` new fields: `kidsMode: Boolean`, `kidsTab: KidsTab`, `kidsCatalog: List<Channel>`, `hasParentPin: Boolean`, `parentUnlocked: Boolean`, `hiddenKidsUrls: Set<String>`, `approvedKidsChannels: List<Channel>`, `pinLockoutUntil: Long`; getters `kidsVisibleChannels: List<Channel>`, `filteredChannels` (now kids-aware), `isCustomSourcesMode` (false in Kids Mode); `fun kidsShieldStateFor(channel: Channel): KidsShieldState`
  - `ChannelListViewModel` new functions: `selectKidsTab(tab: KidsTab)`, `setParentPin(pin: String)`, `verifyParentPin(pin: String, onResult: (PinResult) -> Unit)`, `lockParent()`, `setKidsMode(enabled: Boolean)`, `disableParentalControl()`, `onKidsShieldClick(channel: Channel)`
  - `ChannelListViewModel.factory(...)` gains a 6th parameter `parentalControlRepository: ParentalControlRepository`

- [ ] **Step 1: Add `loadKidsCatalog` to `ChannelRepository`**

Add import `import com.example.tuner.parental.KidsVisibility` and this function after `loadCatalog`:

```kotlin
    /** Kids Mode base list: iptv-org's Kids and Animation category playlists, merged. */
    suspend fun loadKidsCatalog(): SingleLoadResult = coroutineScope {
        val kids = async { loadAndParse(PlaylistApi.categoryUrl("kids"), KidsVisibility.KIDS_SOURCE_LABEL) }
        val animation = async { loadAndParse(PlaylistApi.categoryUrl("animation"), KidsVisibility.KIDS_SOURCE_LABEL) }
        KidsVisibility.mergeKidsPlaylists(kids.await(), animation.await())
    }
```

- [ ] **Step 2: Extend `ChannelListUiState`**

In `ChannelListViewModel.kt` add imports:

```kotlin
import com.example.tuner.data.repository.ParentalControlRepository
import com.example.tuner.data.repository.PinResult
import com.example.tuner.parental.KidsShieldState
import com.example.tuner.parental.KidsVisibility
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
```

Add below `enum class ChannelViewMode`:

```kotlin
/** The two tabs a child can switch between in Kids Mode. */
enum class KidsTab { CHANNELS, FAVORITES }
```

Replace the whole `data class ChannelListUiState(...) { ... }` with:

```kotlin
data class ChannelListUiState(
    val topMode: TopMode = TopMode.CATALOG,
    val region: PlaylistSource.Region = PlaylistSource.REGIONS.first(),
    val category: PlaylistSource.Category = PlaylistSource.CATEGORIES.first(),
    val language: PlaylistSource.Language = PlaylistSource.LANGUAGES.first(),
    val isLoading: Boolean = false,
    val loadedChannels: List<Channel> = emptyList(),
    val filterText: String = "",
    val groupBySource: Boolean = false,
    val viewMode: ChannelViewMode = ChannelViewMode.LIST,
    val customSources: List<CustomSource> = emptyList(),
    val customSourcesStatusMessage: String? = null,
    val loadErrorMessage: String? = null,
    val selectedChannel: Channel? = null,
    val favoriteKeys: Set<String> = emptySet(),
    val historyEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val kidsMode: Boolean = false,
    val kidsTab: KidsTab = KidsTab.CHANNELS,
    val kidsCatalog: List<Channel> = emptyList(),
    val hasParentPin: Boolean = false,
    val parentUnlocked: Boolean = false,
    val hiddenKidsUrls: Set<String> = emptySet(),
    val approvedKidsChannels: List<Channel> = emptyList(),
    val pinLockoutUntil: Long = 0L
) {
    val kidsVisibleChannels: List<Channel>
        get() = KidsVisibility.visibleChannels(kidsCatalog, approvedKidsChannels, hiddenKidsUrls)

    // In Kids Mode the Favorites tab reuses loadedChannels (streamed favorites) but only keeps
    // channels a child is allowed to see.
    private val baseChannels: List<Channel>
        get() = when {
            !kidsMode -> loadedChannels
            kidsTab == KidsTab.CHANNELS -> kidsVisibleChannels
            else -> {
                val allowed = kidsVisibleChannels.mapTo(HashSet()) { it.streamUrl }
                loadedChannels.filter { it.streamUrl in allowed }
            }
        }

    val filteredChannels: List<Channel>
        get() {
            val base = baseChannels
            return if (filterText.isBlank()) base else base.filter { it.name.contains(filterText, ignoreCase = true) }
        }

    val isCustomSourcesMode: Boolean get() = !kidsMode && topMode == TopMode.CUSTOM

    fun isFavorite(channel: Channel): Boolean = favoriteKeys.contains(channel.favoriteKey())

    fun kidsShieldStateFor(channel: Channel): KidsShieldState = when {
        kidsMode -> KidsShieldState.VISIBLE_IN_KIDS
        approvedKidsChannels.any { it.streamUrl == channel.streamUrl } -> KidsShieldState.APPROVED
        channel.streamUrl in hiddenKidsUrls -> KidsShieldState.HIDDEN
        else -> KidsShieldState.NEUTRAL
    }
}
```

- [ ] **Step 3: Add the constructor parameter and collectors**

Change the class header to:

```kotlin
class ChannelListViewModel(
    private val channelRepository: ChannelRepository,
    private val customSourceRepository: CustomSourceRepository,
    private val appStateRepository: AppStateRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val parentalControlRepository: ParentalControlRepository
) : ViewModel() {
```

In `init`, insert before `restoreLastState()`:

```kotlin
        viewModelScope.launch {
            parentalControlRepository.state.collect { parental ->
                _uiState.update {
                    it.copy(
                        hasParentPin = parental.hasPin,
                        hiddenKidsUrls = parental.hiddenUrls,
                        approvedKidsChannels = parental.approvedChannels,
                        pinLockoutUntil = parental.lockoutUntil
                    )
                }
            }
        }
        viewModelScope.launch {
            parentalControlRepository.parentUnlocked.collect { unlocked ->
                _uiState.update { it.copy(parentUnlocked = unlocked) }
            }
        }
        // Only reacts to changes after launch — restoreLastState() handles the initial value.
        viewModelScope.launch {
            parentalControlRepository.state
                .map { it.kidsModeEnabled }
                .distinctUntilChanged()
                .drop(1)
                .collect { enabled -> if (enabled) enterKidsMode() else exitKidsMode() }
        }
```

- [ ] **Step 4: Route restore through Kids Mode**

Replace `restoreLastState()` with:

```kotlin
    private fun restoreLastState() {
        viewModelScope.launch {
            val savedFilter = appStateRepository.lastFilterText.first()
            _uiState.update { it.copy(filterText = savedFilter) }

            val saved = appStateRepository.lastCatalogState.first()
            val region = PlaylistSource.REGIONS.firstOrNull { it.code == saved.regionCode } ?: PlaylistSource.REGIONS.first()
            val category = PlaylistSource.CATEGORIES.firstOrNull { it.slug == saved.categorySlug } ?: PlaylistSource.CATEGORIES.first()
            val language = PlaylistSource.LANGUAGES.firstOrNull { it.code == saved.languageCode } ?: PlaylistSource.LANGUAGES.first()
            _uiState.update { it.copy(region = region, category = category, language = language) }

            if (parentalControlRepository.state.first().kidsModeEnabled) {
                enterKidsMode()
            } else {
                applyTopMode(saved.topMode)
            }
        }
    }

    private fun applyTopMode(mode: TopMode) {
        when (mode) {
            TopMode.CATALOG -> reloadCatalog()
            TopMode.CUSTOM -> activateCustomSourcesMode(persist = false)
            TopMode.FAVORITES -> selectFavoritesMode(persist = false)
            TopMode.HISTORY -> selectHistoryMode(persist = false)
        }
    }
```

- [ ] **Step 5: Add Kids Mode transitions, tabs and loading**

Add after `applyTopMode`:

```kotlin
    private fun enterKidsMode() {
        localModeJob?.cancel()
        _uiState.update {
            it.copy(kidsMode = true, kidsTab = KidsTab.CHANNELS, filterText = "", customSourcesStatusMessage = null)
        }
        reloadKidsCatalog()
    }

    private fun exitKidsMode() {
        localModeJob?.cancel()
        _uiState.update { it.copy(kidsMode = false, kidsTab = KidsTab.CHANNELS) }
        viewModelScope.launch { applyTopMode(appStateRepository.lastCatalogState.first().topMode) }
    }

    private fun reloadKidsCatalog() {
        val requestId = ++loadRequestId
        viewModelScope.launch {
            setLoading(true)
            val result = channelRepository.loadKidsCatalog()
            if (requestId != loadRequestId) return@launch
            _uiState.update {
                it.copy(
                    isLoading = false,
                    kidsCatalog = (result as? SingleLoadResult.Success)?.channels ?: emptyList(),
                    loadErrorMessage = (result as? SingleLoadResult.Failure)?.message
                )
            }
            // Don't leave a non-kids channel playing once Kids Mode is on.
            val state = _uiState.value
            val selected = state.selectedChannel
            if (selected != null && state.kidsVisibleChannels.none { it.streamUrl == selected.streamUrl }) {
                clearSelectedChannel()
            }
        }
    }

    fun selectKidsTab(tab: KidsTab) {
        localModeJob?.cancel()
        _uiState.update { it.copy(kidsTab = tab) }
        if (tab == KidsTab.FAVORITES) {
            localModeJob = viewModelScope.launch {
                favoritesRepository.favorites.collect { favorites ->
                    _uiState.update { it.copy(loadedChannels = favorites) }
                }
            }
        }
    }
```

At the top of `resetToAllChannels()` body insert:

```kotlin
        if (_uiState.value.kidsMode) {
            selectKidsTab(KidsTab.CHANNELS)
            setFilterText("")
            return
        }
```

- [ ] **Step 6: Add parental actions**

Add before `// --- Custom source library CRUD`:

```kotlin
    // --- Parental control ---

    fun setParentPin(pin: String) {
        viewModelScope.launch { parentalControlRepository.setPin(pin) }
    }

    fun verifyParentPin(pin: String, onResult: (PinResult) -> Unit) {
        viewModelScope.launch { onResult(parentalControlRepository.verifyPin(pin)) }
    }

    fun lockParent() = parentalControlRepository.lock()

    fun setKidsMode(enabled: Boolean) {
        viewModelScope.launch { parentalControlRepository.setKidsMode(enabled) }
    }

    fun disableParentalControl() {
        viewModelScope.launch { parentalControlRepository.disableParentalControl() }
    }

    fun onKidsShieldClick(channel: Channel) {
        val shieldState = _uiState.value.kidsShieldStateFor(channel)
        viewModelScope.launch {
            when (shieldState) {
                KidsShieldState.VISIBLE_IN_KIDS -> parentalControlRepository.hideFromKids(channel)
                KidsShieldState.APPROVED -> parentalControlRepository.removeKidsApproval(channel)
                KidsShieldState.HIDDEN, KidsShieldState.NEUTRAL -> parentalControlRepository.allowForKids(channel)
            }
        }
    }
```

- [ ] **Step 7: Update the factory**

Replace the `companion object` with:

```kotlin
    companion object {
        fun factory(
            channelRepository: ChannelRepository,
            customSourceRepository: CustomSourceRepository,
            appStateRepository: AppStateRepository,
            favoritesRepository: FavoritesRepository,
            historyRepository: HistoryRepository,
            parentalControlRepository: ParentalControlRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChannelListViewModel(
                    channelRepository,
                    customSourceRepository,
                    appStateRepository,
                    favoritesRepository,
                    historyRepository,
                    parentalControlRepository
                )
            }
        }
    }
```

In `MainActivity.kt`, add `parentalControlRepository = app.parentalControlRepository` as the last argument of `ChannelListViewModel.factory(...)`.

- [ ] **Step 8: Build and run all unit tests**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/tuner/data/repository/ChannelRepository.kt app/src/main/java/com/example/tuner/ui/channels/ChannelListViewModel.kt app/src/main/java/com/example/tuner/MainActivity.kt
git commit -m "feat(parental): load kids catalog and add Kids Mode view state"
```

---

### Task 6: PIN pad, math challenge dialog and auth flow

**Files:**
- Create: `app/src/main/java/com/example/tuner/ui/parental/PinPadDialog.kt`
- Create: `app/src/main/java/com/example/tuner/ui/parental/MathChallengeDialog.kt`
- Create: `app/src/main/java/com/example/tuner/ui/parental/ParentalAuthFlow.kt`

**Interfaces:**
- Consumes: `MathChallenge`, `ChallengeDifficulty` (Task 1); `PinResult` (Task 4).
- Produces:
  - `@Composable internal fun ParentalDialogFrame(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable internal fun NumericKeypad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit)`
  - `@Composable fun PinPadDialog(title: String, message: String?, isError: Boolean, lockoutUntil: Long, onPinEntered: (String) -> Unit, onDismiss: () -> Unit, onForgotPin: (() -> Unit)? = null)`
  - `@Composable fun MathChallengeDialog(difficulty: ChallengeDifficulty, onSolved: () -> Unit, onDismiss: () -> Unit)`
  - `enum class ParentalFlowStart { SETUP, UNLOCK, CHANGE_PIN }`
  - `@Composable fun ParentalAuthFlow(start: ParentalFlowStart, lockoutUntil: Long, onVerifyPin: (String, (PinResult) -> Unit) -> Unit, onSetPin: (String) -> Unit, onFinished: (success: Boolean) -> Unit)`

- [ ] **Step 1: Create `PinPadDialog.kt`**

```kotlin
package com.example.tuner.ui.parental

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerSurface
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary
import kotlinx.coroutines.delay

private const val PIN_LENGTH = 4

@Composable
internal fun ParentalDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(shape)
                .background(TunerSurface)
                .border(1.dp, TunerOutline, shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TunerTextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            content()
            TextButton(onClick = onDismiss) { Text("Cancel", color = TunerTextSecondary) }
        }
    }
}

/** 3×4 digit grid (1–9, blank, 0, backspace). Large keys so it works with touch and D-pad. */
@Composable
internal fun NumericKeypad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit -> KeypadKey(digit.toString(), digit.toString(), enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 64.dp, height = 52.dp))
            KeypadKey("0", "0", enabled) { onDigit('0') }
            KeypadKey("⌫", "Delete", enabled, onBackspace)
        }
    }
}

@Composable
private fun KeypadKey(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 52.dp)
            .clip(shape)
            .background(TunerBackground)
            .border(1.dp, TunerOutline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) TunerTextPrimary else TunerTextSecondary
        )
    }
}

/**
 * 4-digit PIN entry. Calls [onPinEntered] as soon as the 4th digit is typed and clears the
 * dots for the next attempt. While [lockoutUntil] is in the future the keypad is disabled and
 * a countdown replaces [message].
 */
@Composable
fun PinPadDialog(
    title: String,
    message: String?,
    isError: Boolean,
    lockoutUntil: Long,
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit,
    onForgotPin: (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockoutUntil) {
        while (System.currentTimeMillis() < lockoutUntil) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val lockedSeconds = ((lockoutUntil - now + 999) / 1000).coerceAtLeast(0)
    val lockedOut = lockedSeconds > 0

    ParentalDialogFrame(title = title, onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PIN_LENGTH) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) TunerAmber else TunerOutline)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val status = if (lockedOut) "Too many wrong tries — wait ${lockedSeconds}s" else message
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = if (lockedOut || isError) TunerRed else TunerTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
        NumericKeypad(
            enabled = !lockedOut,
            onDigit = { digit ->
                if (pin.length < PIN_LENGTH) {
                    pin += digit
                    if (pin.length == PIN_LENGTH) {
                        val entered = pin
                        pin = ""
                        onPinEntered(entered)
                    }
                }
            },
            onBackspace = { pin = pin.dropLast(1) }
        )
        if (onForgotPin != null) {
            TextButton(onClick = onForgotPin) { Text("Forgot PIN?", color = TunerCyan) }
        }
    }
}
```

- [ ] **Step 2: Create `MathChallengeDialog.kt`**

```kotlin
package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tuner.parental.ChallengeDifficulty
import com.example.tuner.parental.MathChallenge
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextPrimary

private const val MAX_WRONG_ANSWERS = 3
private const val MAX_ANSWER_DIGITS = 4

@Composable
fun MathChallengeDialog(
    difficulty: ChallengeDifficulty,
    onSolved: () -> Unit,
    onDismiss: () -> Unit
) {
    var challenge by remember { mutableStateOf(MathChallenge.generate(difficulty)) }
    var answer by remember { mutableStateOf("") }
    var wrongCount by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val title = if (difficulty == ChallengeDifficulty.SETUP) "Parents only: solve this" else "Reset PIN: solve this"

    ParentalDialogFrame(title = title, onDismiss = onDismiss) {
        Text(
            text = "${challenge.question} = ?",
            style = MaterialTheme.typography.headlineSmall,
            color = TunerAmber,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = answer.ifEmpty { "–" },
            style = MaterialTheme.typography.headlineMedium,
            color = TunerTextPrimary
        )
        message?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = TunerRed, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        NumericKeypad(
            enabled = true,
            onDigit = { digit -> if (answer.length < MAX_ANSWER_DIGITS) answer += digit },
            onBackspace = { answer = answer.dropLast(1) }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (challenge.isCorrect(answer)) {
                    onSolved()
                } else {
                    wrongCount++
                    answer = ""
                    if (wrongCount >= MAX_WRONG_ANSWERS) {
                        challenge = MathChallenge.generate(difficulty)
                        wrongCount = 0
                        message = "Not quite — here's a new problem."
                    } else {
                        message = "Not quite — try again."
                    }
                }
            },
            enabled = answer.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = TunerAmber, contentColor = TunerBackground)
        ) {
            Text("CHECK")
        }
    }
}
```

- [ ] **Step 3: Create `ParentalAuthFlow.kt`**

```kotlin
package com.example.tuner.ui.parental

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.tuner.data.repository.PinResult
import com.example.tuner.parental.ChallengeDifficulty

enum class ParentalFlowStart { SETUP, UNLOCK, CHANGE_PIN }

private enum class FlowStep { SETUP_CHALLENGE, RECOVERY_CHALLENGE, UNLOCK, CHOOSE_PIN, CONFIRM_PIN }

/**
 * One dialog at a time, walking through:
 * - SETUP: math problem → choose PIN → confirm PIN
 * - UNLOCK: PIN pad ("Forgot PIN?" → harder math problem → choose PIN → confirm PIN)
 * - CHANGE_PIN: choose PIN → confirm PIN
 * [onFinished] gets true when the parent ends up unlocked, false on cancel.
 */
@Composable
fun ParentalAuthFlow(
    start: ParentalFlowStart,
    lockoutUntil: Long,
    onVerifyPin: (String, (PinResult) -> Unit) -> Unit,
    onSetPin: (String) -> Unit,
    onFinished: (success: Boolean) -> Unit
) {
    var step by remember {
        mutableStateOf(
            when (start) {
                ParentalFlowStart.SETUP -> FlowStep.SETUP_CHALLENGE
                ParentalFlowStart.UNLOCK -> FlowStep.UNLOCK
                ParentalFlowStart.CHANGE_PIN -> FlowStep.CHOOSE_PIN
            }
        )
    }
    var chosenPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun goTo(next: FlowStep, newMessage: String? = null, error: Boolean = false) {
        step = next
        message = newMessage
        isError = error
    }

    val cancel = { onFinished(false) }

    when (step) {
        FlowStep.SETUP_CHALLENGE -> MathChallengeDialog(
            difficulty = ChallengeDifficulty.SETUP,
            onSolved = { goTo(FlowStep.CHOOSE_PIN) },
            onDismiss = cancel
        )
        FlowStep.RECOVERY_CHALLENGE -> MathChallengeDialog(
            difficulty = ChallengeDifficulty.RECOVERY,
            onSolved = { goTo(FlowStep.CHOOSE_PIN) },
            onDismiss = cancel
        )
        FlowStep.UNLOCK -> PinPadDialog(
            title = "Enter parent PIN",
            message = message,
            isError = isError,
            lockoutUntil = lockoutUntil,
            onPinEntered = { pin ->
                onVerifyPin(pin) { result ->
                    when (result) {
                        PinResult.Ok -> onFinished(true)
                        is PinResult.Wrong -> {
                            message = "Wrong PIN — ${result.attemptsLeft} tries left"
                            isError = true
                        }
                        is PinResult.LockedOut -> {
                            message = null
                            isError = true
                        }
                    }
                }
            },
            onDismiss = cancel,
            onForgotPin = { goTo(FlowStep.RECOVERY_CHALLENGE) }
        )
        FlowStep.CHOOSE_PIN -> PinPadDialog(
            title = "Choose a 4-digit PIN",
            message = message,
            isError = isError,
            lockoutUntil = 0L,
            onPinEntered = { pin ->
                chosenPin = pin
                goTo(FlowStep.CONFIRM_PIN)
            },
            onDismiss = cancel
        )
        FlowStep.CONFIRM_PIN -> PinPadDialog(
            title = "Enter the PIN again",
            message = null,
            isError = false,
            lockoutUntil = 0L,
            onPinEntered = { pin ->
                if (pin == chosenPin) {
                    onSetPin(pin)
                    onFinished(true)
                } else {
                    chosenPin = ""
                    goTo(FlowStep.CHOOSE_PIN, "PINs didn't match — choose again", error = true)
                }
            },
            onDismiss = cancel
        )
    }
}
```

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/tuner/ui/parental/PinPadDialog.kt app/src/main/java/com/example/tuner/ui/parental/MathChallengeDialog.kt app/src/main/java/com/example/tuner/ui/parental/ParentalAuthFlow.kt
git commit -m "feat(parental): add PIN pad, math challenge and auth flow dialogs"
```

---

### Task 7: Parental Control section in Settings

**Files:**
- Create: `app/src/main/java/com/example/tuner/ui/parental/ParentalControlSection.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `ChannelListUiState` fields `hasParentPin`, `parentUnlocked`, `kidsMode`, `pinLockoutUntil`; `ChannelListViewModel.setKidsMode`, `lockParent`, `disableParentalControl`, `verifyParentPin`, `setParentPin` (Task 5); `ParentalAuthFlow`, `ParentalFlowStart` (Task 6).
- Produces: `@Composable fun ParentalControlSection(uiState: ChannelListUiState, viewModel: ChannelListViewModel)`

- [ ] **Step 1: Create the section**

```kotlin
package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.channels.ChannelListUiState
import com.example.tuner.ui.channels.ChannelListViewModel
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

@Composable
fun ParentalControlSection(uiState: ChannelListUiState, viewModel: ChannelListViewModel) {
    var flowStart by remember { mutableStateOf<ParentalFlowStart?>(null) }
    val amberButton = ButtonDefaults.buttonColors(containerColor = TunerAmber, contentColor = TunerBackground)

    when {
        !uiState.hasParentPin -> SettingRow(
            title = "Set up parental control",
            subtitle = "Kids Mode shows only kids channels. Settings and leaving Kids Mode need a PIN."
        ) {
            Button(onClick = { flowStart = ParentalFlowStart.SETUP }, colors = amberButton) { Text("SET UP") }
        }

        !uiState.parentUnlocked -> SettingRow(
            title = "Parental control is on",
            subtitle = if (uiState.kidsMode) "Kids Mode is active." else "Unlock to turn on Kids Mode or manage channels."
        ) {
            Button(onClick = { flowStart = ParentalFlowStart.UNLOCK }, colors = amberButton) { Text("UNLOCK") }
        }

        else -> {
            SettingRow(
                title = "Kids Mode",
                subtitle = "Only kids channels, search and favorites. Turning it off needs the PIN."
            ) {
                Switch(
                    checked = uiState.kidsMode,
                    onCheckedChange = viewModel::setKidsMode,
                    colors = SwitchDefaults.colors(checkedThumbColor = TunerAmber, checkedTrackColor = TunerAmber.copy(alpha = 0.4f))
                )
            }
            Text(
                text = "While unlocked, tap the shield on any channel to hide it from Kids Mode or allow it.",
                style = MaterialTheme.typography.labelSmall,
                color = TunerTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { flowStart = ParentalFlowStart.CHANGE_PIN }, modifier = Modifier.weight(1f)) {
                    Text("CHANGE PIN", color = TunerTextPrimary, maxLines = 1)
                }
                OutlinedButton(onClick = viewModel::lockParent, modifier = Modifier.weight(1f)) {
                    Text("LOCK", color = TunerTextPrimary, maxLines = 1)
                }
                Button(
                    onClick = viewModel::disableParentalControl,
                    colors = ButtonDefaults.buttonColors(containerColor = TunerRed, contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("TURN OFF", maxLines = 1)
                }
            }
        }
    }

    flowStart?.let { start ->
        ParentalAuthFlow(
            start = start,
            lockoutUntil = uiState.pinLockoutUntil,
            onVerifyPin = viewModel::verifyParentPin,
            onSetPin = viewModel::setParentPin,
            onFinished = { flowStart = null }
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TunerTextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TunerTextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}
```

- [ ] **Step 2: Insert it into Settings**

In `SettingsScreen.kt`, add import `import com.example.tuner.ui.parental.ParentalControlSection`. Directly before the About block (`Spacer(Modifier.height(28.dp))` followed by `SectionHeader("About")`) insert:

```kotlin
        Spacer(Modifier.height(28.dp))
        SectionHeader("Parental Control")
        ParentalControlSection(uiState = uiState, viewModel = viewModel)
```

- [ ] **Step 3: Build and install**

Run: `.\gradlew.bat :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device.`

- [ ] **Step 4: Manual check on device**

Settings → Parental Control:
1. Tap SET UP → a `a × b + c` problem shows. Enter a wrong answer 3 times → "here's a new problem". Solve it.
2. Choose `1234`, confirm `1235` → "PINs didn't match". Choose `1234`, confirm `1234` → section shows Kids Mode switch, CHANGE PIN, LOCK, TURN OFF.
3. Tap LOCK → "Parental control is on" + UNLOCK. Enter wrong PIN 4 times → "N tries left" each time; 5th → keypad disabled with a 30 s countdown. Force-stop and reopen the app → still locked out until the countdown ends.
4. After it ends, tap "Forgot PIN?" → `a × b − c` problem → set a new PIN → unlocked.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/tuner/ui/parental/ParentalControlSection.kt app/src/main/java/com/example/tuner/ui/settings/SettingsScreen.kt
git commit -m "feat(parental): add Parental Control section to Settings"
```

---

### Task 8: Kids Mode on the channel screen

**Files:**
- Create: `app/src/main/java/com/example/tuner/ui/parental/KidsTabsToolbar.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt`
- Modify: `app/src/main/java/com/example/tuner/MainActivity.kt` (`TunerApp` redirect)

**Interfaces:**
- Consumes: `KidsTab`, `ChannelListUiState.kidsMode/kidsTab/hasParentPin/parentUnlocked/pinLockoutUntil`, `ChannelListViewModel.selectKidsTab/verifyParentPin/setParentPin` (Task 5); `ParentalAuthFlow` (Task 6).
- Produces: `@Composable fun KidsTabsToolbar(selected: KidsTab, onSelect: (KidsTab) -> Unit, modifier: Modifier = Modifier)`; `TunerTitleBar` gains `kidsMode: Boolean`.

- [ ] **Step 1: Create `KidsTabsToolbar.kt`**

```kotlin
package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.channels.KidsTab
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerAmberTint
import com.example.tuner.ui.theme.TunerTextPrimary

/** Replaces the Region/Category/Language toolbar while Kids Mode is on. */
@Composable
fun KidsTabsToolbar(selected: KidsTab, onSelect: (KidsTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidsTab.entries.forEach { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = { Text(if (tab == KidsTab.CHANNELS) "Kids channels" else "Favorites") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TunerAmberTint,
                    selectedLabelColor = TunerAmber,
                    labelColor = TunerTextPrimary
                )
            )
        }
    }
}
```

- [ ] **Step 2: Title bar badge, hidden custom-sources button, settings gate**

In `ChannelListScreen.kt` add imports:

```kotlin
import com.example.tuner.ui.parental.KidsTabsToolbar
import com.example.tuner.ui.parental.ParentalAuthFlow
import com.example.tuner.ui.parental.ParentalFlowStart
import com.example.tuner.ui.theme.TunerBackground
```

(`TunerBackground` is already imported — skip it if present.)

In `ChannelListScreen`, after `val selected = uiState.selectedChannel` add:

```kotlin
    var showParentGate by remember { mutableStateOf(false) }
```

Replace the `TunerTitleBar(...)` call with:

```kotlin
        TunerTitleBar(
            viewMode = uiState.viewMode,
            kidsMode = uiState.kidsMode,
            onSetViewMode = viewModel::setViewMode,
            onOpenCustomSourceManager = onOpenCustomSourceManager,
            onOpenSettings = {
                if (uiState.kidsMode && !uiState.parentUnlocked) showParentGate = true else onOpenSettings()
            },
            onResetToAllChannels = viewModel::resetToAllChannels
        )

        if (showParentGate) {
            ParentalAuthFlow(
                start = if (uiState.hasParentPin) ParentalFlowStart.UNLOCK else ParentalFlowStart.SETUP,
                lockoutUntil = uiState.pinLockoutUntil,
                onVerifyPin = viewModel::verifyParentPin,
                onSetPin = viewModel::setParentPin,
                onFinished = { success ->
                    showParentGate = false
                    if (success) onOpenSettings()
                }
            )
        }
```

Replace the `TunerTitleBar` function signature and body with:

```kotlin
@Composable
private fun TunerTitleBar(
    viewMode: ChannelViewMode,
    kidsMode: Boolean,
    onSetViewMode: (ChannelViewMode) -> Unit,
    onOpenCustomSourceManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onResetToAllChannels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(TunerGradientTop, TunerGradientBottom)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onResetToAllChannels)
        ) {
            AppIcon(size = 34.dp)
            Text(
                text = "StreamBox",
                style = MaterialTheme.typography.titleLarge,
                color = TunerAmber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
            if (kidsMode) {
                Text(
                    text = "KIDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TunerBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TunerAmber)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = { onSetViewMode(if (viewMode == ChannelViewMode.LIST) ChannelViewMode.GRID else ChannelViewMode.LIST) }) {
            Icon(
                imageVector = if (viewMode == ChannelViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (viewMode == ChannelViewMode.LIST) "Switch to icon view" else "Switch to list view",
                tint = TunerTextPrimary
            )
        }
        if (!kidsMode) {
            IconButton(onClick = onOpenCustomSourceManager) {
                Icon(Icons.Filled.Link, contentDescription = "Manage custom sources", tint = TunerTextPrimary)
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TunerTextPrimary)
        }
    }
}
```

Add import `import androidx.compose.ui.draw.clip`.

- [ ] **Step 3: Kids toolbar and empty messages in `ChannelListPane`**

Replace the `RegionCategoryToolbar(...)` call in `ChannelListPane` with:

```kotlin
        if (uiState.kidsMode) {
            KidsTabsToolbar(
                selected = uiState.kidsTab,
                onSelect = viewModel::selectKidsTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        } else {
            RegionCategoryToolbar(
                topMode = uiState.topMode,
                region = uiState.region,
                category = uiState.category,
                language = uiState.language,
                onSelectRegion = viewModel::selectRegion,
                onSelectCategory = viewModel::selectCategory,
                onSelectLanguage = viewModel::selectLanguage,
                onSelectCustomSources = viewModel::activateCustomSourcesMode,
                onSelectFavorites = viewModel::selectFavoritesMode,
                onSelectHistory = viewModel::selectHistoryMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
```

Change `if (uiState.topMode == TopMode.HISTORY && !uiState.historyEnabled)` to:

```kotlin
        if (!uiState.kidsMode && uiState.topMode == TopMode.HISTORY && !uiState.historyEnabled)
```

In the `ChannelList(...)` call change `emptyMessage = emptyMessageFor(uiState.topMode),` to:

```kotlin
            emptyMessage = if (uiState.kidsMode) kidsEmptyMessageFor(uiState.kidsTab) else emptyMessageFor(uiState.topMode),
```

Add after `emptyMessageFor`:

```kotlin
private fun kidsEmptyMessageFor(tab: KidsTab): String = when (tab) {
    KidsTab.CHANNELS -> "NO KIDS CHANNELS FOUND"
    KidsTab.FAVORITES -> "NO FAVORITES YET — TAP ★ ON A CHANNEL"
}
```

- [ ] **Step 4: Send a relocked child back to the channel list**

In `MainActivity.kt` add imports `androidx.compose.runtime.LaunchedEffect`, then in `TunerApp` after `var showSplash ...` add:

```kotlin
    val uiState by viewModel.uiState.collectAsState()

    // If the parent session ends (app backgrounded, 10 min timeout) while Settings or the
    // custom-source manager is open in Kids Mode, don't leave the child sitting on it.
    LaunchedEffect(uiState.kidsMode, uiState.parentUnlocked) {
        if (uiState.kidsMode && !uiState.parentUnlocked) screen = Screen.CHANNELS
    }
```

- [ ] **Step 5: Build and install**

Run: `.\gradlew.bat :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device.`

- [ ] **Step 6: Manual check on device**

1. Settings → unlock → turn Kids Mode on → Back. Title shows **KIDS** badge; toolbar shows "Kids channels" / "Favorites"; link (custom sources) icon gone; list shows kids/animation channels.
2. Search "cartoon" → filters within kids channels only.
3. Star a kids channel → appears in Favorites tab. A non-kids favorite from before does not appear.
4. Press Home, reopen → tap Settings → PIN pad appears (relocked). Cancel → stays on list.
5. Force-stop and reopen → Kids Mode still on.
6. Enter PIN → Settings → turn Kids Mode off → Back → previous tab (Catalog/Favorites/…) restored.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/tuner/ui/parental/KidsTabsToolbar.kt app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt app/src/main/java/com/example/tuner/MainActivity.kt
git commit -m "feat(parental): show Kids Mode tabs, badge and PIN-gated settings"
```

---

### Task 9: Per-channel shield buttons

**Files:**
- Create: `app/src/main/java/com/example/tuner/ui/parental/KidsShieldButton.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/components/ChannelRow.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/components/ChannelGridTile.kt`
- Modify: `app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt`

**Interfaces:**
- Consumes: `KidsShieldState` (Task 3); `ChannelListUiState.kidsShieldStateFor`, `parentUnlocked`; `ChannelListViewModel.onKidsShieldClick` (Task 5).
- Produces:
  - `@Composable fun KidsShieldButton(state: KidsShieldState, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 22.dp)`
  - `ChannelRow(..., shieldState: KidsShieldState? = null, onShieldClick: () -> Unit = {}, modifier)`
  - `ChannelGridTile(..., shieldState: KidsShieldState? = null, onShieldClick: () -> Unit = {}, modifier)`
  - Private `ChannelList(...)` gains `shieldStateFor: ((Channel) -> KidsShieldState)?` and `onShieldClick: (Channel) -> Unit`.

- [ ] **Step 1: Create `KidsShieldButton.kt`**

```kotlin
package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tuner.parental.KidsShieldState
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextSecondary

/** Shown on channel rows/tiles only while a parent is unlocked. */
@Composable
fun KidsShieldButton(
    state: KidsShieldState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp
) {
    val icon = when (state) {
        KidsShieldState.VISIBLE_IN_KIDS, KidsShieldState.APPROVED -> Icons.Filled.Shield
        KidsShieldState.HIDDEN -> Icons.Filled.RemoveModerator
        KidsShieldState.NEUTRAL -> Icons.Outlined.Shield
    }
    val tint = when (state) {
        KidsShieldState.VISIBLE_IN_KIDS, KidsShieldState.APPROVED -> TunerAmber
        KidsShieldState.HIDDEN -> TunerRed
        KidsShieldState.NEUTRAL -> TunerTextSecondary
    }
    val description = when (state) {
        KidsShieldState.VISIBLE_IN_KIDS -> "Hide from kids"
        KidsShieldState.APPROVED -> "Remove from kids"
        KidsShieldState.HIDDEN, KidsShieldState.NEUTRAL -> "Allow for kids"
    }
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}
```

- [ ] **Step 2: Add the shield slot to `ChannelRow`**

Add imports `import com.example.tuner.parental.KidsShieldState` and `import com.example.tuner.ui.parental.KidsShieldButton`. Change the signature to:

```kotlin
fun ChannelRow(
    index: Int,
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    showSourceTag: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    shieldState: KidsShieldState? = null,
    onShieldClick: () -> Unit = {},
    modifier: Modifier = Modifier
)
```

Insert directly before the favorite `IconButton(onClick = onToggleFavorite, ...)`:

```kotlin
        if (shieldState != null) {
            KidsShieldButton(state = shieldState, onClick = onShieldClick, modifier = Modifier.size(32.dp))
        }
```

- [ ] **Step 3: Add the shield slot to `ChannelGridTile`**

Add the same two imports. Change the signature to:

```kotlin
fun ChannelGridTile(
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    shieldState: KidsShieldState? = null,
    onShieldClick: () -> Unit = {},
    modifier: Modifier = Modifier
)
```

Insert after the favorite `IconButton(...) { ... }` block, still inside the outer `Box`:

```kotlin
        if (shieldState != null) {
            KidsShieldButton(
                state = shieldState,
                onClick = onShieldClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp),
                iconSize = 18.dp
            )
        }
```

- [ ] **Step 4: Pass shields through `ChannelList`**

In `ChannelListScreen.kt` add import `import com.example.tuner.parental.KidsShieldState`.

Add two parameters to the private `ChannelList` after `onToggleFavorite: (Channel) -> Unit,`:

```kotlin
    shieldStateFor: ((Channel) -> KidsShieldState)?,
    onShieldClick: (Channel) -> Unit,
```

In the grid `ChannelGridTile(...)` call add:

```kotlin
                                shieldState = shieldStateFor?.invoke(numbered.channel),
                                onShieldClick = { onShieldClick(numbered.channel) },
```

In the list `ChannelRow(...)` call add:

```kotlin
                                shieldState = shieldStateFor?.invoke(numbered.channel),
                                onShieldClick = { onShieldClick(numbered.channel) },
```

In `ChannelListPane`'s `ChannelList(...)` call add after `onToggleFavorite = viewModel::toggleFavorite,`:

```kotlin
            shieldStateFor = if (uiState.parentUnlocked) uiState::kidsShieldStateFor else null,
            onShieldClick = viewModel::onKidsShieldClick,
```

- [ ] **Step 5: Build and install**

Run: `.\gradlew.bat :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device.`

- [ ] **Step 6: Manual check on device**

1. Locked: no shields on any row or tile.
2. Settings → unlock → Back (Kids Mode off). Every row shows an outline shield. Tap one on a non-kids channel (e.g. a news channel) → filled amber shield.
3. Settings → Kids Mode on → Back. That news channel now appears in the kids list. Tap its shield ("Hide from kids") → it disappears.
4. Turn Kids Mode off → the same channel shows the red slashed shield. Tap it → filled amber (approved again).
5. Switch to grid view → shields appear top-left on tiles, stars top-right.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/tuner/ui/parental/KidsShieldButton.kt app/src/main/java/com/example/tuner/ui/components/ChannelRow.kt app/src/main/java/com/example/tuner/ui/components/ChannelGridTile.kt app/src/main/java/com/example/tuner/ui/channels/ChannelListScreen.kt
git commit -m "feat(parental): add hide/allow shield buttons to channels"
```

---

### Task 10: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run all unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; `MathChallengeTest`, `PinHasherTest`, `LockoutPolicyTest`, `KidsVisibilityTest`, `M3UParserTest` all pass.

- [ ] **Step 2: Clean install and walk the spec's manual checklist**

Run: `.\gradlew.bat :app:installDebug`, then on the device repeat Task 7 Step 4, Task 8 Step 6 and Task 9 Step 6 in order, plus:
- While a news channel plays, turn Kids Mode on → playback stops once the kids list loads.
- Kids Mode on, parent unlocked, wait 10 minutes (or temporarily set `UNLOCK_WINDOW_MILLIS` to 30 s for the check and revert) → shields disappear; Settings asks for PIN.
- TURN OFF parental control → Kids Mode off, section shows SET UP again.

- [ ] **Step 3: Check for crashes**

Run: `& "C:/Program Files (x86)/Android/android-sdk/platform-tools/adb.exe" logcat -d -b crash`
Expected: no new `FATAL EXCEPTION` for `com.example.tuner`.
