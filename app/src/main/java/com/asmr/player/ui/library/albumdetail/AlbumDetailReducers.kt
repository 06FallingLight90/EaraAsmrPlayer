package com.asmr.player.ui.library.albumdetail

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track

/**
 * C8 各批次纯函数 reducer（r3-c8-reducer-plan.md §2/§3）：把 AlbumDetailViewModel
 * 内联的 Success 态 copy 变换收编为 (AlbumDetailModel, 事件参数) -> AlbumDetailModel? 纯函数。
 *
 * 约定：
 * - 返回 [AlbumDetailModel]（非空）：无条件重置类变换，守卫由调用点承担。
 * - 返回 null：守卫早退语义（null = 不赋值，调用点跳过/return）。
 * - copy 字段与守卫条件逐字对齐原 VM 内联逻辑（含注释随迁）。
 * - Android/VM 依赖禁入：本文件须保持纯 JVM 可测（AlbumDetailReducersTest 表驱动）。
 */

/** 域 A：listenTogether 轮询回写 listenerCount（原 refreshListenTogetherRjSummary 内联守卫：RJ 匹配且数值有变化才赋值）。 */
internal fun updateListenTogetherListenerCount(
    model: AlbumDetailModel,
    normalizedRj: String,
    listenerCount: Int
): AlbumDetailModel? {
    if (!model.listenTogetherSummaryRj().equals(normalizedRj, ignoreCase = true)) return null
    if (model.listenTogetherRjListenerCount == listenerCount) return null
    return model.copy(listenTogetherRjListenerCount = listenerCount)
}

/** 域 B/H：asmrOne 内容五字段重置（原 invalidateAsmrOneEndpointState / refreshAsmrOneSection 内联 copy；keyRj 空白与在载守卫留在调用点）。 */
internal fun resetAsmrOneContent(model: AlbumDetailModel): AlbumDetailModel {
    return model.copy(
        asmrOneWorkId = null,
        asmrOneSite = null,
        asmrOneTree = emptyList(),
        hasResolvedAsmrOneContent = false,
        isLoadingAsmrOne = false
    )
}

/** 域 C：四路在线 loading 标志收口（原 cancelPendingOnlineJobs 内联 copy；resetLoadingState 守卫留在调用点）。 */
internal fun resetOnlineLoadingFlags(model: AlbumDetailModel): AlbumDetailModel {
    return model.copy(
        isLoadingDlsite = false,
        isLoadingDlsiteTrial = false,
        isLoadingAsmrOne = false,
        isLoadingDlsitePlay = false
    )
}

/** 域 C：dlsitePlay 访问四字段重置（原 invalidateDlsitePlayAccess 内联守卫：树空且未装载且不在载时早退）。 */
internal fun resetDlsitePlayAccess(model: AlbumDetailModel): AlbumDetailModel? {
    if (
        model.dlsitePlayTree.isEmpty() &&
        !model.hasResolvedDlsitePlayContent &&
        !model.isLoadingDlsitePlay
    ) return null
    return model.copy(
        dlsitePlayWorkno = "",
        dlsitePlayTree = emptyList(),
        hasResolvedDlsitePlayContent = false,
        isLoadingDlsitePlay = false
    )
}

/** 域 D：本地音轨流回写（原 observeLocalTracks collect 内联守卫与合并：localAlbum 存在且 id 匹配才赋值；displayAlbum 同 id 时同步替换音轨）。 */
internal fun updateLocalTracks(
    model: AlbumDetailModel,
    localId: Long,
    tracks: List<Track>
): AlbumDetailModel? {
    val currentLocal = model.localAlbum ?: return null
    if (currentLocal.id != localId) return null

    val updatedLocal = currentLocal.copy(tracks = tracks)
    val updatedDisplay = if (model.displayAlbum.id == localId) {
        model.displayAlbum.copy(tracks = tracks)
    } else {
        model.displayAlbum
    }
    return model.copy(
        localAlbum = updatedLocal,
        displayAlbum = updatedDisplay
    )
}

/** 域 H：asmrOne 装载收尾（原 finishAsmrOneLoad 内联守卫：key 匹配才赋值；失败提示副作用留在调用点）。 */
internal fun markAsmrOneLoadFinished(
    model: AlbumDetailModel,
    keyRj: String,
    resolved: Boolean
): AlbumDetailModel? {
    val updatedKey = model.rjCode.trim().uppercase()
    if (!updatedKey.equals(keyRj, ignoreCase = true)) return null
    return model.copy(
        isLoadingAsmrOne = false,
        hasResolvedAsmrOneContent = if (resolved) true else model.hasResolvedAsmrOneContent
    )
}

