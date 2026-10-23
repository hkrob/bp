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
// existing setup into a profile (see migrateLegacyIfNeeded); left in place afterward rather than
// removed — harmless to leave, and deleting old prefs keys has its own failure modes for no gain.
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

/** Never throws: a corrupted or unrecognisable value (a future format change, a bad write) is
 *  treated as "no profiles saved" rather than crashing the About tab. */
internal fun decodeProfiles(json: String): List<VisionServerProfile> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VisionServerProfile(
                id = o.getString("id"),
                name = o.getString("name"),
                baseUrl = o.getString("baseUrl"),
                model = o.getString("model"),
                apiKey = if (o.isNull("apiKey")) null else o.getString("apiKey")
            )
        }
    }.getOrDefault(emptyList())
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

    /** The selected profile, or the first saved one if the selection is unset or stale. */
    fun activeProfile(): VisionServerProfile? {
        val saved = profiles()
        return saved.find { it.id == activeProfileId } ?: saved.firstOrNull()
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
    }
}
