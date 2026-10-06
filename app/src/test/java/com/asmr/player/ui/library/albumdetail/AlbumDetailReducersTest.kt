package com.asmr.player.ui.library.albumdetail

import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C8-1 首批纯函数 reducer 的表驱动测试（r3-c8-reducer-plan.md §4"新路径锚"）：
 * 输入为 model + 事件参数，断言输出 model 逐字段等于原 VM 内联 copy 语义的期望值；
 * 守卫早退统一断言返回 null。纯 JVM，无 Robolectric。
 */
class AlbumDetailReducersTest {

    // ------------------------------------------------------------ harness

    private fun album(
        id: Long = 0L,
        title: String = "专辑",
        rjCode: String = "",
        tracks: List<Track> = emptyList()
    ): Album = Album(id = id, title = title, path = "/p/$id", rjCode = rjCode, tracks = tracks)

    private fun model(
        baseRjCode: String = "RJ123",
        rjCode: String = "RJ123",
        listenTogetherRjListenerCount: Int? = null,
        displayAlbum: Album = album(id = 1L, title = "display"),
        localAlbum: Album? = album(id = 2L, title = "local"),
        dlsiteWorkno: String = "RJ123",
        dlsitePlayWorkno: String = "RJ123",
        asmrOneWorkId: String? = "w-1",
        asmrOneSite: Int? = 0,
        asmrOneTree: List<AsmrOneTrackNodeResponse> = listOf(AsmrOneTrackNodeResponse(title = "t")),
        dlsitePlayTree: List<AsmrOneTrackNodeResponse> = emptyList(),
        hasResolvedAsmrOneContent: Boolean = false,
        hasResolvedDlsitePlayContent: Boolean = false,
        isLoadingDlsite: Boolean = false,
        isLoadingDlsiteTrial: Boolean = false,
        isLoadingAsmrOne: Boolean = false,
        isLoadingDlsitePlay: Boolean = false,
        dlsiteTrialTracks: List<Track> = emptyList()
    ): AlbumDetailModel = AlbumDetailModel(
        baseRjCode = baseRjCode,
        rjCode = rjCode,
        listenTogetherRjListenerCount = listenTogetherRjListenerCount,
        displayAlbum = displayAlbum,
        localAlbum = localAlbum,
        dlsiteInfo = null,
        dlsiteGalleryUrls = emptyList(),
        dlsiteTrialTracks = dlsiteTrialTracks,
        dlsiteRecommendations = com.asmr.player.data.remote.scraper.DlsiteRecommendations(),
        dlsiteWorkno = dlsiteWorkno,
        dlsitePlayWorkno = dlsitePlayWorkno,
        dlsiteEditions = emptyList(),
        dlsiteSelectedLang = "ja-jp",
        hasResolvedInitialDlsiteTarget = false,
        hasLoadedInitialDlsiteContent = false,
        hasResolvedAsmrOneContent = hasResolvedAsmrOneContent,
        hasResolvedDlsitePlayContent = hasResolvedDlsitePlayContent,
        preserveHeaderAlbumMetadata = false,
        isDlsiteLanguageUserSelected = false,
        asmrOneWorkId = asmrOneWorkId,
        asmrOneSite = asmrOneSite,
        asmrOneTree = asmrOneTree,
        dlsitePlayTree = dlsitePlayTree,
        isLoadingDlsite = isLoadingDlsite,
        isLoadingDlsiteTrial = isLoadingDlsiteTrial,
        isLoadingAsmrOne = isLoadingAsmrOne,
        isLoadingDlsitePlay = isLoadingDlsitePlay
    )

    // ------------------------------------------------------------ 域 A listenTogether

    @Test
    fun `updateListenTogetherListenerCount guard and transform`() {
        // 守卫：RJ 不匹配 → 不赋值
        assertNull(updateListenTogetherListenerCount(model(), normalizedRj = "RJ999", listenerCount = 5))
        // 守卫：数值相同 → 不赋值
        assertNull(
            updateListenTogetherListenerCount(
                model(listenTogetherRjListenerCount = 5),
                normalizedRj = "RJ123",
                listenerCount = 5
            )
        )
        // 匹配且数值不同才变换；baseRj 空白时回退 rjCode 参与匹配
        val changed = updateListenTogetherListenerCount(model(), normalizedRj = "RJ123", listenerCount = 42)
        assertEquals(42, changed?.listenTogetherRjListenerCount)
        assertEquals(model().displayAlbum, changed?.displayAlbum)
        assertEquals(model().rjCode, changed?.rjCode)

        val fallback = updateListenTogetherListenerCount(
            model(baseRjCode = "", rjCode = "RJ123"),
            normalizedRj = "rj123",
            listenerCount = 7
        )
        assertEquals(7, fallback?.listenTogetherRjListenerCount)
    }

