package com.robcloud.bloodpressure.ocr

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "vision_server_prefs"
private const val KEY_PROFILES = "profiles"
private const val KEY_ACTIVE_ID = "active_id"

// Single-config keys from before multiple profiles were supported. Read once to migrate an
// existing setup into a profile (see migrateLegacyIfNeeded), then cleared: LEGACY_KEY_API_KEY in
// particular is a plaintext secret, and leaving it on disk indefinitely after it's already been
// copied into the migrated profile — including after that profile is itself later edited or
// deleted — has no upside, unlike the other prefs this app keeps around for good.
private const val LEGACY_KEY_BASE_URL = "base_url"
private const val LEGACY_KEY_MODEL = "model"
private const val LEGACY_KEY_API_KEY = "api_key"

/**
 * One saved server to scan a reading with — a self-hosted model, or a cloud provider's free tier,
 * anything that speaks the OpenAI chat-completions API with image input. [id] is stable across
 * edits, so selecting or deleting one doesn't depend on its position in the list.
 */
data class VisionServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String?
)

/** Everything [scanReadingPhoto] needs — the active profile, reduced to just that. */
data class VisionServerConfig(val baseUrl: String, val model: String, val apiKey: String?)

internal fun encodeProfiles(profiles: List<VisionServerProfile>): String {
    val array = JSONArray()
    profiles.forEach { profile ->
        array.put(
            JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("baseUrl", profile.baseUrl)
                put("model", profile.model)
                put("apiKey", profile.apiKey ?: JSONObject.NULL)
            }
        )
    }
    return array.toString()
}

/** Never throws, and a corrupted or unrecognisable entry (a future format change, a bad write)
 *  is dropped on its own rather than discarding every other saved profile along with it — the
 *  mistake [ReminderStore.decodeTimes] already avoids for the same reason. */
internal fun decodeProfiles(json: String): List<VisionServerProfile> {
    if (json.isBlank()) return emptyList()
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        runCatching {
            val o = array.getJSONObject(i)
            VisionServerProfile(
                id = o.getString("id"),
                name = o.getString("name"),
                baseUrl = o.getString("baseUrl"),
                model = o.getString("model"),
                apiKey = if (o.isNull("apiKey")) null else o.getString("apiKey")
            )
        }.getOrNull()
    }
}

/**
 * Where to find the server(s) to scan a reading photo with — any number of them, saved and
 * switched between freely. Entirely optional: nothing is scanned anywhere, and no photo leaves
 * the device, until at least one is added from the About tab.
 */
class VisionServerStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun profiles(): List<VisionServerProfile> {
        migrateLegacyIfNeeded()
        return decodeProfiles(prefs.getString(KEY_PROFILES, "") ?: "")
    }

    private fun saveProfiles(profiles: List<VisionServerProfile>) {
        prefs.edit { putString(KEY_PROFILES, encodeProfiles(profiles)) }
    }

    /** Adds [profile] if its id is new, or replaces the existing one with that id. The very
     *  first profile saved becomes active automatically; later ones don't change the selection. */
    fun upsertProfile(profile: VisionServerProfile) {
        val current = profiles()
        val next = if (current.any { it.id == profile.id }) {
            current.map { if (it.id == profile.id) profile else it }
        } else {
            current + profile
        }
        saveProfiles(next)
        if (activeProfileId == null) activeProfileId = profile.id
    }

    /** Removing the active profile falls back to whatever's left, or to nothing. */
    fun deleteProfile(id: String) {
        val next = profiles().filterNot { it.id == id }
        saveProfiles(next)
        if (activeProfileId == id) activeProfileId = next.firstOrNull()?.id
    }

    var activeProfileId: String?
        get() = prefs.getString(KEY_ACTIVE_ID, null)
        set(value) = prefs.edit { putString(KEY_ACTIVE_ID, value) }

    /**
     * The selected profile, or the first saved one if the selection is unset or stale (pointing
     * at an id that's no longer in the list — e.g. after the active profile's own entry was
     * corrupted and dropped by [decodeProfiles]). That fallback is persisted here, not just
     * returned, so the About tab's radio selection (which compares ids directly) agrees with what
     * a scan actually uses instead of silently falling out of sync with it.
     */
    fun activeProfile(): VisionServerProfile? {
        val saved = profiles()
        val selected = saved.find { it.id == activeProfileId }
        if (selected != null) return selected
        val fallback = saved.firstOrNull()
        activeProfileId = fallback?.id
        return fallback
    }

    /** Null until at least one profile is saved. */
    fun config(): VisionServerConfig? =
        activeProfile()?.let { VisionServerConfig(it.baseUrl, it.model, it.apiKey) }

    /** Runs once per install: turns a pre-multi-profile single config into profile "My server". */
    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_PROFILES)) return
        val legacyUrl = prefs.getString(LEGACY_KEY_BASE_URL, null)
        val legacyModel = prefs.getString(LEGACY_KEY_MODEL, null)
        if (legacyUrl.isNullOrBlank() || legacyModel.isNullOrBlank()) {
            saveProfiles(emptyList()) // marks migration done even when there's nothing to bring over
            clearLegacyKeys()
            return
        }
        val migrated = VisionServerProfile(
            id = UUID.randomUUID().toString(),
            name = "My server",
            baseUrl = legacyUrl,
            model = legacyModel,
            apiKey = prefs.getString(LEGACY_KEY_API_KEY, null)?.ifBlank { null }
        )
        saveProfiles(listOf(migrated))
        activeProfileId = migrated.id
        clearLegacyKeys()
    }

    private fun clearLegacyKeys() {
        prefs.edit {
            remove(LEGACY_KEY_BASE_URL)
            remove(LEGACY_KEY_MODEL)
            remove(LEGACY_KEY_API_KEY)
        }
    }
}
