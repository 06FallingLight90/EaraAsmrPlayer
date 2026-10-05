package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.domain.model.TagSource
import com.asmr.player.util.TagNormalizer
import com.asmr.player.util.buildTagsToken
import com.asmr.player.util.parseAlbumTags

/**
 * R3-C3：标签/FTS 写族实现（自 LibraryWriteRepository 逐字搬移，逻辑未改）。
 * 门面 [LibraryWriteRepository] 保留全部签名委托至此；调用方零改动。
 */
internal class LibraryTagWriteSupport(private val database: AppDatabase) {
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

    /** 刷新专辑 FTS 行：tags + USER 标签 CSV 合并建 tagsToken（原 VM upsertAlbumFtsIndex 逐字下沉）。 */
    suspend fun upsertAlbumFtsIndex(albumId: Long, entity: AlbumEntity) {
        val userTagsCsv = database.tagDao().getAlbumTagsCsvOnce(albumId, TagSource.USER).orEmpty()
        val combinedTagsCsv = buildString {
            append(entity.tags)
            if (userTagsCsv.isNotBlank()) {
                if (isNotEmpty() && last() != ',') append(',')
                append(userTagsCsv)
            }
        }
        val tagsToken = buildTagsToken(combinedTagsCsv)
        database.albumFtsDao().upsert(
            listOf(
                AlbumFtsEntity(
                    albumId = albumId,
                    title = entity.title,
                    circle = entity.circle,
                    cv = entity.cv,
                    rjCode = entity.rjCode,
                    workId = entity.workId,
                    tagsToken = tagsToken
                )
            )
        )
    }

    /**
     * 按 CSV 整替专辑标签（指定 source）：insertTags → getTagsByNormalized →
     * deleteAlbumTagsByAlbumIdExceptSource（保留 USER 源）→ insertAlbumTags（原 VM upsertAlbumTagsFromCsv 逐字下沉）。
     * 空标签直接返回，无任何写。
     */
    suspend fun upsertAlbumTagsFromCsv(albumId: Long, tagsCsv: String, source: Int) {
        val tags = parseAlbumTags(tagsCsv)
        if (tags.isEmpty()) return

        val tagEntities = tags.map { (name, normalized) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        val tagDao = database.tagDao()
        tagDao.insertTags(tagEntities)

        val normalizedList = tags.map { it.second }
        val persisted = tagDao.getTagsByNormalized(normalizedList)
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        tagDao.deleteAlbumTagsByAlbumIdExceptSource(albumId, TagSource.USER)
        val refs = normalizedList.mapNotNull { normalized ->
            val tagId = idByNormalized[normalized] ?: return@mapNotNull null
            AlbumTagEntity(albumId = albumId, tagId = tagId, source = source)
        }
        if (refs.isNotEmpty()) tagDao.insertAlbumTags(refs)
    }

    /**
     * 首次启动把专辑 tags CSV 播种为 AUTO 标签引用（原 VM ensureTagTablesInitialized 的事务段逐字下沉）。
     * countTags 空表判定与专辑清单获取由调用方完成。
     */
    suspend fun seedAutoTagsFromAlbumTags(albums: List<AlbumEntity>) {
        val firstNameByNormalized = LinkedHashMap<String, String>()
        albums.flatMap { parseAlbumTags(it.tags) }.forEach { (name, normalized) ->
            if (!firstNameByNormalized.containsKey(normalized)) firstNameByNormalized[normalized] = name
        }
        if (firstNameByNormalized.isEmpty()) return

        val tagDao = database.tagDao()
        val tagEntities = firstNameByNormalized.map { (normalized, name) ->
            TagEntity(name = name, nameNormalized = normalized)
        }
        tagDao.insertTags(tagEntities)
        val persisted = tagDao.getTagsByNormalized(firstNameByNormalized.keys.toList())
        val idByNormalized = persisted.associateBy({ it.nameNormalized }, { it.id })

        database.withTransaction {
            albums.forEach { album ->
                val pairs = parseAlbumTags(album.tags)
                if (pairs.isEmpty()) return@forEach
                val refs = pairs.mapNotNull { (_, normalized) ->
                    val tagId = idByNormalized[normalized] ?: return@mapNotNull null
                    AlbumTagEntity(albumId = album.id, tagId = tagId, source = TagSource.AUTO)
                }
                if (refs.isNotEmpty()) {
                    tagDao.deleteAlbumTagsByAlbumIdExceptSource(album.id, TagSource.USER)
                    tagDao.insertAlbumTags(refs)
                }
            }
        }
    }
}