    // ------------------------------------------------------------ 域 B/H resetAsmrOneContent

    @Test
    fun `resetAsmrOneContent resets five asmrOne fields only`() {
        val base = model(hasResolvedAsmrOneContent = true, isLoadingAsmrOne = true, isLoadingDlsite = true)
        val updated = resetAsmrOneContent(base)
        assertNull(updated.asmrOneWorkId)
        assertNull(updated.asmrOneSite)
        assertTrue(updated.asmrOneTree.isEmpty())
        assertFalse(updated.hasResolvedAsmrOneContent)
        assertFalse(updated.isLoadingAsmrOne)
        // 域外字段不动
        assertTrue(updated.isLoadingDlsite)
        assertEquals(base.rjCode, updated.rjCode)
        assertEquals(base.dlsitePlayWorkno, updated.dlsitePlayWorkno)
    }

    // ------------------------------------------------------------ 域 C resetOnlineLoadingFlags / resetDlsitePlayAccess

    @Test
    fun `resetOnlineLoadingFlags clears four loading flags only`() {
        val base = model(
            isLoadingDlsite = true,
            isLoadingDlsiteTrial = true,
            isLoadingAsmrOne = true,
            isLoadingDlsitePlay = true,
            hasResolvedAsmrOneContent = true,
            hasResolvedDlsitePlayContent = true
        )
        val updated = resetOnlineLoadingFlags(base)
        assertFalse(updated.isLoadingDlsite)
        assertFalse(updated.isLoadingDlsiteTrial)
        assertFalse(updated.isLoadingAsmrOne)
        assertFalse(updated.isLoadingDlsitePlay)
        // 保留已完成数据
        assertTrue(updated.hasResolvedAsmrOneContent)
        assertTrue(updated.hasResolvedDlsitePlayContent)
        assertEquals(base.dlsitePlayWorkno, updated.dlsitePlayWorkno)
    }

    @Test
    fun `resetDlsitePlayAccess guard and transform`() {
        // 守卫：树空 + 未装载 + 不在载 → 早退
        assertNull(resetDlsitePlayAccess(model()))
        // 任一条件不成立 → 重置
        val withTree = resetDlsitePlayAccess(model(dlsitePlayTree = listOf(AsmrOneTrackNodeResponse(title = "n"))))
        assertResetedDlsitePlayAccess(withTree)
        val withResolved = resetDlsitePlayAccess(model(hasResolvedDlsitePlayContent = true))
        assertResetedDlsitePlayAccess(withResolved)
        val withLoading = resetDlsitePlayAccess(model(isLoadingDlsitePlay = true))
        assertResetedDlsitePlayAccess(withLoading)
    }

    private fun assertResetedDlsitePlayAccess(updated: AlbumDetailModel?) {
        assertEquals("", updated?.dlsitePlayWorkno)
        assertTrue(updated?.dlsitePlayTree?.isEmpty() == true)
        assertFalse(updated?.hasResolvedDlsitePlayContent == true)
        assertFalse(updated?.isLoadingDlsitePlay == true)
    }

    // ------------------------------------------------------------ 域 D updateLocalTracks

    @Test
    fun `updateLocalTracks guard and transform`() {
        val tracks = listOf(Track(albumId = 2L, title = "a", path = "/a"))
        // 守卫：无 localAlbum → 早退
        assertNull(updateLocalTracks(model(localAlbum = null), localId = 2L, tracks = tracks))
        // 守卫：id 不匹配 → 早退
        assertNull(updateLocalTracks(model(), localId = 99L, tracks = tracks))

        // displayAlbum 与 localAlbum 同 id → 两者音轨同步替换
        val sameId = model(
            displayAlbum = album(id = 2L, title = "display"),
            localAlbum = album(id = 2L, title = "local")
        )
        val updatedSame = updateLocalTracks(sameId, localId = 2L, tracks = tracks)!!
        assertEquals(tracks, updatedSame.localAlbum?.tracks)
        assertEquals(tracks, updatedSame.displayAlbum.tracks)

        // displayAlbum id 不同 → 仅 localAlbum 替换，displayAlbum 原样
        val diffId = model(
            displayAlbum = album(id = 1L, title = "display"),
            localAlbum = album(id = 2L, title = "local")
        )
        val updatedDiff = updateLocalTracks(diffId, localId = 2L, tracks = tracks)!!
        assertEquals(tracks, updatedDiff.localAlbum?.tracks)
        assertSame(diffId.displayAlbum, updatedDiff.displayAlbum)
    }

