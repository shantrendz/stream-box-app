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
