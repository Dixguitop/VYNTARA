package com.vyntara.editor.settings

enum class AppThemeId {
    CRIMSON, VIOLET, OCEAN, EMERALD, AMBER
}

enum class UiDensity { COMPACT, COMFORTABLE, SPACIOUS }

enum class CardCorners { SHARP, ROUNDED, PILL }

enum class PreviewQuality { AUTO, LOW, MEDIUM, HIGH }

enum class AutoSaveInterval(val seconds: Int) {
    OFF(0), S30(30), S60(60), S120(120)
}

/**
 * All user-facing preferences. Persisted via DataStore.
 * Designed so future editor features read the same source of truth.
 */
data class AppSettings(
    // Personalization
    val themeId: AppThemeId = AppThemeId.CRIMSON,
    val uiDensity: UiDensity = UiDensity.COMFORTABLE,
    val cardCorners: CardCorners = CardCorners.ROUNDED,
    val accentOverrideArgb: Long = 0L, // 0 = none
    val showNavLabels: Boolean = true,

    // Language
    val languageCode: String = "es",

    // Performance
    val previewQuality: PreviewQuality = PreviewQuality.AUTO,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val thermalGuard: Boolean = true,

    // Editor defaults
    val defaultResolutionLongSide: Int = 1080,
    val defaultFps: Int = 30,
    val defaultAspectLabel: String = "16:9",
    val snapEnabled: Boolean = true,
    val gridEnabled: Boolean = false,
    val timelineHeightLevel: Int = 1, // 0 compact, 1 normal, 2 tall
    val autoSave: AutoSaveInterval = AutoSaveInterval.S60,

    // Boot
    val cinematicBoot: Boolean = true,
    val bootSound: Boolean = false,
    val reduceMotion: Boolean = false,

    // Sort
    val projectsSortNewestFirst: Boolean = true
)