/** 域 I：trial 请求键推导（原 refreshDlsiteTrialSection 三处内联表达式：workno 空白时回退 rjCode）。 */
internal fun AlbumDetailModel.dlsiteTrialRequestWorkno(): String {
    return dlsiteWorkno.trim().uppercase().ifBlank { rjCode.trim().uppercase() }
}

/** 域 I：trial 开载置位（原 refreshDlsiteTrialSection 内联守卫：workno 匹配且双 loading 均为 false 才赋值）。 */
internal fun setDlsiteTrialLoading(
    model: AlbumDetailModel,
    requestWorkno: String
): AlbumDetailModel? {
    if (!model.dlsiteTrialRequestWorkno().equals(requestWorkno, ignoreCase = true)) return null
    if (model.isLoadingDlsite || model.isLoadingDlsiteTrial) return null
    return model.copy(isLoadingDlsiteTrial = true)
}

/** 域 I：trial 成功装载（原 refreshDlsiteTrialSection 成功分支内联守卫：workno 匹配才赋值）。 */
internal fun finishDlsiteTrialLoad(
    model: AlbumDetailModel,
    requestWorkno: String,
    tracks: List<Track>
): AlbumDetailModel? {
    if (!model.dlsiteTrialRequestWorkno().equals(requestWorkno, ignoreCase = true)) return null
    return model.copy(
        dlsiteTrialTracks = tracks,
        isLoadingDlsiteTrial = false
    )
}

/** 域 I：trial 失败收口（原 refreshDlsiteTrialSection catch 分支内联守卫：workno 匹配才赋值）。 */
internal fun clearDlsiteTrialLoading(
    model: AlbumDetailModel,
    requestWorkno: String
): AlbumDetailModel? {
    if (!model.dlsiteTrialRequestWorkno().equals(requestWorkno, ignoreCase = true)) return null
    return model.copy(isLoadingDlsiteTrial = false)
}

/** 域 G：语言切换大重置（原 selectDlsiteLanguage 内联 copy；selectedLang/workno 由调用点推导；token/attemptedRj 副作用留在调用点；displayAlbum 按 fetchedDlsiteInfo=null 重合并，沿用调用点的 model 快照应用）。 */
internal fun applyDlsiteLanguageSwitch(
    model: AlbumDetailModel,
    selectedLang: String,
    workno: String
): AlbumDetailModel {
    return model.copy(
        dlsiteSelectedLang = selectedLang,
        dlsiteWorkno = workno,
        dlsitePlayWorkno = "",
        rjCode = workno,
        displayAlbum = mergeDetailHeaderAlbum(
            currentDisplayAlbum = model.displayAlbum,
            localAlbum = model.localAlbum,
            fetchedDlsiteInfo = null,
            rjCode = workno,
            asmrOneWorkId = null,
            preserveHeaderAlbumMetadata = model.preserveHeaderAlbumMetadata
        ),
        dlsiteInfo = null,
        dlsiteGalleryUrls = emptyList(),
        dlsiteTrialTracks = emptyList(),
        dlsiteRecommendations = DlsiteRecommendations(),
        hasResolvedInitialDlsiteTarget = true,
        hasLoadedInitialDlsiteContent = false,
        hasResolvedAsmrOneContent = false,
        hasResolvedDlsitePlayContent = false,
        isDlsiteLanguageUserSelected = true,
        asmrOneWorkId = null,
        asmrOneSite = null,
        asmrOneTree = emptyList(),
        dlsitePlayTree = emptyList(),
        isLoadingDlsite = false,
        isLoadingDlsiteTrial = false,
        isLoadingAsmrOne = false,
        isLoadingDlsitePlay = false
    )
}

