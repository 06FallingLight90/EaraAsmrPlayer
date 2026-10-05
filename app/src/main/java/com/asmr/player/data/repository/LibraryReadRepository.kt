package com.asmr.player.data.repository

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import android.content.Context
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumTagsCsv
import com.asmr.player.data.local.datastore.LibraryPreferencesStore
import com.asmr.player.data.local.datastore.PersistedLibraryFilters
import com.asmr.player.domain.model.LibraryFilterPreset
import com.asmr.player.domain.model.LibrarySort
import com.asmr.player.domain.model.LibraryTrackAlbumHeaderRow
import com.asmr.player.domain.model.LibraryTrackRow
import com.asmr.player.domain.model.TagWithCount
import com.asmr.player.data.local.db.dao.TrackTagsCsv
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.domain.model.LibraryQuerySpec
import kotlinx.coroutines.flow.Flow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 库读取数据访问（R2-B4c 自 LibraryViewModel 下沉）。
 * 冷流与 PagingSource 均为透传，映射/合并/去重等编排归调用方 ViewModel；
 * 一次性查询为单行透传。行为契约见 docs/behavior-notes/library-delete-family.md 附录六。
 */
@Singleton
class LibraryReadRepository @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) {
    /** R3-B5a：预设/排序/过滤持久化读出口（原 VM 手动构造 LibraryPreferencesStore 的读侧透传）。 */
    private val preferencesStore = LibraryPreferencesStore(context)

    val libraryFilterPresets: Flow<List<LibraryFilterPreset>> get() = preferencesStore.presets

    val librarySort: Flow<LibrarySort> get() = preferencesStore.sort

    val libraryFilters: Flow<PersistedLibraryFilters> get() = preferencesStore.filters
    // ---------- 流（原 VM StateFlow 直出的 DAO 段逐字下沉） ----------

    fun observeTagsWithCounts(userSource: Int): Flow<List<TagWithCount>> = database.tagDao().getTagsWithCounts(userSource)

    fun observeAlbumTagsBySource(source: Int): Flow<List<AlbumTagsCsv>> = database.tagDao().getAlbumTagsBySource(source)

    fun observeTrackTagsBySource(source: Int): Flow<List<TrackTagsCsv>> = database.trackTagDao().getTrackTagsBySource(source)

    fun observeDistinctCircles(): Flow<List<String>> = database.albumDao().getDistinctCircles()

    fun observeDistinctCvs(): Flow<List<String>> = database.albumDao().getDistinctCvs()

    fun observeLibraryTracks(query: SupportSQLiteQuery): Flow<List<LibraryTrackRow>> = database.trackDao().queryLibraryTracks(query)

    fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>> = database.trackDao().getTracksForAlbum(albumId)

    // ---------- PagingSource factory（原 VM Pager pagingSourceFactory 参数化） ----------

    fun albumsPaged(query: SupportSQLiteQuery): PagingSource<Int, AlbumEntity> = database.albumDao().queryAlbumsPaged(query)

    /** R3-B1d：spec 出口——ui 侧不再直接构建 Room SQL（消 ui→db.query 边，防 SCC 回潮）。 */
    fun albumsPaged(spec: LibraryQuerySpec): PagingSource<Int, AlbumEntity> = albumsPaged(LibraryQueryBuilder.build(spec))

    /** R3-B1d：最近收听（LastPlayedDesc 固定排序）出口——RecentAlbumsPanel 不再自建 SQL。 */
    fun observeAlbumsByLastPlayed(): Flow<List<AlbumEntity>> =
        database.albumDao().queryAlbums(LibraryQueryBuilder.build(LibraryQuerySpec(sort = LibrarySort.LastPlayedDesc)))

    fun libraryTrackAlbumHeadersPaged(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackAlbumHeaderRow> =
        database.trackDao().queryLibraryTrackAlbumHeadersPaged(query)

    /** R3-B5a：spec 出口——LibraryTrackQueryBuilder 随迁 data，ui 侧不再构建 Room SQL。 */
    fun libraryTrackAlbumHeadersPaged(spec: LibraryQuerySpec): PagingSource<Int, LibraryTrackAlbumHeaderRow> =
        libraryTrackAlbumHeadersPaged(LibraryTrackQueryBuilder.buildAlbumHeaders(spec))

    fun observeLibraryTracks(spec: LibraryQuerySpec): Flow<List<LibraryTrackRow>> =
        observeLibraryTracks(LibraryTrackQueryBuilder.build(spec))

    fun observeLibraryTracksForAlbum(spec: LibraryQuerySpec, albumId: Long): Flow<List<LibraryTrackRow>> =
        observeLibraryTracks(LibraryTrackQueryBuilder.buildForAlbum(spec, albumId))

    // ---------- 一次性查询 ----------

    suspend fun getAlbumById(albumId: Long): AlbumEntity? = database.albumDao().getAlbumById(albumId)

    suspend fun getAlbumByWorkIdOnce(workId: String): AlbumEntity? = database.albumDao().getAlbumByWorkIdOnce(workId)

    suspend fun getAllAlbumsOnce(): List<AlbumEntity> = database.albumDao().getAllAlbumsOnce()

    suspend fun getTracksForAlbumOnce(albumId: Long): List<TrackEntity> = database.trackDao().getTracksForAlbumOnce(albumId)

    suspend fun getTracksByIdsOnce(ids: List<Long>): List<TrackEntity> = database.trackDao().getTracksByIdsOnce(ids)

    suspend fun getTrackByIdOnce(trackId: Long): TrackEntity? = database.trackDao().getTrackByIdOnce(trackId)

    suspend fun getExistingTagIds(ids: List<Long>): List<Long> = database.tagDao().getExistingTagIds(ids)

    suspend fun countTags(): Long = database.tagDao().countTags()

    suspend fun getOnlineSavedResourcesForAlbum(albumId: Long): List<OnlineSavedResourceEntity> =
        database.onlineSavedResourceDao().getForAlbumOnce(albumId)

    suspend fun getDownloadTaskByRootDir(rootDir: String): DownloadTaskEntity? = database.downloadDao().getTaskByRootDir(rootDir)
}
