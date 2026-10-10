package com.asmr.player.ui.common.dialog

import com.asmr.player.domain.model.AlbumGroupStatsRow
import kotlinx.coroutines.flow.StateFlow

/**
 * 批量加入选择器（US-04/T8）合集 tab 的窄数据端口。PlaylistPickerScreen 双 tab 化时
 * 由宿主侧注入（AlbumGroupsViewModel.asBatchAddGroupTarget() 适配），使 ui.playlists
 * 无需直接依赖 ui.groups（feature-to-feature import 守护禁新增）；归位 ui.common.dialog：
 * feature-to-feature 白名单特征，各 ui.* 与 main 均可自由引用（同 RoundedTopSheet/
 * CollectionPickerContent 的共享薄组件定位）。
 */
interface BatchAddGroupTarget {
    /** 合集列表（含统计投影，与 AlbumGroupsViewModel.groups 同源）。 */
    val groups: StateFlow<List<AlbumGroupStatsRow>>

    /** 合集 tab 的新建入口（命名冲突/成功反馈由实现侧经 MessageManager 呈现）。 */
    fun createGroup(name: String)

    /**
     * 按 track 粒度（mediaId=trackPath）批量挂载到合集；幂等去重与 summary 反馈
     * 由实现侧保证（对齐 PlaylistsViewModel.addItemsToPlaylistInBackground 的回调形状）。
     */
    fun addTracksToGroupInBackground(
        groupId: Long,
        mediaIds: List<String>,
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    )
}