/** 域 G：语言切换尾部本地重装载（原 selectDlsiteLanguage launch 内联守卫：rjCode 与 workno 匹配才赋值；displayAlbum 按当前 dlsiteInfo 重合并）。 */
internal fun applyDlsiteLanguageLocalReload(
    model: AlbumDetailModel,
    workno: String,
    local: Album?
): AlbumDetailModel? {
    if (!model.rjCode.equals(workno, ignoreCase = true)) return null
    val displayAlbum = mergeDetailHeaderAlbum(
        currentDisplayAlbum = model.displayAlbum,
        localAlbum = local,
        fetchedDlsiteInfo = model.dlsiteInfo,
        rjCode = model.rjCode,
        asmrOneWorkId = model.asmrOneWorkId,
        preserveHeaderAlbumMetadata = model.preserveHeaderAlbumMetadata
    )
    return model.copy(localAlbum = local, displayAlbum = displayAlbum)
}

/** 域 J：dlsitePlay 开载置位（原 ensureDlsitePlayLoaded launch 头部内联 copy；沿用调用点的 model 快照应用）。 */
internal fun markDlsitePlayLoading(model: AlbumDetailModel): AlbumDetailModel {
    return model.copy(
        isLoadingDlsitePlay = true,
        hasResolvedDlsitePlayContent = false
    )
}

/** 域 J：dlsitePlay 成功树装载（原 ensureDlsitePlayLoaded 成功分支内联 copy；pickedWorkno 空白归一随迁）。 */
internal fun finishDlsitePlayLoad(
    model: AlbumDetailModel,
    tree: List<AsmrOneTrackNodeResponse>,
    pickedWorkno: String?
): AlbumDetailModel {
    return model.copy(
        dlsitePlayTree = tree,
        dlsitePlayWorkno = pickedWorkno?.trim().orEmpty(),
        hasResolvedDlsitePlayContent = true,
        isLoadingDlsitePlay = false
    )
}

/** 域 J：dlsitePlay 失败收口（原 ensureDlsitePlayLoaded catch 分支内联 copy；attemptKey 回滚副作用留在调用点）。 */
internal fun markDlsitePlayLoadFailed(model: AlbumDetailModel): AlbumDetailModel {
    return model.copy(
        hasResolvedDlsitePlayContent = true,
        isLoadingDlsitePlay = false
    )
}

/** 域 F：dlsite 开载置位（原 ensureDlsiteLoaded launch 头部内联 copy；沿用调用点的 model 快照应用）。 */
internal fun markDlsiteLoadStarted(model: AlbumDetailModel): AlbumDetailModel {
    return model.copy(
        isLoadingDlsite = true,
        isLoadingDlsiteTrial = false
    )
}

/** 域 F：初始装载目标解析推进（原 ensureDlsiteLoaded target resolve 段内联 copy；mustReloadAsmrOne / keepAsmrOneContentDuringTargetSwitch 由调用点推导，asmrOne token 与 attemptedRj 副作用留在调用点）。 */
internal fun applyInitialDlsiteTargetResolved(
    model: AlbumDetailModel,
    resolvedTarget: ResolvedDlsiteLoadTarget,
    mustReloadAsmrOne: Boolean,
    keepAsmrOneContentDuringTargetSwitch: Boolean
): AlbumDetailModel {
    return model.copy(
        rjCode = resolvedTarget.workno,
        displayAlbum = mergeDetailHeaderAlbum(
            currentDisplayAlbum = model.displayAlbum,
            localAlbum = model.localAlbum,
            fetchedDlsiteInfo = model.dlsiteInfo,
            rjCode = resolvedTarget.workno,
            asmrOneWorkId = if (mustReloadAsmrOne) null else model.asmrOneWorkId,
            preserveHeaderAlbumMetadata = model.preserveHeaderAlbumMetadata
        ),
        dlsiteWorkno = resolvedTarget.workno,
        dlsiteEditions = resolvedTarget.editions,
        dlsiteSelectedLang = resolvedTarget.selectedLang,
        hasResolvedInitialDlsiteTarget = true,
        hasResolvedAsmrOneContent = if (mustReloadAsmrOne) false else model.hasResolvedAsmrOneContent,
        asmrOneWorkId = if (mustReloadAsmrOne && !keepAsmrOneContentDuringTargetSwitch) {
            null
        } else {
            model.asmrOneWorkId
        },
        asmrOneSite = if (mustReloadAsmrOne && !keepAsmrOneContentDuringTargetSwitch) {
            null
        } else {
            model.asmrOneSite
        },
        asmrOneTree = if (mustReloadAsmrOne && !keepAsmrOneContentDuringTargetSwitch) {
            emptyList()
        } else {
            model.asmrOneTree
        },
        isLoadingDlsite = true,
        isLoadingAsmrOne = if (mustReloadAsmrOne) false else model.isLoadingAsmrOne,
        isLoadingDlsiteTrial = false
    )
}

