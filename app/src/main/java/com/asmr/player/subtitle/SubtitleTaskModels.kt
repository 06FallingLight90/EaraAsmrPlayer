package com.asmr.player.subtitle

import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.SubtitleFallbackCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleTranslationSourceEntity

internal data class GeneratedTranslationLayout(
    val sources: List<SubtitleTranslationSourceEntity>,
    val fallbackCaptions: List<SubtitleFallbackCaptionEntity>
)

internal fun buildGeneratedTranslationLayout(
    itemId: String,
    captions: List<GeneratedSubtitle>
): GeneratedTranslationLayout {
    require(itemId.isNotBlank())
    require(captions.isNotEmpty())
    val sources = captions.mapIndexed { index, caption ->
        SubtitleTranslationSourceEntity(
            itemId = itemId,
            sourceIndex = index,
            startMs = caption.startMs,
            endMs = caption.endMs,
            text = caption.text
        )
    }
    return GeneratedTranslationLayout(
        sources = sources,
        fallbackCaptions = captions.mapIndexed { index, caption ->
            SubtitleFallbackCaptionEntity(
                itemId = itemId,
                captionIndex = index,
                firstSourceIndex = index,
                lastSourceIndex = index,
                startMs = caption.startMs,
                endMs = caption.endMs,
                text = caption.text
            )
        }
    )
}

internal fun rebuildGeneratedFallbackSuffix(
    trackId: Long,
    confirmedSourceCount: Int,
    sources: List<SubtitleTranslationSourceEntity>,
    fallback: List<SubtitleFallbackCaptionEntity>
): List<SubtitleEntity> = fallback.flatMap { caption ->
    when {
        caption.lastSourceIndex < confirmedSourceCount -> emptyList()
        caption.firstSourceIndex >= confirmedSourceCount -> listOf(
            SubtitleEntity(
                trackId = trackId,
                startMs = caption.startMs,
                endMs = caption.endMs,
                text = caption.text,
                japaneseText = caption.text
            )
        )
        else -> sources.asSequence()
            .drop(confirmedSourceCount)
            .takeWhile { source -> source.sourceIndex <= caption.lastSourceIndex }
            .map { source ->
                SubtitleEntity(
                    trackId = trackId,
                    startMs = source.startMs,
                    endMs = source.endMs,
                    text = source.text,
                    japaneseText = source.text
                )
            }
            .toList()
    }
}

