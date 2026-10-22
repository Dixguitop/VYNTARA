package com.vyntara.editor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vyntara.editor.capabilities.DeviceProfile
import com.vyntara.editor.settings.AppSettings
import com.vyntara.editor.settings.AppThemeId
import com.vyntara.editor.settings.AutoSaveInterval
import com.vyntara.editor.settings.CardCorners
import com.vyntara.editor.settings.PreviewQuality
import com.vyntara.editor.settings.UiDensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vyntara_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_id")
        val DENSITY = stringPreferencesKey("ui_density")
        val CORNERS = stringPreferencesKey("card_corners")
        val ACCENT = longPreferencesKey("accent_override")
        val NAV_LABELS = booleanPreferencesKey("show_nav_labels")
        val LANGUAGE = stringPreferencesKey("language_code")
        val PREVIEW_Q = stringPreferencesKey("preview_quality")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen_on")
        val HAPTICS = booleanPreferencesKey("haptics")
        val THERMAL = booleanPreferencesKey("thermal_guard")
        val DEF_RES = intPreferencesKey("default_resolution")
        val DEF_FPS = intPreferencesKey("default_fps")
        val DEF_ASPECT = stringPreferencesKey("default_aspect")
        val SNAP = booleanPreferencesKey("snap_enabled")
        val GRID = booleanPreferencesKey("grid_enabled")
        val TIMELINE_H = intPreferencesKey("timeline_height")
        val AUTO_SAVE = stringPreferencesKey("auto_save")
        val CINEMATIC = booleanPreferencesKey("cinematic_boot_enabled")
        val BOOT_SOUND = booleanPreferencesKey("boot_sound_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val SORT_NEWEST = booleanPreferencesKey("projects_sort_newest")
        val FIRST_RUN = booleanPreferencesKey("first_run_done")
        val CACHED_PROFILE = stringPreferencesKey("cached_device_profile")
        val CACHED_APP_VERSION = intPreferencesKey("cached_app_version")
        val CACHED_ANDROID_SDK = intPreferencesKey("cached_android_sdk")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeId = runCatching { AppThemeId.valueOf(p[Keys.THEME] ?: "CRIMSON") }
                .getOrDefault(AppThemeId.CRIMSON),
            uiDensity = runCatching { UiDensity.valueOf(p[Keys.DENSITY] ?: "COMFORTABLE") }
                .getOrDefault(UiDensity.COMFORTABLE),
            cardCorners = runCatching { CardCorners.valueOf(p[Keys.CORNERS] ?: "ROUNDED") }
                .getOrDefault(CardCorners.ROUNDED),
            accentOverrideArgb = p[Keys.ACCENT] ?: 0L,
            showNavLabels = p[Keys.NAV_LABELS] ?: true,
            languageCode = p[Keys.LANGUAGE] ?: "es",
            previewQuality = runCatching { PreviewQuality.valueOf(p[Keys.PREVIEW_Q] ?: "AUTO") }
                .getOrDefault(PreviewQuality.AUTO),
            keepScreenOn = p[Keys.KEEP_SCREEN] ?: true,
            haptics = p[Keys.HAPTICS] ?: true,
            thermalGuard = p[Keys.THERMAL] ?: true,
            defaultResolutionLongSide = p[Keys.DEF_RES] ?: 1080,
            defaultFps = p[Keys.DEF_FPS] ?: 30,
            defaultAspectLabel = p[Keys.DEF_ASPECT] ?: "16:9",
            snapEnabled = p[Keys.SNAP] ?: true,
            gridEnabled = p[Keys.GRID] ?: false,
            timelineHeightLevel = p[Keys.TIMELINE_H] ?: 1,
            autoSave = runCatching {
                AutoSaveInterval.valueOf(p[Keys.AUTO_SAVE] ?: "S60")
            }.getOrDefault(AutoSaveInterval.S60),
            cinematicBoot = p[Keys.CINEMATIC] ?: true,
            bootSound = p[Keys.BOOT_SOUND] ?: false,
            reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
            projectsSortNewestFirst = p[Keys.SORT_NEWEST] ?: true
        )
    }

    // Compatibility aliases used by BootViewModel
    val cinematicEnabled: Flow<Boolean> = settings.map { it.cinematicBoot }
    val firstRunDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_RUN] ?: false }
    val soundEnabled: Flow<Boolean> = settings.map { it.bootSound }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        context.dataStore.edit { p ->
            p[Keys.THEME] = next.themeId.name
            p[Keys.DENSITY] = next.uiDensity.name
            p[Keys.CORNERS] = next.cardCorners.name
            p[Keys.ACCENT] = next.accentOverrideArgb
            p[Keys.NAV_LABELS] = next.showNavLabels
            p[Keys.LANGUAGE] = next.languageCode
            p[Keys.PREVIEW_Q] = next.previewQuality.name
            p[Keys.KEEP_SCREEN] = next.keepScreenOn
            p[Keys.HAPTICS] = next.haptics
            p[Keys.THERMAL] = next.thermalGuard
            p[Keys.DEF_RES] = next.defaultResolutionLongSide
            p[Keys.DEF_FPS] = next.defaultFps
            p[Keys.DEF_ASPECT] = next.defaultAspectLabel
            p[Keys.SNAP] = next.snapEnabled
            p[Keys.GRID] = next.gridEnabled
            p[Keys.TIMELINE_H] = next.timelineHeightLevel
            p[Keys.AUTO_SAVE] = next.autoSave.name
            p[Keys.CINEMATIC] = next.cinematicBoot
            p[Keys.BOOT_SOUND] = next.bootSound
            p[Keys.REDUCE_MOTION] = next.reduceMotion
            p[Keys.SORT_NEWEST] = next.projectsSortNewestFirst
        }
    }

    suspend fun setCinematicEnabled(enabled: Boolean) {
        update { it.copy(cinematicBoot = enabled) }
    }

    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[Keys.FIRST_RUN] = true }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        update { it.copy(bootSound = enabled) }
    }

    suspend fun resetAll() {
        context.dataStore.edit { prefs ->
            val keepProfile = prefs[Keys.CACHED_PROFILE]
            val keepVer = prefs[Keys.CACHED_APP_VERSION]
            val keepSdk = prefs[Keys.CACHED_ANDROID_SDK]
            val first = prefs[Keys.FIRST_RUN]
            prefs.clear()
            if (keepProfile != null) prefs[Keys.CACHED_PROFILE] = keepProfile
            if (keepVer != null) prefs[Keys.CACHED_APP_VERSION] = keepVer
            if (keepSdk != null) prefs[Keys.CACHED_ANDROID_SDK] = keepSdk
            if (first != null) prefs[Keys.FIRST_RUN] = first
        }
    }

    suspend fun getCachedProfile(currentAppVersion: Int, currentSdk: Int): DeviceProfile? {
        val prefs = context.dataStore.data.first()
        val cachedVersion = prefs[Keys.CACHED_APP_VERSION] ?: -1
        val cachedSdk = prefs[Keys.CACHED_ANDROID_SDK] ?: -1
        if (cachedVersion != currentAppVersion || cachedSdk != currentSdk) return null
        val json = prefs[Keys.CACHED_PROFILE] ?: return null
        return DeviceProfile.fromJson(json)
    }

    suspend fun saveProfile(profile: DeviceProfile, appVersion: Int, sdk: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_PROFILE] = profile.toJson()
            prefs[Keys.CACHED_APP_VERSION] = appVersion
            prefs[Keys.CACHED_ANDROID_SDK] = sdk
        }
    }
}