/** 域 F：初始装载无 workno 收口（原 ensureDlsiteLoaded 两处 workno 空白早退内联 copy：视为已装载并停 loading；workno 空白判定留在调用点）。 */
internal fun finishDlsiteInitialLoadWithoutWorkno(model: AlbumDetailModel): AlbumDetailModel {
    return model.copy(
        hasLoadedInitialDlsiteContent = true,
        isLoadingDlsite = false
    )
}

/** 域 F：dlsite 抓取结果合并（原 ensureDlsiteLoaded 抓取后内联 copy；displayAlbum 按抓取到的 dlsiteInfo 重合并；preserveHeaderAlbumMetadata 时保留旧 dlsiteInfo）。 */
internal fun applyDlsiteContentLoaded(
    model: AlbumDetailModel,
    fetchedDlsiteInfo: Album?,
    dlsiteGalleryUrls: List<String>,
    dlsiteTrialTracks: List<Track>,
    dlsiteRecommendations: DlsiteRecommendations
): AlbumDetailModel {
    return model.copy(
        displayAlbum = mergeDetailHeaderAlbum(
            currentDisplayAlbum = model.displayAlbum,
            localAlbum = model.localAlbum,
            fetchedDlsiteInfo = fetchedDlsiteInfo,
            rjCode = model.rjCode,
            asmrOneWorkId = model.asmrOneWorkId,
            preserveHeaderAlbumMetadata = model.preserveHeaderAlbumMetadata
        ),
        dlsiteInfo = if (model.preserveHeaderAlbumMetadata) model.dlsiteInfo else fetchedDlsiteInfo,
        dlsiteGalleryUrls = dlsiteGalleryUrls,
        dlsiteTrialTracks = dlsiteTrialTracks,
        dlsiteRecommendations = dlsiteRecommendations,
        hasLoadedInitialDlsiteContent = true,
        isLoadingDlsite = false
    )
}

/** 域 F：推荐位 asmrOne 富化回写（原 ensureDlsiteLoaded enrich job 内联 copy；token 与推荐位非空守卫留在调用点）。 */
internal fun applyDlsiteRecommendationEnrich(
    model: AlbumDetailModel,
    enriched: DlsiteRecommendations
): AlbumDetailModel {
    return model.copy(dlsiteRecommendations = enriched)
}

/** 域 F：初始装载失败收口（原 ensureDlsiteLoaded catch 分支内联 copy）。 */
internal fun markDlsiteLoadFailed(model: AlbumDetailModel): AlbumDetailModel {
    return model.copy(isLoadingDlsite = false)
}

/** 域 F：推荐位两路合并（原 ensureDlsiteLoaded 局部函数 mergePreferNonBlank 及三路组装逐字随迁）。 */
internal fun mergeDlsiteRecommendations(
    fromV2: DlsiteRecommendations,
    fallback: DlsiteRecommendations
): DlsiteRecommendations {
    return DlsiteRecommendations(
        circleWorks = mergePreferNonBlank(fromV2.circleWorks, fallback.circleWorks),
        sameVoiceWorks = mergePreferNonBlank(fromV2.sameVoiceWorks, fallback.sameVoiceWorks),
        alsoBoughtWorks = mergePreferNonBlank(fromV2.alsoBoughtWorks, fallback.alsoBoughtWorks)
    )
}

private fun mergePreferNonBlank(
    primary: List<DlsiteRecommendedWork>,
    secondary: List<DlsiteRecommendedWork>
): List<DlsiteRecommendedWork> {
    if (primary.isEmpty()) return secondary
    if (secondary.isEmpty()) return primary
    val secondaryById = secondary.associateBy { it.rjCode.trim().uppercase() }
    val merged = primary.map { p ->
        val s = secondaryById[p.rjCode.trim().uppercase()]
        if (s == null) {
            p
        } else {
            p.copy(
                title = p.title.ifBlank { s.title },
                coverUrl = p.coverUrl.ifBlank { s.coverUrl },
                ribbon = p.ribbon ?: s.ribbon
            )
        }
    }
    val existing = merged.mapTo(hashSetOf()) { it.rjCode.trim().uppercase() }
    val appended = secondary.filter { it.rjCode.trim().uppercase() !in existing }
    return (merged + appended).distinctBy { it.rjCode.trim().uppercase() }
}
