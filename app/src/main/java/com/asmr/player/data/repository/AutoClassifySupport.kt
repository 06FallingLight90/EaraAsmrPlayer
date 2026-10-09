package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumGroupItemDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity

/**
 * T7：合集三类默认 seed + 来源自动归类单点（行为契约见 docs/behavior-notes/collection-auto-classify.md）。
 * - [classify]：albums.source → 默认合集类型（dlsite_download→音声、local_scan→歌曲、null/空白/未知→其它音频）。
 * - [ensureDefaultGroups]：按固定名称判存幂等 seed；任一默认合集首次创建时对存量曲目一次性回填。
 * - [attachTrackToDefaultGroup] / [attachTracksToDefaultGroups]：新入库轨增量挂载（幂等，IGNORE 去重，
 *   用户移除过的不回加——增量只对 tracks 新插轨调用）；source 由调用方传专辑最终定性值，
 *   本类不读写 albums.source（来源定性"永不覆盖"规则不受影响）。
 */
internal class AutoClassifySupport(private val database: AppDatabase) {

    enum class DefaultGroupKind(val fixedName: String) {
        SONGS("歌曲"),
        AUDIO_WORKS("音声"),
        OTHER("其它音频")
    }

    /** 专辑来源 → 默认合集类型（三映射单点；未知/未定性一律落「其它音频」兜底）。 */
    fun classify(source: String?): DefaultGroupKind = when (source?.trim()) {
        AlbumEntity.SOURCE_DLSITE_DOWNLOAD -> DefaultGroupKind.AUDIO_WORKS
        AlbumEntity.SOURCE_LOCAL_SCAN -> DefaultGroupKind.SONGS
        else -> DefaultGroupKind.OTHER
    }

    /** 三类默认合集幂等 seed（固定名称判存，已存在不重建不改名）；任一首次创建则全量回填存量曲目。 */
    suspend fun ensureDefaultGroups() {
        val groupDao = database.albumGroupDao()
        val groupItemDao = database.albumGroupItemDao()
        database.withTransaction {
            var createdAny = false
            DefaultGroupKind.entries.forEach { kind ->
                val existing = groupDao.getGroupByNameOnce(kind.fixedName)
                if (existing == null) {
                    groupDao.insertGroup(AlbumGroupEntity(name = kind.fixedName))
                    createdAny = true
                }
            }
            if (createdAny) {
                backfillStockTracks(groupItemDao)
            }
        }
    }

    /** 单轨挂载（trackPath 解析出 albumId 后走批量路径）；track 不在库/默认合集不存在则静默跳过。 */
    suspend fun attachTrackToDefaultGroup(trackPath: String, source: String?) {
        val path = trackPath.trim()
        if (path.isBlank()) return
        val track = database.trackDao().getTrackByPathOnce(path) ?: return
        attachTracksToDefaultGroups(track.albumId, listOf(track.path), source)
    }

    /**
     * 批量幂等挂载：同一专辑（albumId）的若干新插轨挂到 source 对应的默认合集。
     * (groupId, mediaId) 已存在则跳过（不覆写 itemOrder/createdAt）；新条目 itemOrder 取组内
     * 同专辑 max+1 连续递增（与 AlbumGroupRepository.addAlbumToGroup 追加语义同族）。
     */
    suspend fun attachTracksToDefaultGroups(albumId: Long, trackPaths: List<String>, source: String?) {
        if (albumId <= 0L) return
        val paths = trackPaths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (paths.isEmpty()) return
        val kind = classify(source)
        val group = database.albumGroupDao().getGroupByNameOnce(kind.fixedName) ?: return
        val groupItemDao = database.albumGroupItemDao()
        val existing = groupItemDao.getGroupMediaIdsOnce(group.id).toHashSet()
        var nextOrder = groupItemDao.getMaxItemOrderInAlbum(group.id, albumId) + 1
        val toInsert = paths.sorted()
            .filterNot { it in existing }
            .map { path ->
                AlbumGroupItemEntity(groupId = group.id, mediaId = path, itemOrder = nextOrder++)
            }
        if (toInsert.isNotEmpty()) groupItemDao.insertItemsIgnoringConflicts(toInsert)
    }

    /** 存量回填（仅 seed 首建时调用一次）：join tracks→albums 按来源分组批量 IGNORE 插入，组内 itemOrder 0..n-1。 */
    private suspend fun backfillStockTracks(groupItemDao: AlbumGroupItemDao) {
        val rows = groupItemDao.getAllTrackSourceRowsOnce()
        val groupDao = database.albumGroupDao()
        DefaultGroupKind.entries.forEach { kind ->
            val group = groupDao.getGroupByNameOnce(kind.fixedName) ?: return@forEach
            val paths = rows.asSequence()
                .filter { classify(it.source) == kind }
                .map { it.path.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
            if (paths.isEmpty()) return@forEach
            val items = paths.mapIndexed { index, path ->
                AlbumGroupItemEntity(groupId = group.id, mediaId = path, itemOrder = index)
            }
            groupItemDao.insertItemsIgnoringConflicts(items)
        }
    }
}
