package com.asmr.player.ui.library.albumdetail

import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
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
        dlsiteInfo: Album? = null,
        dlsiteGalleryUrls: List<String> = emptyList(),
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
        hasResolvedInitialDlsiteTarget: Boolean = false,
        hasLoadedInitialDlsiteContent: Boolean = false,
        isDlsiteLanguageUserSelected: Boolean = false,
        dlsiteTrialTracks: List<Track> = emptyList(),
        preserveHeaderAlbumMetadata: Boolean = false
    ): AlbumDetailModel = AlbumDetailModel(
        baseRjCode = baseRjCode,
        rjCode = rjCode,
        listenTogetherRjListenerCount = listenTogetherRjListenerCount,
        displayAlbum = displayAlbum,
        localAlbum = localAlbum,
        dlsiteInfo = dlsiteInfo,
        dlsiteGalleryUrls = dlsiteGalleryUrls,
        dlsiteTrialTracks = dlsiteTrialTracks,
        dlsiteRecommendations = com.asmr.player.data.remote.scraper.DlsiteRecommendations(),
        dlsiteWorkno = dlsiteWorkno,
        dlsitePlayWorkno = dlsitePlayWorkno,
        dlsiteEditions = emptyList(),
        dlsiteSelectedLang = "ja-jp",
        hasResolvedInitialDlsiteTarget = hasResolvedInitialDlsiteTarget,
        hasLoadedInitialDlsiteContent = hasLoadedInitialDlsiteContent,
        hasResolvedAsmrOneContent = hasResolvedAsmrOneContent,
        hasResolvedDlsitePlayContent = hasResolvedDlsitePlayContent,
        preserveHeaderAlbumMetadata = preserveHeaderAlbumMetadata,
        isDlsiteLanguageUserSelected = isDlsiteLanguageUserSelected,
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

    // ------------------------------------------------------------ 域 H 主体（C8-4）

    private fun workDetails(
        id: Int = 7,
        title: String = "asmr-details"
    ): WorkDetailsResponse = WorkDetailsResponse(
        id = id,
        source_id = "src-$id",
        title = title,
        circle = null,
        vas = null,
        tags = null,
        duration = 0,
        mainCoverUrl = "",
        dl_count = 0,
        price = 0
    )

    @Test
    fun `markAsmrOneLoading sets loading and clears resolved`() {
        val base = model(hasResolvedAsmrOneContent = true, isLoadingAsmrOne = false)
        val updated = markAsmrOneLoading(base)
        assertTrue(updated.isLoadingAsmrOne)
        assertFalse(updated.hasResolvedAsmrOneContent)
        // 域外字段不动
        assertEquals(base.asmrOneWorkId, updated.asmrOneWorkId)
        assertEquals(base.asmrOneTree, updated.asmrOneTree)
        assertEquals(base.displayAlbum, updated.displayAlbum)
    }

    @Test
    fun `finishAsmrOneTreeLoad writes tree and closes loading`() {
        val tree = listOf(AsmrOneTrackNodeResponse(title = "n1"))
        val details = workDetails()
        val base = model(isLoadingAsmrOne = true, hasResolvedAsmrOneContent = false)
        val updated = finishAsmrOneTreeLoad(
            model = base,
            workId = " w-9 ",
            site = 200,
            tree = tree,
            resolvedDetails = details
        )
        assertEquals("w-9", updated.asmrOneWorkId)
        assertEquals(200, updated.asmrOneSite)
        assertEquals(tree, updated.asmrOneTree)
        assertTrue(updated.hasResolvedAsmrOneContent)
        assertFalse(updated.isLoadingAsmrOne)
        // displayAlbum = 按 resolvedDetails + trimmed workId 重合并（锚定 mergeAsmrOneHeaderAlbum 参数接线，其自身语义由 Support 覆盖）
        assertEquals(
            mergeAsmrOneHeaderAlbum(
                currentDisplayAlbum = base.displayAlbum,
                localAlbum = base.localAlbum,
                fetchedDlsiteInfo = base.dlsiteInfo,
                resolvedAsmrOneDetails = details,
                rjCode = base.rjCode,
                asmrOneWorkId = "w-9",
                preserveHeaderAlbumMetadata = base.preserveHeaderAlbumMetadata
            ),
            updated.displayAlbum
        )
    }

    @Test
    fun `finishAsmrOneTreeLoad blank workId falls back to existing`() {
        val tree = listOf(AsmrOneTrackNodeResponse(title = "n2"))
        // workId=null → 回退既有 workId
        val nullWorkId = finishAsmrOneTreeLoad(
            model = model(asmrOneWorkId = "w-keep"),
            workId = null,
            site = null,
            tree = tree,
            resolvedDetails = null
        )
        assertEquals("w-keep", nullWorkId.asmrOneWorkId)
        assertNull(nullWorkId.asmrOneSite)
        // workId 空白 → 同样回退
        val blankWorkId = finishAsmrOneTreeLoad(
            model = model(asmrOneWorkId = "w-keep"),
            workId = "  ",
            site = 0,
            tree = emptyList(),
            resolvedDetails = null
        )
        assertEquals("w-keep", blankWorkId.asmrOneWorkId)
        assertEquals(0, blankWorkId.asmrOneSite)
    }

    @Test
    fun `applyAsmrOneResolvedDetails merges header and writes workId only`() {
        val details = workDetails()
        val tree = listOf(AsmrOneTrackNodeResponse(title = "n3"))
        val base = model(
            asmrOneWorkId = "w-old",
            asmrOneSite = 0,
            asmrOneTree = tree,
            isLoadingAsmrOne = true,
            hasResolvedAsmrOneContent = false
        )
        val updated = applyAsmrOneResolvedDetails(base, workId = "w-new", resolvedDetails = details)
        assertEquals("w-new", updated.asmrOneWorkId)
        // site/tree/loading/hasResolved 原样（中间合并不收口装载）
        assertEquals(0, updated.asmrOneSite)
        assertEquals(tree, updated.asmrOneTree)
        assertTrue(updated.isLoadingAsmrOne)
        assertFalse(updated.hasResolvedAsmrOneContent)
        assertEquals(
            mergeAsmrOneHeaderAlbum(
                currentDisplayAlbum = base.displayAlbum,
                localAlbum = base.localAlbum,
                fetchedDlsiteInfo = base.dlsiteInfo,
                resolvedAsmrOneDetails = details,
                rjCode = base.rjCode,
                asmrOneWorkId = "w-new",
                preserveHeaderAlbumMetadata = base.preserveHeaderAlbumMetadata
            ),
            updated.displayAlbum
        )
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

    // ------------------------------------------------------------ 域 G selectDlsiteLanguage

    @Test
    fun `applyDlsiteLanguageSwitch resets all fields and remerges header album`() {
        val info = album(id = 9L, title = "online-info", rjCode = "RJ123")
        val trial = listOf(Track(albumId = 2L, title = "trial", path = "/t"))
        val base = model(
            dlsiteInfo = info,
            dlsiteGalleryUrls = listOf("https://img/g1"),
            dlsiteTrialTracks = trial,
            hasLoadedInitialDlsiteContent = true,
            hasResolvedAsmrOneContent = true,
            hasResolvedDlsitePlayContent = true,
            isLoadingDlsite = true,
            isLoadingDlsiteTrial = true,
            isLoadingAsmrOne = true,
            isLoadingDlsitePlay = true
        )
        val updated = applyDlsiteLanguageSwitch(base, selectedLang = "JPN", workno = "RJ456")
        assertEquals("JPN", updated.dlsiteSelectedLang)
        assertEquals("RJ456", updated.dlsiteWorkno)
        assertEquals("", updated.dlsitePlayWorkno)
        assertEquals("RJ456", updated.rjCode)
        // displayAlbum = 旧 displayAlbum 按 fetchedDlsiteInfo=null 重合并
        assertEquals(
            base.displayAlbum.withResolvedWorkIdentity(rjCode = "RJ456", asmrOneWorkId = null),
            updated.displayAlbum
        )
        assertNull(updated.dlsiteInfo)
        assertTrue(updated.dlsiteGalleryUrls.isEmpty())
        assertTrue(updated.dlsiteTrialTracks.isEmpty())
        assertEquals(
            com.asmr.player.data.remote.scraper.DlsiteRecommendations(),
            updated.dlsiteRecommendations
        )
        assertTrue(updated.hasResolvedInitialDlsiteTarget)
        assertFalse(updated.hasLoadedInitialDlsiteContent)
        assertFalse(updated.hasResolvedAsmrOneContent)
        assertFalse(updated.hasResolvedDlsitePlayContent)
        assertTrue(updated.isDlsiteLanguageUserSelected)
        assertNull(updated.asmrOneWorkId)
        assertNull(updated.asmrOneSite)
        assertTrue(updated.asmrOneTree.isEmpty())
        assertTrue(updated.dlsitePlayTree.isEmpty())
        assertFalse(updated.isLoadingDlsite)
        assertFalse(updated.isLoadingDlsiteTrial)
        assertFalse(updated.isLoadingAsmrOne)
        assertFalse(updated.isLoadingDlsitePlay)
        // 域外字段不动
        assertEquals(base.baseRjCode, updated.baseRjCode)
        assertEquals(base.localAlbum, updated.localAlbum)
        assertFalse(updated.preserveHeaderAlbumMetadata)
    }

    @Test
    fun `applyDlsiteLanguageLocalReload guard and transform`() {
        val local = album(id = 3L, title = "new-local", rjCode = "RJ456")
        // 守卫：rjCode 不匹配 → 早退
        assertNull(applyDlsiteLanguageLocalReload(model(), workno = "RJ999", local = local))
        // 匹配（大小写不敏感）→ localAlbum 替换 + displayAlbum 按当前 dlsiteInfo 重合并
        val base = model()
        val updated = applyDlsiteLanguageLocalReload(base, workno = "rj123", local = local)!!
        assertEquals(local, updated.localAlbum)
        assertEquals(
            base.displayAlbum.withResolvedWorkIdentity(rjCode = base.rjCode, asmrOneWorkId = base.asmrOneWorkId),
            updated.displayAlbum
        )
        // local 为 null → 仅重合并 displayAlbum，localAlbum 置空
        val nullLocal = applyDlsiteLanguageLocalReload(base, workno = "RJ123", local = null)!!
        assertNull(nullLocal.localAlbum)
        assertEquals(updated.displayAlbum, nullLocal.displayAlbum)
    }

    // ------------------------------------------------------------ 域 J dlsitePlay 尾段

    @Test
    fun `markDlsitePlayLoading sets loading and clears resolved`() {
        val updated = markDlsitePlayLoading(model(hasResolvedDlsitePlayContent = true))
        assertTrue(updated.isLoadingDlsitePlay)
        assertFalse(updated.hasResolvedDlsitePlayContent)
        // 域外字段不动
        assertEquals(model().dlsitePlayWorkno, updated.dlsitePlayWorkno)
        assertEquals(model().dlsitePlayTree, updated.dlsitePlayTree)
    }

    @Test
    fun `finishDlsitePlayLoad writes tree and closes loading`() {
        val tree = listOf(AsmrOneTrackNodeResponse(title = "n1"))
        val updated = finishDlsitePlayLoad(
            model(isLoadingDlsitePlay = true),
            tree = tree,
            pickedWorkno = " rj456 "
        )
        assertEquals(tree, updated.dlsitePlayTree)
        // 原内联语义仅 trim 不转大写（大写归一发生在候选推导处）
        assertEquals("rj456", updated.dlsitePlayWorkno)
        assertTrue(updated.hasResolvedDlsitePlayContent)
        assertFalse(updated.isLoadingDlsitePlay)

        // pickedWorkno=null → workno 归一为空串
        val noPick = finishDlsitePlayLoad(model(), tree = emptyList(), pickedWorkno = null)
        assertEquals("", noPick.dlsitePlayWorkno)
        assertTrue(noPick.hasResolvedDlsitePlayContent)
        assertFalse(noPick.isLoadingDlsitePlay)
    }

    @Test
    fun `markDlsitePlayLoadFailed closes loading keeping tree`() {
        val tree = listOf(AsmrOneTrackNodeResponse(title = "n2"))
        val base = model(dlsitePlayTree = tree, dlsitePlayWorkno = "RJ789", isLoadingDlsitePlay = true)
        val updated = markDlsitePlayLoadFailed(base)
        assertFalse(updated.isLoadingDlsitePlay)
        assertTrue(updated.hasResolvedDlsitePlayContent)
        // 树与 workno 保持原样
        assertEquals(tree, updated.dlsitePlayTree)
        assertEquals("RJ789", updated.dlsitePlayWorkno)
    }

    // ------------------------------------------------------------ 域 F ensureDlsiteLoaded

    @Test
    fun `markDlsiteLoadStarted sets dlsite loading and clears trial loading`() {
        val base = model(isLoadingDlsiteTrial = true, hasLoadedInitialDlsiteContent = true)
        val updated = markDlsiteLoadStarted(base)
        assertTrue(updated.isLoadingDlsite)
        assertFalse(updated.isLoadingDlsiteTrial)
        // 域外字段不动
        assertTrue(updated.hasLoadedInitialDlsiteContent)
        assertEquals(base.rjCode, updated.rjCode)
    }

    @Test
    fun `applyInitialDlsiteTargetResolved reload without keep clears asmrOne content`() {
        val target = ResolvedDlsiteLoadTarget(
            editions = emptyList(),
            selectedLang = "JPN",
            workno = "RJ456"
        )
        val base = model(
            rjCode = "RJ123",
            hasResolvedAsmrOneContent = true,
            isLoadingAsmrOne = true,
            isLoadingDlsiteTrial = true
        )
        val updated = applyInitialDlsiteTargetResolved(
            model = base,
            resolvedTarget = target,
            mustReloadAsmrOne = true,
            keepAsmrOneContentDuringTargetSwitch = false
        )
        assertEquals("RJ456", updated.rjCode)
        assertEquals("RJ456", updated.dlsiteWorkno)
        assertTrue(updated.hasResolvedInitialDlsiteTarget)
        // displayAlbum = 旧 displayAlbum 按 fetchedDlsiteInfo=null、asmrOneWorkId=null 重合并
        assertEquals(
            base.displayAlbum.withResolvedWorkIdentity(rjCode = "RJ456", asmrOneWorkId = null),
            updated.displayAlbum
        )
        assertFalse(updated.hasResolvedAsmrOneContent)
        assertNull(updated.asmrOneWorkId)
        assertNull(updated.asmrOneSite)
        assertTrue(updated.asmrOneTree.isEmpty())
        assertFalse(updated.isLoadingAsmrOne)
        assertTrue(updated.isLoadingDlsite)
        assertFalse(updated.isLoadingDlsiteTrial)
    }

    @Test
    fun `applyInitialDlsiteTargetResolved reload with keep preserves asmrOne content`() {
        val target = ResolvedDlsiteLoadTarget(
            editions = emptyList(),
            selectedLang = "JPN",
            workno = "RJ456"
        )
        val base = model(rjCode = "RJ123", hasResolvedAsmrOneContent = true)
        val updated = applyInitialDlsiteTargetResolved(
            model = base,
            resolvedTarget = target,
            mustReloadAsmrOne = true,
            keepAsmrOneContentDuringTargetSwitch = true
        )
        // 树非空时保留 asmrOne 内容（仅 hasResolved 清 false）
        assertEquals(base.asmrOneWorkId, updated.asmrOneWorkId)
        assertEquals(base.asmrOneSite, updated.asmrOneSite)
        assertEquals(base.asmrOneTree, updated.asmrOneTree)
        assertFalse(updated.hasResolvedAsmrOneContent)
        // displayAlbum 仍按 asmrOneWorkId=null 重合并（merge 参数不感知 keep）
        assertEquals(
            base.displayAlbum.withResolvedWorkIdentity(rjCode = "RJ456", asmrOneWorkId = null),
            updated.displayAlbum
        )
    }

    @Test
    fun `applyInitialDlsiteTargetResolved without reload keeps asmrOne state`() {
        val target = ResolvedDlsiteLoadTarget(
            editions = emptyList(),
            selectedLang = "JPN",
            workno = "RJ123"
        )
        val base = model(
            rjCode = "RJ123",
            hasResolvedAsmrOneContent = true,
            isLoadingAsmrOne = true
        )
        val updated = applyInitialDlsiteTargetResolved(
            model = base,
            resolvedTarget = target,
            mustReloadAsmrOne = false,
            keepAsmrOneContentDuringTargetSwitch = false
        )
        assertTrue(updated.hasResolvedAsmrOneContent)
        assertTrue(updated.isLoadingAsmrOne)
        assertEquals(base.asmrOneWorkId, updated.asmrOneWorkId)
        assertEquals(base.asmrOneTree, updated.asmrOneTree)
        // displayAlbum 按原 asmrOneWorkId 重合并
        assertEquals(
            base.displayAlbum.withResolvedWorkIdentity(
                rjCode = "RJ123",
                asmrOneWorkId = base.asmrOneWorkId
            ),
            updated.displayAlbum
        )
        assertTrue(updated.isLoadingDlsite)
        assertTrue(updated.hasResolvedInitialDlsiteTarget)
    }

    @Test
    fun `finishDlsiteInitialLoadWithoutWorkno settles load`() {
        val base = model(isLoadingDlsite = true, hasLoadedInitialDlsiteContent = false)
        val updated = finishDlsiteInitialLoadWithoutWorkno(base)
        assertTrue(updated.hasLoadedInitialDlsiteContent)
        assertFalse(updated.isLoadingDlsite)
        // 域外字段不动
        assertEquals(base.rjCode, updated.rjCode)
        assertEquals(base.dlsiteWorkno, updated.dlsiteWorkno)
    }

    @Test
    fun `applyDlsiteContentLoaded merges header and writes content fields`() {
        val fetched = album(id = 9L, title = "online-info", rjCode = "RJ123")
        val gallery = listOf("https://img/g1")
        val trial = listOf(Track(albumId = 2L, title = "trial", path = "/t"))
        val recs = com.asmr.player.data.remote.scraper.DlsiteRecommendations()
        val base = model(isLoadingDlsite = true, hasLoadedInitialDlsiteContent = false)
        val updated = applyDlsiteContentLoaded(
            model = base,
            fetchedDlsiteInfo = fetched,
            dlsiteGalleryUrls = gallery,
            dlsiteTrialTracks = trial,
            dlsiteRecommendations = recs
        )
        assertTrue(updated.hasLoadedInitialDlsiteContent)
        assertFalse(updated.isLoadingDlsite)
        assertSame(fetched, updated.dlsiteInfo)
        assertEquals(gallery, updated.dlsiteGalleryUrls)
        assertEquals(trial, updated.dlsiteTrialTracks)
        assertEquals(recs, updated.dlsiteRecommendations)
        // displayAlbum = 按 fetchedDlsiteInfo 重合并（锚定 mergeDetailHeaderAlbum 参数接线，其自身语义由 Support 覆盖）
        assertEquals(
            mergeDetailHeaderAlbum(
                currentDisplayAlbum = base.displayAlbum,
                localAlbum = base.localAlbum,
                fetchedDlsiteInfo = fetched,
                rjCode = base.rjCode,
                asmrOneWorkId = base.asmrOneWorkId,
                preserveHeaderAlbumMetadata = base.preserveHeaderAlbumMetadata
            ),
            updated.displayAlbum
        )
    }

    @Test
    fun `applyDlsiteContentLoaded preserves header metadata keeps old dlsiteInfo`() {
        val oldInfo = album(id = 8L, title = "old-info", rjCode = "RJ123")
        val fetched = album(id = 9L, title = "online-info", rjCode = "RJ123")
        val base = model(dlsiteInfo = oldInfo, preserveHeaderAlbumMetadata = true)
        val updated = applyDlsiteContentLoaded(
            model = base,
            fetchedDlsiteInfo = fetched,
            dlsiteGalleryUrls = emptyList(),
            dlsiteTrialTracks = emptyList(),
            dlsiteRecommendations = com.asmr.player.data.remote.scraper.DlsiteRecommendations()
        )
        assertSame(oldInfo, updated.dlsiteInfo)
        // preserve 路径 displayAlbum 仅解析 work identity
        assertEquals(
            base.displayAlbum.withResolvedWorkIdentity(
                rjCode = base.rjCode,
                asmrOneWorkId = base.asmrOneWorkId
            ),
            updated.displayAlbum
        )
    }

    @Test
    fun `applyDlsiteRecommendationEnrich replaces recommendations only`() {
        val enriched = com.asmr.player.data.remote.scraper.DlsiteRecommendations(
            circleWorks = listOf(DlsiteRecommendedWork(rjCode = "RJ999", title = "t", coverUrl = "c"))
        )
        val base = model(isLoadingDlsite = true)
        val updated = applyDlsiteRecommendationEnrich(base, enriched)
        assertEquals(enriched, updated.dlsiteRecommendations)
        // 域外字段不动
        assertEquals(base.isLoadingDlsite, updated.isLoadingDlsite)
        assertEquals(base.displayAlbum, updated.displayAlbum)
    }

    @Test
    fun `markDlsiteLoadFailed closes dlsite loading only`() {
        val base = model(isLoadingDlsite = true, isLoadingDlsiteTrial = true)
        val updated = markDlsiteLoadFailed(base)
        assertFalse(updated.isLoadingDlsite)
        // 失败收口不动 trial 标志与其他字段
        assertTrue(updated.isLoadingDlsiteTrial)
        assertFalse(updated.hasLoadedInitialDlsiteContent)
    }

    @Test
    fun `mergeDlsiteRecommendations prefer non blank and dedupe`() {
        // primary 空 → 直接用 secondary
        val onlySecondary = mergeDlsiteRecommendations(
            fromV2 = com.asmr.player.data.remote.scraper.DlsiteRecommendations(),
            fallback = com.asmr.player.data.remote.scraper.DlsiteRecommendations(
                circleWorks = listOf(DlsiteRecommendedWork(rjCode = "RJ1", title = "f1", coverUrl = "c1"))
            )
        )
        assertEquals(listOf(DlsiteRecommendedWork(rjCode = "RJ1", title = "f1", coverUrl = "c1")), onlySecondary.circleWorks)

        // secondary 空 → 直接用 primary
        val primary = listOf(DlsiteRecommendedWork(rjCode = "RJ2", title = "p2", coverUrl = "c2"))
        val onlyPrimary = mergeDlsiteRecommendations(
            fromV2 = com.asmr.player.data.remote.scraper.DlsiteRecommendations(alsoBoughtWorks = primary),
            fallback = com.asmr.player.data.remote.scraper.DlsiteRecommendations()
        )
        assertEquals(primary, onlyPrimary.alsoBoughtWorks)

        // 重叠项按 primary 优先、secondary 补空白；rjCode 大小写/空白不敏感去重
        val merged = mergeDlsiteRecommendations(
            fromV2 = com.asmr.player.data.remote.scraper.DlsiteRecommendations(
                sameVoiceWorks = listOf(
                    DlsiteRecommendedWork(rjCode = " rj3 ", title = "", coverUrl = "cv3", ribbon = null),
                    DlsiteRecommendedWork(rjCode = "RJ4", title = "p4", coverUrl = "cv4", ribbon = "r4")
                )
            ),
            fallback = com.asmr.player.data.remote.scraper.DlsiteRecommendations(
                sameVoiceWorks = listOf(
                    DlsiteRecommendedWork(rjCode = "RJ3", title = "f3", coverUrl = "old", ribbon = "rf3"),
                    DlsiteRecommendedWork(rjCode = "RJ5", title = "f5", coverUrl = "cv5", ribbon = null)
                )
            )
        )
        val mergedWorks = merged.sameVoiceWorks
        // RJ3：primary 的空白 title 由 secondary 补，coverUrl/ribbon 保留 primary
        assertEquals(DlsiteRecommendedWork(rjCode = " rj3 ", title = "f3", coverUrl = "cv3", ribbon = "rf3"), mergedWorks[0])
        assertEquals(DlsiteRecommendedWork(rjCode = "RJ4", title = "p4", coverUrl = "cv4", ribbon = "r4"), mergedWorks[1])
        // RJ5 仅在 secondary → 追加
        assertEquals(DlsiteRecommendedWork(rjCode = "RJ5", title = "f5", coverUrl = "cv5", ribbon = null), mergedWorks[2])
    }
}
