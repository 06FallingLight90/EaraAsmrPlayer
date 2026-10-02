package com.asmr.player.ui.library

// Selftest fixture: must be caught by rule 'feature-to-feature' (guard self-check).
// Cross-feature ui.library -> ui.settings import; ui.common/ui.theme and
// same-feature targets are whitelisted and must NOT be caught.
// Not part of any source set; never compiled by Gradle.
import com.asmr.player.ui.settings.SettingsViewModel

class RuleCheckSample
