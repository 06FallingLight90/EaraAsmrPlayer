package com.asmr.player.ui.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.asmr.player.ui.library.albumdetail.albumLandscapeArtworkRight
import com.asmr.player.ui.library.albumdetail.albumLandscapeCollapseDistance
import com.asmr.player.ui.library.albumdetail.albumLandscapeCollapseProgress
import com.asmr.player.ui.library.albumdetail.albumLandscapeCoverScale
import com.asmr.player.ui.library.albumdetail.albumLandscapeCoverShadowAlpha
import com.asmr.player.ui.library.albumdetail.albumLandscapeDirectoryTop
import com.asmr.player.ui.library.albumdetail.albumLandscapeHeaderStart
import com.asmr.player.ui.library.albumdetail.albumLandscapePaneViewportHeightPx
import com.asmr.player.ui.library.albumdetail.albumLandscapePlaybackProgress
import com.asmr.player.ui.library.albumdetail.albumLandscapePulseEnabled
import com.asmr.player.ui.library.albumdetail.albumLandscapePulseSweepFraction
import com.asmr.player.ui.library.albumdetail.albumLandscapeSpectrumOffsetY
import com.asmr.player.ui.library.albumdetail.albumLandscapeSpectrumTranslationY
import com.asmr.player.ui.library.albumdetail.albumLandscapeSurfaceHeight
import com.asmr.player.ui.library.albumdetail.isVideoPreviewUrl
import com.asmr.player.ui.library.albumdetail.resolveStableAlbumHeroIdentity
import com.asmr.player.ui.library.albumdetail.shouldUseAlbumDetailLandscapeLayout
import com.asmr.player.ui.library.albumdetail.StableAlbumHeroIdentity

/**
 * 钉住 AlbumDetailScreen.kt 内待拆纯逻辑的现行为（S11 前置安全网）：
 * 横屏布局数学、视频预览 URL 判定、封面身份占位回退。
 * 期望值按语义独立推导（布局契约常量：ArtworkStart=68dp、ArtworkContentGap=12dp、
 * SpectrumTopPadding=12dp、SpectrumArtworkOffset=0.15、SpectrumCollapseFollow=0.82、
 * CoverShadowStartAlpha=0.16、CollapseDistance=artwork*0.28 上限 128dp）。
 */
class AlbumDetailScreenSupportTest {
    // ---------- 横屏布局选择：仅"非紧凑宽 + 严格横放" ----------

    @Test
    fun shouldUseAlbumDetailLandscapeLayout_onlyForNonCompactWiderThanTall() {
        assertTrue(shouldUseAlbumDetailLandscapeLayout(false, 800, 600))
        assertFalse(shouldUseAlbumDetailLandscapeLayout(false, 600, 800))
        // 紧凑宽优先否决（600-840dp 中间档语义：宽度档与方向不可互换）
        assertFalse(shouldUseAlbumDetailLandscapeLayout(true, 800, 600))
        // 正方形不算横放
        assertFalse(shouldUseAlbumDetailLandscapeLayout(false, 800, 800))
    }

    // ---------- 横屏几何 ----------

    @Test
    fun albumLandscapeHeaderStart_andArtworkRight_followLayoutContract() {
        assertEquals(280.dp, albumLandscapeHeaderStart(200.dp)) // 68 + 200 + 12
        assertEquals(268.dp, albumLandscapeArtworkRight(200.dp)) // 68 + 200
    }

    @Test
    fun albumLandscapeCollapseDistance_scalesAndCaps() {
        assertEquals(28.dp, albumLandscapeCollapseDistance(100.dp)) // 100 * 0.28
        assertEquals(128.dp, albumLandscapeCollapseDistance(500.dp)) // 140 → 上限 128
    }

    @Test
    fun albumLandscapeCollapseProgress_ratioClamped() {
        assertEquals(0f, albumLandscapeCollapseProgress(50f, 0f)) // max<=0 → 0
        assertEquals(0.5f, albumLandscapeCollapseProgress(50f, 100f))
        assertEquals(1f, albumLandscapeCollapseProgress(150f, 100f))
        assertEquals(0f, albumLandscapeCollapseProgress(-10f, 100f))
    }

    @Test
    fun albumLandscapeCoverScale_shrinksUpTo30Percent() {
        assertEquals(1f, albumLandscapeCoverScale(0f, 100f))
        assertEquals(0.7f, albumLandscapeCoverScale(100f, 100f))
        assertEquals(0.85f, albumLandscapeCoverScale(50f, 100f))
    }

    @Test
    fun albumLandscapePlaybackProgress_positionOverDuration() {
        assertEquals(0f, albumLandscapePlaybackProgress(10_000L, 0L)) // duration<=0 → 0
        assertEquals(0.5f, albumLandscapePlaybackProgress(30_000L, 60_000L))
        assertEquals(1f, albumLandscapePlaybackProgress(90_000L, 60_000L))
    }

    @Test
    fun albumLandscapePulseSweepFraction_easesOutQuadratic() {
        assertEquals(0f, albumLandscapePulseSweepFraction(0f))
        assertEquals(1f, albumLandscapePulseSweepFraction(1f))
        assertEquals(0.75f, albumLandscapePulseSweepFraction(0.5f)) // 1 - (1-0.5)^2
        assertEquals(1f, albumLandscapePulseSweepFraction(1.5f)) // 越界钳到 1
    }

    @Test
    fun albumLandscapeCoverShadowAlpha_fadesInAfterStartAlpha() {
        assertEquals(0f, albumLandscapeCoverShadowAlpha(0f))
        assertEquals(0f, albumLandscapeCoverShadowAlpha(0.16f)) // 起点前不产生阴影
        assertEquals(0.5f, albumLandscapeCoverShadowAlpha(0.58f), 1e-6f) // (0.58-0.16)/0.84
        assertEquals(1f, albumLandscapeCoverShadowAlpha(1f))
    }

