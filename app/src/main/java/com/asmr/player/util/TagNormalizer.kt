package com.asmr.player.util

import java.text.Normalizer

object TagNormalizer {
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        val nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
        val lower = nfkc.lowercase()
        val replaced = lower
            .replace('·', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('－', ' ')
            .replace('—', ' ')
            .replace('/', ' ')
            .replace('\\', ' ')
        val collapsedSpaces = replaced.replace(Regex("\\s+"), "")
        return collapsedSpaces
    }
}

/** 标签 CSV → 归一、去重后的空格分隔 token（用于 FTS 检索）。 */
internal fun buildTagsToken(tagsCsv: String): String {
    return tagsCsv.split(",")
        .map { TagNormalizer.normalize(it) }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
}

/** 标签 CSV →（原样名, 归一名）列表，按归一形去重。 */
internal fun parseAlbumTags(tagsCsv: String): List<Pair<String, String>> {
    return tagsCsv.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it to TagNormalizer.normalize(it) }
        .filter { it.second.isNotBlank() }
        .distinctBy { it.second }
}

