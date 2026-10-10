package com.asmr.player.ui.groups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T8：批量加入合集 summary 文案钉测（纯 JVM，AlbumGroupsViewModel companion 纯函数）。
 * 三态语义对齐 PlaylistsViewModel.showAddSummary（成功/成功带跳过/全部已存在提示）。
 */
class AlbumGroupsBatchAddTest {

    @Test
    fun addSummaryMessage_fullAdd_reportsSuccessWithoutSkip() {
        val result = AlbumGroupsViewModel.addSummaryMessage(
            added = 3,
            requested = 3,
            targetName = "音声"
        )
        assertEquals("已添加 3 项到合集：音声" to true, result)
    }

    @Test
    fun addSummaryMessage_partialAdd_reportsSuccessWithSkip() {
        val result = AlbumGroupsViewModel.addSummaryMessage(
            added = 2,
            requested = 5,
            targetName = "歌曲"
        )
        assertEquals("已添加 2 项到合集：歌曲，跳过 3 项" to true, result)
    }

    @Test
    fun addSummaryMessage_allSkipped_reportsInfoLevel() {
        val result = AlbumGroupsViewModel.addSummaryMessage(
            added = 0,
            requested = 2,
            targetName = "其它音频"
        )
        assertEquals("所选项目已在合集：其它音频" to false, result)
    }

    @Test
    fun addSummaryMessage_nothingRequested_isSilent() {
        assertNull(AlbumGroupsViewModel.addSummaryMessage(added = 0, requested = 0, targetName = "任意"))
    }

    @Test
    fun normalizeMediaIds_trimsDedupsAndDropsBlanks() {
        assertEquals(
            listOf("/a/1.mp3", "/a/2.mp3"),
            AlbumGroupsViewModel.normalizeMediaIds(listOf(" /a/1.mp3 ", "", "  ", "/a/2.mp3", "/a/1.mp3"))
        )
    }
}