    // ------------------------------------------------------------ 域 H markAsmrOneLoadFinished

    @Test
    fun `markAsmrOneLoadFinished guard and transform`() {
        // 守卫：key 不匹配 → 早退
        assertNull(markAsmrOneLoadFinished(model(), keyRj = "RJ999", resolved = true))

        // resolved=true → hasResolved 置 true
        val resolved = markAsmrOneLoadFinished(model(isLoadingAsmrOne = true), keyRj = "rj123", resolved = true)!!
        assertFalse(resolved.isLoadingAsmrOne)
        assertTrue(resolved.hasResolvedAsmrOneContent)

        // resolved=false 且原未装载 → 保持 false
        val unresolvedKeep = markAsmrOneLoadFinished(model(), keyRj = "RJ123", resolved = false)!!
        assertFalse(unresolvedKeep.isLoadingAsmrOne)
        assertFalse(unresolvedKeep.hasResolvedAsmrOneContent)

        // resolved=false 且原已装载 → 保持 true（不清除已装载标记）
        val resolvedKeep = markAsmrOneLoadFinished(
            model(hasResolvedAsmrOneContent = true, isLoadingAsmrOne = true),
            keyRj = "RJ123",
            resolved = false
        )!!
        assertFalse(resolvedKeep.isLoadingAsmrOne)
        assertTrue(resolvedKeep.hasResolvedAsmrOneContent)
    }

    // ------------------------------------------------------------ 域 I trial 族

    @Test
    fun `dlsiteTrialRequestWorkno falls back to rjCode when workno blank`() {
        assertEquals("RJ123", model().dlsiteTrialRequestWorkno())
        assertEquals("RJ456", model(dlsiteWorkno = " rj456 ").dlsiteTrialRequestWorkno())
        assertEquals("RJ123", model(dlsiteWorkno = " ", rjCode = "RJ123").dlsiteTrialRequestWorkno())
    }

    @Test
    fun `setDlsiteTrialLoading guard and transform`() {
        // 守卫：workno 不匹配 → 早退
        assertNull(setDlsiteTrialLoading(model(), requestWorkno = "RJ999"))
        // 守卫：dlsite 在载 → 早退
        assertNull(setDlsiteTrialLoading(model(isLoadingDlsite = true), requestWorkno = "RJ123"))
        // 守卫：trial 已在载 → 早退
        assertNull(setDlsiteTrialLoading(model(isLoadingDlsiteTrial = true), requestWorkno = "RJ123"))

        val updated = setDlsiteTrialLoading(model(), requestWorkno = "rj123")!!
        assertTrue(updated.isLoadingDlsiteTrial)
        assertFalse(updated.isLoadingDlsite)
    }

    @Test
    fun `finishDlsiteTrialLoad guard and transform`() {
        val tracks = listOf(Track(albumId = 2L, title = "trial", path = "/t"))
        // 守卫：workno 不匹配 → 早退
        assertNull(finishDlsiteTrialLoad(model(isLoadingDlsiteTrial = true), requestWorkno = "RJ999", tracks = tracks))

        val updated = finishDlsiteTrialLoad(
            model(isLoadingDlsiteTrial = true, dlsiteTrialTracks = emptyList()),
            requestWorkno = "RJ123",
            tracks = tracks
        )!!
        assertEquals(tracks, updated.dlsiteTrialTracks)
        assertFalse(updated.isLoadingDlsiteTrial)
        assertFalse(updated.isLoadingDlsite)
    }

    @Test
    fun `clearDlsiteTrialLoading guard and transform`() {
        // 守卫：workno 不匹配 → 早退
        assertNull(clearDlsiteTrialLoading(model(isLoadingDlsiteTrial = true), requestWorkno = "RJ999"))

        val tracks = listOf(Track(albumId = 2L, title = "old", path = "/o"))
        val updated = clearDlsiteTrialLoading(
            model(isLoadingDlsiteTrial = true, dlsiteTrialTracks = tracks),
            requestWorkno = "RJ123"
        )!!
        assertFalse(updated.isLoadingDlsiteTrial)
        // 失败收口不动已有试听音轨
        assertEquals(tracks, updated.dlsiteTrialTracks)
    }
}
