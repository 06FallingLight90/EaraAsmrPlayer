package com.asmr.player.data.local.metadata

import android.media.MediaMetadataRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 纯 JVM：MediaMetadataRetriever 的 METADATA_KEY_* 为编译期常量，
 * 引用处内联后不触发框架类加载，映射纯函数可直接单测。
 */
class AudioMetadataMappingTest {

    @Test
    fun fullFields_keptAfterTrim() {
        val extracted = mapOf<Int, String?>(
            MediaMetadataRetriever.METADATA_KEY_TITLE to " 01. 白色季节 ",
            MediaMetadataRetriever.METADATA_KEY_ARTIST to "CV A",
            MediaMetadataRetriever.METADATA_KEY_ALBUM to "RJ12345678",
        )

        val metadata = AudioMetadataMapping.fromExtracted(extracted, cover = ByteArray(3) { it.toByte() })

        assertEquals("01. 白色季节", metadata.title)
        assertEquals("CV A", metadata.artist)
        assertEquals("RJ12345678", metadata.album)
        assertEquals(3, metadata.embeddedCover?.size)
    }

    @Test
    fun partialMissing_blankTextAndEmptyCoverFallBackToNull() {
        val extracted = mapOf<Int, String?>(
            MediaMetadataRetriever.METADATA_KEY_TITLE to "01. 白色季节",
            MediaMetadataRetriever.METADATA_KEY_ARTIST to "   ",
            MediaMetadataRetriever.METADATA_KEY_ALBUM to null,
        )

        val metadata = AudioMetadataMapping.fromExtracted(extracted, cover = ByteArray(0))

        assertEquals("01. 白色季节", metadata.title)
        assertNull(metadata.artist)
        assertNull(metadata.album)
        assertNull(metadata.embeddedCover)
    }

    @Test
    fun allMissing_everyFieldNull() {
        val metadata = AudioMetadataMapping.fromExtracted(emptyMap(), cover = null)

        assertNull(metadata.title)
        assertNull(metadata.artist)
        assertNull(metadata.album)
        assertNull(metadata.embeddedCover)
    }
}