    @Test
    fun albumLandscapeDirectoryTop_headerMinusLiftPlus4ClampedToZero() {
        assertEquals(54.dp, albumLandscapeDirectoryTop(100.dp, 50.dp))
        assertEquals(0.dp, albumLandscapeDirectoryTop(10.dp, 50.dp)) // 负值钳 0
    }

    @Test
    fun albumLandscapePulseEnabled_requiresPlayingAndProgress() {
        assertTrue(albumLandscapePulseEnabled(true, 0.5f))
        assertFalse(albumLandscapePulseEnabled(true, 0f))
        assertFalse(albumLandscapePulseEnabled(false, 0.5f))
    }

    @Test
    fun albumLandscapeSurfaceHeight_viewportPlusCollapseDistance() {
        assertEquals(456.dp, albumLandscapeSurfaceHeight(400.dp, 200.dp)) // 400 + 56
    }

    @Test
    fun albumLandscapePaneViewportHeightPx_shrinksByHiddenBottom() {
        assertEquals(0, albumLandscapePaneViewportHeightPx(0, 50f, 100f)) // 非法表面高 → 0
        assertEquals(400, albumLandscapePaneViewportHeightPx(500, 100f, 200f)) // 隐藏 100
        assertEquals(500, albumLandscapePaneViewportHeightPx(500, 200f, 200f)) // 全展开 → 全高
        assertEquals(500, albumLandscapePaneViewportHeightPx(500, 300f, 200f)) // 越界钳满
    }

    @Test
    fun albumLandscapeSpectrumGeometry_followsContract() {
        assertEquals(42.dp, albumLandscapeSpectrumOffsetY(200.dp)) // 12 + 200*0.15
        assertEquals(-82f, albumLandscapeSpectrumTranslationY(100f)) // -100*0.82
        assertEquals(0f, albumLandscapeSpectrumTranslationY(-50f), 0f) // 负 collapse 不上浮（-0.0 视同 0）
    }

    // ---------- isVideoPreviewUrl ----------

    @Test
    fun isVideoPreviewUrl_detectsVideoExtensionsAfterStrippingQueryAndFragment() {
        assertTrue(isVideoPreviewUrl("https://example.com/a.mp4"))
        assertTrue(isVideoPreviewUrl("https://example.com/a.MP4"))
        assertTrue(isVideoPreviewUrl("https://example.com/a.mkv"))
        assertTrue(isVideoPreviewUrl("https://example.com/a.webm"))
        assertTrue(isVideoPreviewUrl("https://example.com/a.m3u8"))
        // query / fragment 先剥离再判后缀
        assertTrue(isVideoPreviewUrl("https://example.com/a.mp4?token=x"))
        assertTrue(isVideoPreviewUrl("https://example.com/a.MKV#t=5"))
        assertFalse(isVideoPreviewUrl("https://example.com/a.mp3"))
        assertFalse(isVideoPreviewUrl("https://example.com/a.mp4.txt")) // 只看末尾后缀
        assertFalse(isVideoPreviewUrl(""))
    }

    // ---------- 封面身份占位回退 ----------

    @Test
    fun resolveStableAlbumHeroIdentity_fillsPlaceholderTitleFromCurrent() {
        val resolved = resolveStableAlbumHeroIdentity(
            stable = StableAlbumHeroIdentity(title = "专辑", rj = "RJ1", circle = ""),
            current = StableAlbumHeroIdentity(title = "真实标题", rj = "RJ1", circle = "社团")
        )
        assertEquals("真实标题", resolved.title)
        assertEquals("RJ1", resolved.rj)
        assertEquals("社团", resolved.circle)
    }

    @Test
    fun resolveStableAlbumHeroIdentity_stableNonPlaceholderTitleWins() {
        val resolved = resolveStableAlbumHeroIdentity(
            stable = StableAlbumHeroIdentity(title = "既有标题", rj = "RJ1", circle = "既有社团"),
            current = StableAlbumHeroIdentity(title = "新标题", rj = "RJ2", circle = "新社团")
        )
        assertEquals("既有标题", resolved.title)
        assertEquals("既有社团", resolved.circle) // stable 非空不回退
    }

    @Test
    fun resolveStableAlbumHeroIdentity_titleEqualToRjCountsAsPlaceholder() {
        val resolved = resolveStableAlbumHeroIdentity(
            stable = StableAlbumHeroIdentity(title = "rj1", rj = "RJ1", circle = "C"),
            current = StableAlbumHeroIdentity(title = "真实标题", rj = "RJ1", circle = "C")
        )
        assertEquals("真实标题", resolved.title)
    }

    @Test
    fun resolveStableAlbumHeroIdentity_unresolvedCurrentDoesNotOverwritePlaceholder() {
        val resolved = resolveStableAlbumHeroIdentity(
            stable = StableAlbumHeroIdentity(title = "专辑", rj = "RJ1", circle = ""),
            current = StableAlbumHeroIdentity(title = "专辑", rj = "RJ1", circle = "")
        )
        assertEquals("专辑", resolved.title)
    }

    @Test
    fun resolveStableAlbumHeroIdentity_blankStableFallsBackToCurrent() {
        val resolved = resolveStableAlbumHeroIdentity(
            stable = StableAlbumHeroIdentity(title = "专辑", rj = "", circle = ""),
            current = StableAlbumHeroIdentity(title = "真实标题", rj = "RJ2", circle = "社团")
        )
        assertEquals("真实标题", resolved.title)
        assertEquals("RJ2", resolved.rj)
        assertEquals("社团", resolved.circle)
    }
}
