package com.asmr.player.subtitle

internal fun fullTranslationRequestCount(totalSources: Int): Int = if (totalSources > 0) 1 else 0
