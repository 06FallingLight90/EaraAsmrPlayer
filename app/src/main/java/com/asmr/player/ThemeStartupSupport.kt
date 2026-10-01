package com.asmr.player

import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.main.ThemeMediaSource

internal fun resolveThemeMode(themePref: String, systemDark: Boolean): ThemeMode {
    return when (themePref.trim().lowercase()) {
        "light" -> ThemeMode.Light
        "soft_dark" -> ThemeMode.SoftDark
        "dark" -> ThemeMode.Dark
        "system" -> if (systemDark) ThemeMode.Dark else ThemeMode.Light
        else -> if (systemDark) ThemeMode.Dark else ThemeMode.Light
    }
}

internal fun resolveStaticHueArgb(
    themePref: String,
    staticHueArgbLight: Int?,
    staticHueArgbDark: Int?
): Int? {
    val normalizedThemePref = themePref.trim().lowercase()
    val usesDarkStaticHue = normalizedThemePref == "dark" || normalizedThemePref == "soft_dark"
    return if (usesDarkStaticHue) staticHueArgbDark else staticHueArgbLight
}

internal fun ThemeMediaSource.dynamicThemeSourceKey(): String? {
    val artwork = artworkUri?.toString().orEmpty().trim()
    if (artwork.isNotBlank()) return "artwork:$artwork"

    if (!isVideo) return null
    val video = videoUri?.toString().orEmpty().trim()
    if (video.isBlank()) return null
    return "video:$video"
}

internal fun resolveBootstrapDynamicHueSeedArgb(
    dynamicHueEnabled: Boolean,
    playbackRestoreResolved: Boolean,
    currentSourceKey: String?,
    persistedSourceKey: String?,
    persistedSeedArgb: Int?
): Int? {
    val seedArgb = persistedSeedArgb ?: return null
    val sourceKey = persistedSourceKey?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    if (!dynamicHueEnabled) return null

    return when {
        currentSourceKey == null && !playbackRestoreResolved -> seedArgb
        currentSourceKey == sourceKey -> seedArgb
        else -> null
    }
}

internal fun shouldClearPersistedDynamicHueSeed(
    dynamicHueEnabled: Boolean,
    playbackRestoreResolved: Boolean,
    currentSourceKey: String?,
    persistedSourceKey: String?,
    persistedSeedArgb: Int?
): Boolean {
    if (persistedSeedArgb == null && persistedSourceKey.isNullOrBlank()) return false
    if (!dynamicHueEnabled) return true
    return playbackRestoreResolved && currentSourceKey == null
}
