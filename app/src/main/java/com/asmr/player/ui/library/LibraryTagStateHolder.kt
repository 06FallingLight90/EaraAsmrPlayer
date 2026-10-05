package com.asmr.player.ui.library

import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.domain.model.LibraryQuerySpec
import com.asmr.player.domain.model.PersistedLibraryFilters
import com.asmr.player.util.parseAlbumTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * R3-C1：LibraryViewModel 用户标签族 State Holder（自 VM 逐字搬移，逻辑未改）。
 * - FTS 索引与标签写直达 repository（VM 原为 1 行代理，随迁消除）。
 * - deleteUserTag 触及过滤状态的部分经 [onFiltersRemoved] 回调交还外壳
 *   （_querySpec 归过滤族/外壳所有，标签族不持有写权）。
 * - loadInheritedTagsForAlbum 的实体解引用已下沉 LibraryReadRepository 出口。
 */
class LibraryTagStateHolder(
    private val scope: CoroutineScope,
    private val readRepository: LibraryReadRepository,
    private val writeRepository: LibraryWriteRepository,
    private val querySpec: StateFlow<LibraryQuerySpec>,
    private val onFiltersRemoved: suspend (PersistedLibraryFilters) -> Unit,
    private val userTagsByAlbumId: StateFlow<Map<Long, List<String>>>,
) {
    fun setUserTagsForAlbum(albumId: Long, tagsCsv: String) {
        scope.launch(Dispatchers.IO) {
            val entity = readRepository.getAlbumById(albumId) ?: return@launch
            writeRepository.replaceAlbumUserTags(albumId, parseAlbumTags(tagsCsv))
            writeRepository.upsertAlbumFtsIndex(albumId, entity)
        }
    }

    fun setUserTagsForTrack(trackId: Long, tagsCsv: String) {
        scope.launch(Dispatchers.IO) {
            writeRepository.replaceTrackUserTags(trackId, parseAlbumTags(tagsCsv))
        }
    }

    fun renameUserTag(tagId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val albumIds = writeRepository.renameUserTag(tagId, trimmed)
            albumIds.forEach { albumId ->
                val entity = readRepository.getAlbumById(albumId) ?: return@forEach
                writeRepository.upsertAlbumFtsIndex(albumId, entity)
            }
        }
    }

    fun deleteUserTag(tagId: Long) {
        scope.launch(Dispatchers.IO) {
            val albumIds = writeRepository.deleteUserTag(tagId)
            val currentFilters = PersistedLibraryFilters.fromSpec(querySpec.value)
            if (currentFilters.includeTagIds.contains(tagId) || currentFilters.excludeTagIds.contains(tagId)) {
                val updatedFilters = currentFilters.copy(
                    includeTagIds = currentFilters.includeTagIds - tagId,
                    excludeTagIds = currentFilters.excludeTagIds - tagId
                )
                onFiltersRemoved(updatedFilters)
            }
            albumIds.forEach { albumId ->
                val entity = readRepository.getAlbumById(albumId) ?: return@forEach
                writeRepository.upsertAlbumFtsIndex(albumId, entity)
            }
        }
    }

    suspend fun loadInheritedTagsForAlbum(albumId: Long): List<String> {
        return readRepository.loadInheritedTagsForAlbum(albumId, userTagsByAlbumId.value[albumId].orEmpty())
    }

    suspend fun ensureTagTablesInitialized() {
        val tagCount = runCatching { readRepository.countTags() }.getOrDefault(0L)
        if (tagCount > 0L) return

        val albums = runCatching { readRepository.getAllAlbumsOnce() }.getOrDefault(emptyList())
        writeRepository.seedAutoTagsFromAlbumTags(albums)
    }
}
