package com.asmr.player.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.domain.model.AlbumGroupStatsRow
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.repository.AlbumGroupRepository
import com.asmr.player.data.repository.RenameAlbumGroupResult
import com.asmr.player.ui.common.dialog.BatchAddGroupTarget
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumGroupsViewModel @Inject constructor(
    private val groupRepository: AlbumGroupRepository,
    private val messageManager: MessageManager
) : ViewModel() {
    val groups: StateFlow<List<AlbumGroupStatsRow>> = groupRepository.observeGroupsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // T7：三类默认合集幂等 seed（歌曲/音声/其它音频，首建时全量回填存量曲目）。
        viewModelScope.launch {
            groupRepository.ensureDefaultGroups()
        }
    }

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = groupRepository.createGroup(trimmed)
            if (id != null) {
                messageManager.showSuccess("已创建分组：$trimmed")
            } else {
                messageManager.showError("分组名称已存在：$trimmed")
            }
        }
    }

    fun deleteGroup(group: AlbumGroupEntity) {
        viewModelScope.launch {
            groupRepository.deleteGroup(group)
            messageManager.showInfo("已删除分组：${group.name}")
        }
    }

    fun renameGroup(groupId: Long, newName: String) {
        val trimmed = newName.trim()
        viewModelScope.launch {
            when (groupRepository.renameGroup(groupId, trimmed)) {
                RenameAlbumGroupResult.RENAMED -> messageManager.showSuccess("已重命名为：$trimmed")
                RenameAlbumGroupResult.DUPLICATE -> messageManager.showError("分组名称已存在：$trimmed")
                RenameAlbumGroupResult.INVALID -> messageManager.showError("分组名称不能为空")
                RenameAlbumGroupResult.NOT_FOUND -> messageManager.showError("分组不存在")
            }
        }
    }

    fun addAlbumToGroupInBackground(
        groupId: Long,
        albumId: Long,
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                addAlbumToGroup(groupId, albumId)
                onComplete()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                messageManager.showError("添加到分组失败，请重试")
                onFailure(t)
            }
        }
    }

    suspend fun addAlbumToGroup(groupId: Long, albumId: Long) {
        if (groupId <= 0L || albumId <= 0L) return
        groupRepository.addAlbumToGroup(groupId, albumId)
        val group = groupRepository.getGroupById(groupId)
        val name = group?.name.orEmpty()
        messageManager.showSuccess("已添加到分组：$name")
    }

    /**
     * T8/US-04：按 track 粒度批量挂载到合集（全部歌曲多选/选源 sheet 走此通道）。
     * summary 三态文案对齐 PlaylistsViewModel.showAddSummary 的歌单语义。
     */
    fun addTracksToGroupInBackground(
        groupId: Long,
        mediaIds: List<String>,
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                addTracksToGroup(groupId, mediaIds)
                onComplete()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                messageManager.showError("添加到合集失败，请重试")
                onFailure(t)
            }
        }
    }

    suspend fun addTracksToGroup(groupId: Long, mediaIds: List<String>) {
        if (groupId <= 0L || mediaIds.isEmpty()) return
        val requested = normalizeMediaIds(mediaIds).size
        val added = groupRepository.addTracksToGroup(groupId, mediaIds)
        val name = groupRepository.getGroupById(groupId)?.name.orEmpty()
        addSummaryMessage(added, requested, name)?.let { (message, isSuccess) ->
            if (isSuccess) messageManager.showSuccess(message) else messageManager.showInfo(message)
        }
    }

    /** T8：双目标选择器合集 tab 的端口适配（方法体形态，调用方零新增 import）。 */
    fun asBatchAddGroupTarget(): BatchAddGroupTarget = object : BatchAddGroupTarget {
        override val groups: StateFlow<List<AlbumGroupStatsRow>> = this@AlbumGroupsViewModel.groups
        override fun createGroup(name: String) = this@AlbumGroupsViewModel.createGroup(name)
        override fun addTracksToGroupInBackground(
            groupId: Long,
            mediaIds: List<String>,
            onComplete: () -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            this@AlbumGroupsViewModel.addTracksToGroupInBackground(groupId, mediaIds, onComplete, onFailure)
        }
    }

    companion object {
        /**
         * 批量加入合集的 summary 文案钉点（纯函数供 JVM 钉测）。返回 (文案, 是否成功级)；
         * added == requested 时无跳过项，requested 为 0（全空白）时不提示。
         */
        internal fun addSummaryMessage(
            added: Int,
            requested: Int,
            targetName: String
        ): Pair<String, Boolean>? = when {
            added > 0 && added < requested ->
                "已添加 $added 项到合集：$targetName，跳过 ${requested - added} 项" to true
            added > 0 -> "已添加 $added 项到合集：$targetName" to true
            requested > 0 -> "所选项目已在合集：$targetName" to false
            else -> null
        }

        /** 与 PlaylistRepository.addItemsToPlaylist 的 mediaId 推导同口径（trim + 去重）。 */
        internal fun normalizeMediaIds(mediaIds: List<String>): List<String> =
            mediaIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
