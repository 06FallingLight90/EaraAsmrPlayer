package com.asmr.player.ui.library.albumdetail

import com.asmr.player.domain.model.Track

/**
 * C8-1 首批纯函数 reducer（r3-c8-reducer-plan.md §2）：把 AlbumDetailViewModel
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
