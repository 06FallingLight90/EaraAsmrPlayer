package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.util.TagNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 库写入数据访问（R2-B4a 自 LibraryViewModel 下沉）。
 * 只搬数据访问：事务边界、删除顺序、runCatching 吞错范围与原实现逐一对应，
 * 行为契约见 docs/behavior-notes/library-delete-family.md。
 * 编排（消息提示、FTS 刷新时机、进度回调、querySpec 更新）仍归调用方 ViewModel。
 */
@Singleton
class LibraryWriteRepository @Inject constructor(
    private val database: AppDatabase,
) {
    /** 事务内整替专辑 USER 标签：先删后插，tag 行按归一名复用（IGNORE 冲突策略），其它源不动。 */
    suspend fun replaceAlbumUserTags(albumId: Long, tags: List<Pair<String, String>>) {
        val tagDao = database.tagDao()
        database.withTransaction {
            tagDao.deleteAlbumTagsByAlbumIdAndSource(albumId, TagSource.USER)
            if (tags.isNotEmpty()) {
                val tagEntities = tags.map { (name, normalized) ->
                    TagEntity(name = name, nameNormalized = normalized)
                }
                tagDao.insertTags(tagEntities)
                val persisted = tagDao.getTagsByNormalized(tags.map { it.second })
                val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                val refs = tags.mapNotNull { (_, normalized) ->
                    val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                    AlbumTagEntity(albumId = albumId, tagId = tagId, source = TagSource.USER)
                }
                if (refs.isNotEmpty()) tagDao.insertAlbumTags(refs)
            }
        }
    }

    /** 事务内整替音轨 USER 标签，语义同 [replaceAlbumUserTags]。 */
    suspend fun replaceTrackUserTags(trackId: Long, tags: List<Pair<String, String>>) {
        val tagDao = database.tagDao()
        val trackTagDao = database.trackTagDao()
        database.withTransaction {
            trackTagDao.deleteTrackTagsByTrackIdAndSource(trackId, TagSource.USER)
            if (tags.isNotEmpty()) {
                val tagEntities = tags.map { (name, normalized) ->
                    TagEntity(name = name, nameNormalized = normalized)
                }
                tagDao.insertTags(tagEntities)
                val persisted = tagDao.getTagsByNormalized(tags.map { it.second })
                val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })
                val refs = tags.mapNotNull { (_, normalized) ->
                    val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                    TrackTagEntity(trackId = trackId, tagId = tagId, source = TagSource.USER)
                }
                if (refs.isNotEmpty()) trackTagDao.insertTrackTags(refs)
            }
        }
    }

    /**
     * 重命名用户标签。归一名冲突时把原标签的专辑/音轨引用合并到已有标签后删除原标签。
     * @return 受影响专辑 id 集合（事务前捕获；合并分支并入冲突标签的专辑），供调用方 FTS 刷新。
     *   标签缺失或归一名为空早退时同样返回事务前集合（与原实现一致）。
     */
    suspend fun renameUserTag(tagId: Long, newName: String): Set<Long> {
        val tagDao = database.tagDao()
        val albumIds = tagDao.getAlbumIdsForTag(tagId).toMutableSet()
        database.withTransaction {
            val existing = tagDao.getTagById(tagId) ?: return@withTransaction
            val newNormalized = TagNormalizer.normalize(newName)
            if (newNormalized.isBlank()) return@withTransaction

            val conflict = tagDao.getTagByNormalized(newNormalized)
            if (conflict != null && conflict.id != existing.id) {
                albumIds.addAll(tagDao.getAlbumIdsForTag(conflict.id))
                tagDao.moveAlbumTagsToAnotherTag(existing.id, conflict.id)
                database.trackTagDao().moveTrackTagsToAnotherTag(existing.id, conflict.id)
                tagDao.deleteAlbumTagsByTagId(existing.id)
                database.trackTagDao().deleteTrackTagsByTagId(existing.id)
                tagDao.deleteTag(existing.id)
            } else {
                tagDao.updateTag(existing.id, newName, newNormalized)
            }
        }
        return albumIds
    }

    /** 删除用户标签及其全部专辑/音轨引用。@return 事务前关联的专辑 id，供调用方 FTS 刷新。 */
    suspend fun deleteUserTag(tagId: Long): Set<Long> {
        val tagDao = database.tagDao()
        val albumIds = tagDao.getAlbumIdsForTag(tagId)
        database.withTransaction {
            tagDao.deleteAlbumTagsByTagId(tagId)
            database.trackTagDao().deleteTrackTagsByTagId(tagId)
            tagDao.deleteTag(tagId)
        }
        return albumIds.toSet()
    }

    /**
     * 删专辑事务。事务内顺序固定：字幕 → 音轨 → 专辑实体（在线缓存资源 + 专辑行）→ 专辑标签 → FTS 行。
     * 专辑行缺失的早退判定由调用方在事务前完成。
     */
    suspend fun deleteAlbumWithContent(albumId: Long, entity: AlbumEntity) {
        database.withTransaction {
            database.trackDao().deleteSubtitlesForAlbum(albumId)
            database.trackDao().deleteTracksForAlbum(albumId)
            deleteAlbumEntity(entity)
            database.tagDao().deleteAlbumTagsByAlbumId(albumId)
            database.albumFtsDao().deleteByAlbumId(albumId)
        }
    }

    /**
     * 目录树删除事务：已验证音轨（远程字幕源/字幕/音轨标签/音轨行）+ 指定在线资源 + 专辑目录树缓存。
     * trackIds/resourceIds 的验证与过滤由调用方在事务前完成。
     */
    suspend fun deleteVerifiedTracksAndResources(albumId: Long, verifiedTrackIds: List<Long>, resourceIds: List<Long>) {
        database.withTransaction {
            if (verifiedTrackIds.isNotEmpty()) {
                database.remoteSubtitleSourceDao().deleteByTrackIds(verifiedTrackIds)
                database.trackDao().deleteSubtitlesForTracks(verifiedTrackIds)
                database.trackTagDao().deleteTrackTagsByTrackIds(verifiedTrackIds)
                database.trackDao().deleteTracksByIds(verifiedTrackIds)
            }
            if (resourceIds.isNotEmpty()) {
                database.onlineSavedResourceDao().deleteByIds(resourceIds)
            }
            database.localTreeCacheDao().deleteByAlbum(albumId)
        }
    }

    /** 删单音轨事务：每步 runCatching 吞错不中断（与原实现一致），最后清目录树缓存。 */
    suspend fun deleteTrackCompletely(trackId: Long, albumId: Long) {
        database.withTransaction {
            runCatching { database.remoteSubtitleSourceDao().deleteByTrackId(trackId) }
            runCatching { database.trackDao().deleteSubtitlesForTrack(trackId) }
            runCatching { database.trackTagDao().deleteTrackTagsByTrackId(trackId) }
            runCatching { database.trackDao().deleteTrackById(trackId) }
            runCatching { database.localTreeCacheDao().deleteByAlbum(albumId) }
        }
    }

    /** 无事务组合删：专辑字幕 + 音轨行（目录树清理路径用，与原实现一致不加事务）。 */
    suspend fun deleteAlbumTracksAndSubtitles(albumId: Long) {
        database.trackDao().deleteSubtitlesForAlbum(albumId)
        database.trackDao().deleteTracksForAlbum(albumId)
    }

    /** 无事务组合删：单音轨字幕 + 音轨行（目录树清理循环逐轨调用，与原实现一致不加事务）。 */
    suspend fun deleteTrackWithSubtitlesById(trackId: Long) {
        database.trackDao().deleteSubtitlesForTrack(trackId)
        database.trackDao().deleteTrackById(trackId)
    }

    /** 无事务组合删：批量音轨字幕 + 音轨行（在线专辑重扫清理用，空守卫留在调用侧）。 */
    suspend fun deleteTracksWithSubtitles(trackIds: List<Long>) {
        database.trackDao().deleteSubtitlesForTracks(trackIds)
        database.trackDao().deleteTracksByIds(trackIds)
    }

    /** 删专辑实体：在线缓存资源 + 专辑行（原 VM private 方法转公开）。 */
    suspend fun deleteAlbumEntity(entity: AlbumEntity) {
        database.onlineSavedResourceDao().deleteByAlbumId(entity.id)
        database.albumDao().deleteAlbum(entity)
    }
}
