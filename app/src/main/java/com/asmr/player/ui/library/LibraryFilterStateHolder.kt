package com.asmr.player.ui.library

import com.asmr.player.data.repository.LibraryReadRepository
import com.asmr.player.data.repository.LibraryWriteRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.LibraryFilterPreset
import com.asmr.player.domain.model.LibraryQuerySpec
import com.asmr.player.domain.model.LibrarySort
import com.asmr.player.domain.model.LibrarySourceFilter
import com.asmr.player.domain.model.PersistedLibraryFilters
import com.asmr.player.domain.model.TagWithCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * R3-C1：LibraryViewModel 过滤/预设族 State Holder（自 VM 逐字搬移，逻辑未改）。
 * _querySpec 的唯一所有者；VM 外壳与标签 holder 经 [querySpec] 只读引用、
 * [removeTagFilters] 供标签删除联动清理过滤。
 */
class LibraryFilterStateHolder(
    private val scope: CoroutineScope,
    private val readRepository: LibraryReadRepository,
    private val writeRepository: LibraryWriteRepository,
    private val settingsRepository: SettingsRepository,
    private val availableTags: StateFlow<List<TagWithCount>>,
) {
    private val _querySpec = MutableStateFlow(LibraryQuerySpec())
    val querySpec: StateFlow<LibraryQuerySpec> = _querySpec.asStateFlow()

    fun setSearchQuery(query: String) {
        val trimmed = query.trim()
        val newText = trimmed.ifBlank { null }
        _querySpec.update { current ->
            if (current.textQuery == newText) current else current.copy(textQuery = newText)
        }
    }

    fun setSort(sort: LibrarySort) {
        _querySpec.update { current ->
            if (current.sort == sort) current else current.copy(sort = sort)
        }
        scope.launch(Dispatchers.IO) {
            writeRepository.setLibrarySort(sort)
        }
    }

    fun setSourceFilter(filter: LibrarySourceFilter?) {
        updateFilters { current -> current.copy(source = filter.takeUnless { it == LibrarySourceFilter.Both }) }
    }

    fun applyFilters(spec: LibraryQuerySpec) {
        applyFilters(PersistedLibraryFilters.fromSpec(spec))
    }

    private fun applyFilters(filters: PersistedLibraryFilters) {
        val sanitized = sanitizeFiltersAgainstAvailableTags(filters)
        _querySpec.update { current ->
            sanitized.applyTo(current)
        }
        scope.launch(Dispatchers.IO) {
            writeRepository.setLibraryFilters(sanitized)
        }
    }

    fun toggleTag(tagId: Long) {
        updateFilters { current ->
            val updated = current.includeTagIds.toMutableSet()
            if (!updated.add(tagId)) updated.remove(tagId)
            current.copy(includeTagIds = updated)
        }
    }

    fun toggleCircle(circle: String) {
        val normalized = circle.trim()
        if (normalized.isBlank()) return
        updateFilters { current ->
            val updated = current.circles.toMutableSet()
            if (!updated.add(normalized)) updated.remove(normalized)
            current.copy(circles = updated)
        }
    }

    fun toggleCv(cv: String) {
        val normalized = cv.trim()
        if (normalized.isBlank()) return
        updateFilters { current ->
            val updated = current.cvs.toMutableSet()
            if (!updated.add(normalized)) updated.remove(normalized)
            current.copy(cvs = updated)
        }
    }

    fun clearFilters() {
        applyFilters(PersistedLibraryFilters.Empty)
    }

    fun applyPreset(preset: LibraryFilterPreset) {
        applyFilters(preset.spec)
    }

    fun savePreset(name: String, spec: LibraryQuerySpec = querySpec.value) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        scope.launch(Dispatchers.IO) {
            writeRepository.saveLibraryPreset(trimmed, spec)
        }
    }

    fun deletePreset(id: String) {
        scope.launch(Dispatchers.IO) {
            writeRepository.deleteLibraryPreset(id)
        }
    }

    fun setLibraryViewMode(mode: Int) {
        scope.launch {
            settingsRepository.setLibraryViewMode(mode)
        }
    }

    /** R3-C1：标签删除联动——把被删 tagId 的过滤从当前 querySpec 移除并持久化（原 deleteUserTag 内联段）。 */
    suspend fun removeTagFilters(updated: PersistedLibraryFilters) {
        _querySpec.update { current -> updated.applyTo(current) }
        writeRepository.setLibraryFilters(updated)
    }

    suspend fun restoreLibraryPreferences() {
        val storedSort = runCatching { readRepository.librarySort.first() }.getOrDefault(LibrarySort.AddedDesc)
        val storedFilters = runCatching { readRepository.libraryFilters.first() }.getOrDefault(PersistedLibraryFilters.Empty)
        val sanitized = sanitizeFiltersAgainstDatabase(storedFilters)
        _querySpec.update { current ->
            sanitized.applyTo(current.copy(sort = storedSort))
        }
        if (sanitized != storedFilters.normalized()) {
            writeRepository.setLibraryFilters(sanitized)
        }
    }

    private fun updateFilters(transform: (LibraryQuerySpec) -> LibraryQuerySpec) {
        val nextFilters = transform(_querySpec.value).filterOnly()
        applyFilters(nextFilters)
    }

    private fun sanitizeFiltersAgainstAvailableTags(filters: PersistedLibraryFilters): PersistedLibraryFilters {
        val validTagIds = availableTags.value.map { it.id }.toSet()
        if (validTagIds.isEmpty() && (filters.includeTagIds.isNotEmpty() || filters.excludeTagIds.isNotEmpty())) {
            return filters.normalized()
        }
        return filters.normalized(validTagIds)
    }

    private suspend fun sanitizeFiltersAgainstDatabase(filters: PersistedLibraryFilters): PersistedLibraryFilters {
        val normalized = filters.normalized()
        val requestedTagIds = (normalized.includeTagIds + normalized.excludeTagIds).filter { it > 0L }
        if (requestedTagIds.isEmpty()) return normalized
        val existing = readRepository.getExistingTagIds(requestedTagIds).toSet()
        return normalized.normalized(existing)
    }
}
