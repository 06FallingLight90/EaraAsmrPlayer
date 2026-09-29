package com.asmr.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 LibraryViewModel / AlbumDetailViewModel 共享的元数据纯函数现行为。
 * 期望值由 TagNormalizer 规格（trim→NFKC→小写→分隔符归空→去全部空白）独立推导。
 */
class AlbumMetadataSupportTest {
    @Test
    fun buildTagsToken_normalizesDeduplicatesAndJoinsWithSpace() {
        // ＣＧ集 经 NFKC+小写 → cg集；iya-Go 的连字符归空后去空白 → iyago；空段被滤除
        assertEquals("ボイス cg集 iyago", buildTagsToken(" ボイス , ＣＧ集 ,, iya-Go "))
    }

    @Test
    fun buildTagsToken_treatsCaseAndSeparatorVariantsAsSameToken() {
        // A→a；a_b 与 a-b 归一后同为 ab；三者去重后剩 a、ab
        assertEquals("a ab", buildTagsToken("A, a_b, a-b"))
    }

    @Test
    fun buildTagsToken_blankCsvYieldsEmptyToken() {
        assertEquals("", buildTagsToken(" ,, "))
    }

    @Test
    fun parseAlbumTags_keepsRawNameAndDeduplicatesByNormalizedForm() {
        // 保留首个原样名（trim 后），重复按归一形去重；ＣＧ集 的归一形为 cg集
        assertEquals(
            listOf("ボイス" to "ボイス", "ＣＧ集" to "cg集"),
            parseAlbumTags(" ボイス , ＣＧ集 ,, ボイス ")
        )
    }

    @Test
    fun parseAlbumTags_dropsBlankSegments() {
        assertEquals(emptyList<Pair<String, String>>(), parseAlbumTags(" ,, "))
    }

    @Test
    fun isLikelyPlaceholderCover_rejectsNonHttpAndKnownPlaceholderPatterns() {
        assertTrue(isLikelyPlaceholderCover(""))
        assertTrue(isLikelyPlaceholderCover("   "))
        assertTrue(isLikelyPlaceholderCover("ftp://example.com/a.jpg"))
        assertTrue(isLikelyPlaceholderCover("http://example.com/0.jpg"))
        assertTrue(isLikelyPlaceholderCover("https://example.com/img/0.png"))
        assertTrue(isLikelyPlaceholderCover(" https://Example.com/No_Image.PNG "))
        assertTrue(isLikelyPlaceholderCover("https://example.com/no-image.webp"))
    }

    @Test
    fun isLikelyPlaceholderCover_acceptsRegularHttpCover() {
        assertFalse(isLikelyPlaceholderCover("https://example.com/cover/main.jpg"))
        assertFalse(isLikelyPlaceholderCover("HTTPS://Example.COM/Main.JPG"))
    }
}
