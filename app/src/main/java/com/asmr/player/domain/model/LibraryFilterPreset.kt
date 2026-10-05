package com.asmr.player.domain.model

/**
 * R3-B5a：从 ui/library/LibraryPresetStore.kt 拆出下沉（UI 消费的过滤预设纯类型，
 * 与 LibraryQuerySpec 三件同族；持久化机制 LibraryPreferencesStore 留 data 层）。
 * 纯搬迁，逻辑未改。
 */

data class LibraryFilterPreset(
    val id: String,
    val name: String,
    val spec: LibraryQuerySpec
)

data class PersistedLibraryFilters(
    val includeTagIds: Set<Long> = emptySet(),
    val excludeTagIds: Set<Long> = emptySet(),
    val circles: Set<String> = emptySet(),
    val cvs: Set<String> = emptySet(),
    val source: LibrarySourceFilter? = null
) {
    val hasActiveFilters: Boolean
        get() = toQuerySpec().hasActiveFilters

    fun toQuerySpec(): LibraryQuerySpec {
        return LibraryQuerySpec(
            includeTagIds = includeTagIds,
            excludeTagIds = excludeTagIds,
            circles = circles,
            cvs = cvs,
            source = source.takeUnless { it == LibrarySourceFilter.Both }
        )
    }

    fun applyTo(spec: LibraryQuerySpec): LibraryQuerySpec {
        return spec.withFiltersFrom(toQuerySpec())
    }

    fun normalized(validTagIds: Set<Long>? = null): PersistedLibraryFilters {
        fun cleanTags(ids: Set<Long>): Set<Long> {
            return ids
                .asSequence()
                .filter { it > 0L }
                .filter { validTagIds == null || validTagIds.contains(it) }
                .toCollection(linkedSetOf())
        }

        fun cleanText(values: Set<String>): Set<String> {
            return values
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toCollection(linkedSetOf())
        }

        return PersistedLibraryFilters(
            includeTagIds = cleanTags(includeTagIds),
            excludeTagIds = cleanTags(excludeTagIds),
            circles = cleanText(circles),
            cvs = cleanText(cvs),
            source = source.takeUnless { it == LibrarySourceFilter.Both }
        )
    }

    companion object {
        val Empty = PersistedLibraryFilters()

        fun fromSpec(spec: LibraryQuerySpec): PersistedLibraryFilters {
            return PersistedLibraryFilters(
                includeTagIds = spec.includeTagIds,
                excludeTagIds = spec.excludeTagIds,
                circles = spec.circles,
                cvs = spec.cvs,
                source = spec.source
            ).normalized()
        }
    }
}
