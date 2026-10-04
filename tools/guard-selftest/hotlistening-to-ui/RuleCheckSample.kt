package com.asmr.player.hotlistening.sample

// Selftest fixture: must be caught by rule 'hotlistening-to-ui' (guard self-check).
// 反向倒挂：hotlistening 层不得依赖 ui 层（Report P0-1 反向倒挂）。
// Not part of any source set; never compiled by Gradle.
import com.asmr.player.ui.player.isOnlineMedia

class RuleCheckSample
