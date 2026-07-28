package ch.rex.photocollagewallpaper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ch.rex.photocollagewallpaper.domain.MosaicLayout
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.wallpaperSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wallpaper_settings",
)

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.wallpaperSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                folderUri = preferences[Keys.FOLDER_URI],
                gapDp = preferences[Keys.GAP_DP] ?: 2f,
                backgroundArgb = preferences[Keys.BACKGROUND_ARGB] ?: 0xFF000000L,
                fadeEnabled = preferences[Keys.FADE_ENABLED] ?: true,
                refreshToken = preferences[Keys.REFRESH_TOKEN] ?: 0L,
                intervalMillis = normalizeIntervalMillis(
                    preferences[Keys.INTERVAL_MILLIS] ?: DEFAULT_INTERVAL_MILLIS,
                ),
                lastMosaicLayout = MosaicLayout.fromStoredValue(
                    preferences[Keys.LAST_MOSAIC_LAYOUT],
                ),
                lastPhotoUris = preferences[Keys.LAST_PHOTO_URIS]
                    .orEmpty()
                    .lineSequence()
                    .filter(String::isNotBlank)
                    .distinct()
                    .take(MAX_PERSISTED_PHOTO_URIS)
                    .toList(),
            )
        }
        .distinctUntilChanged()

    suspend fun selectFolder(folderUri: String) {
        dataStore.edit { preferences ->
            preferences[Keys.FOLDER_URI] = folderUri
            preferences[Keys.REFRESH_TOKEN] = nextRefreshToken()
            preferences.remove(Keys.LAST_PHOTO_URIS)
            preferences.remove(Keys.LAST_MOSAIC_LAYOUT)
        }
    }

    suspend fun setGapDp(gapDp: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.GAP_DP] = gapDp.coerceIn(0f, 12f)
        }
    }

    suspend fun setBackgroundArgb(backgroundArgb: Long) {
        val opaqueColor = (backgroundArgb and 0x00FFFFFFL) or 0xFF000000L
        dataStore.edit { preferences ->
            preferences[Keys.BACKGROUND_ARGB] = opaqueColor
        }
    }

    suspend fun setFadeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.FADE_ENABLED] = enabled
        }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(
            minimumValue = MIN_INTERVAL_MINUTES,
            maximumValue = MAX_INTERVAL_MINUTES,
        )
        dataStore.edit { preferences ->
            preferences[Keys.INTERVAL_MILLIS] = safeMinutes * MILLIS_PER_MINUTE
        }
    }

    suspend fun saveLastMosaic(
        folderUri: String,
        layout: MosaicLayout,
        photoUris: List<String>,
    ) {
        val encodedUris = photoUris
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_PERSISTED_PHOTO_URIS)
            .joinToString(separator = "\n")
        if (encodedUris.isBlank()) {
            return
        }

        dataStore.edit { preferences ->
            // A slow decode from an old folder must never overwrite the current folder's
            // startup collage.
            if (preferences[Keys.FOLDER_URI] == folderUri) {
                preferences[Keys.LAST_PHOTO_URIS] = encodedUris
                preferences[Keys.LAST_MOSAIC_LAYOUT] = layout.name
            }
        }
    }

    private fun nextRefreshToken(): Long =
        System.currentTimeMillis() xor System.nanoTime()

    private object Keys {
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val GAP_DP = floatPreferencesKey("gap_dp")
        val BACKGROUND_ARGB = longPreferencesKey("background_argb")
        val FADE_ENABLED = booleanPreferencesKey("fade_enabled")
        val REFRESH_TOKEN = longPreferencesKey("refresh_token")
        val INTERVAL_MILLIS = longPreferencesKey("interval_millis")
        val LAST_MOSAIC_LAYOUT = stringPreferencesKey("last_mosaic_layout")
        val LAST_PHOTO_URIS = stringPreferencesKey("last_photo_uris")
    }

    private companion object {
        const val MAX_PERSISTED_PHOTO_URIS = 3
    }
}
