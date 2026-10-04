package com.asmr.player.domain.model

data class LibraryQuerySpec(
    val textQuery: String? = null,
    val includeTagIds: Set<Long> = emptySet(),
    val excludeTagIds: Set<Long> = emptySet(),
    val cvs: Set<String> = emptySet(),
    val circles: Set<String> = emptySet(),
    val source: LibrarySourceFilter? = null,
    val sort: LibrarySort = LibrarySort.AddedDesc
) {
    val hasActiveFilters: Boolean
        get() = includeTagIds.isNotEmpty() ||
            excludeTagIds.isNotEmpty() ||
            cvs.isNotEmpty() ||
            circles.isNotEmpty() ||
            (source != null && source != LibrarySourceFilter.Both)

    fun filterOnly(): LibraryQuerySpec {
        return copy(textQuery = null, sort = LibrarySort.AddedDesc)
    }

    fun withFiltersFrom(spec: LibraryQuerySpec): LibraryQuerySpec {
        return copy(
            includeTagIds = spec.includeTagIds,
            excludeTagIds = spec.excludeTagIds,
            cvs = spec.cvs,
            circles = spec.circles,
            source = spec.source.takeUnless { it == LibrarySourceFilter.Both }
        )
    }
}

enum class LibrarySourceFilter {
    LocalOnly,
    DownloadOnly,
    LocalAndDownload,
    Both
}

enum class LibrarySort {
    AddedDesc,
    TitleAsc,
    RjAsc,
    CircleAsc,
    CvAsc,
    LastPlayedDesc;

    companion object {
        fun fromStoredName(value: String?): LibrarySort {
            return entries.firstOrNull { it.name == value } ?: AddedDesc
        }
    }
}
