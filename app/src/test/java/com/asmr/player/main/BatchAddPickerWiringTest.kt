package com.asmr.player.main

import com.asmr.player.domain.model.AllSongsTrackRow
import com.asmr.player.domain.model.BatchAddTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T8：批量加入装配线钉测。BatchPlaylistPickerRequest 缺省目标 = 歌单（存量调用点
 * 语义保持）；toBatchAddMediaItems 的行投影 → MediaItem 字段映射（trackPath 兼任
 * mediaId，album_id/track_id extras 供播放链使用）。MediaItem/Bundle 需 Robolectric。
 */
@RunWith(RobolectricTestRunner::class)
class BatchAddPickerWiringTest {

    @Test
    fun batchRequest_defaultsToPlaylistTarget() {
        assertEquals(
            BatchAddTarget.PLAYLIST,
            BatchPlaylistPickerRequest(items = emptyList()).defaultTarget
        )
        assertEquals(
            BatchAddTarget.GROUP,
            BatchPlaylistPickerRequest(items = emptyList(), defaultTarget = BatchAddTarget.GROUP).defaultTarget
        )
    }

    @Test
    fun toBatchAddMediaItems_mapsRowIdentityFields() {
        val row = AllSongsTrackRow(
            trackId = 7L,
            albumId = 3L,
            trackTitle = "Track A",
            trackPath = "/albums/a/01.mp3",
            duration = 12.0,
            artist = "cv 名",
            albumTitle = "作品标题",
            coverPath = "/covers/a.jpg"
        )

        val items = listOf(row).toBatchAddMediaItems()

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("/albums/a/01.mp3", item.mediaId)
        // uri 经 toPlayableUri 的 file:// 包装（本地路径约定）；Windows Robolectric 会把
        // 根路径解析为当前盘符并以 %5C 编码反斜杠，故取解码路径统一分隔符后比对尾部。
        val decodedPath = item.localConfiguration?.uri?.path.orEmpty().replace('\\', '/')
        assertTrue(decodedPath.endsWith("albums/a/01.mp3"))
        assertEquals("Track A", item.mediaMetadata.title?.toString())
        assertEquals("作品标题", item.mediaMetadata.albumTitle?.toString())
        val extras = item.mediaMetadata.extras
        assertEquals(3L, extras?.getLong("album_id"))
        assertEquals(7L, extras?.getLong("track_id"))
    }
}
