package com.asmr.player.translation.sample

// Selftest fixture: must be caught by rule 'translation-to-ui' (guard self-check).
// 反向倒挂：translation 层不得依赖 ui 层（Report P0-1 反向倒挂）。
// Not part of any source set; never compiled by Gradle.
import com.asmr.player.ui.theme.AsmrTheme

class RuleCheckSample
