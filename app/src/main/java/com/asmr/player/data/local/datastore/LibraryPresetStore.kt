package com.asmr.player.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asmr.player.domain.model.LibraryFilterPreset
import com.asmr.player.domain.model.LibraryQuerySpec
import com.asmr.player.domain.model.LibrarySort
import com.asmr.player.domain.model.LibrarySourceFilter
import com.asmr.player.domain.model.PersistedLibraryFilters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

internal val Context.libraryDataStore by preferencesDataStore(name = "library")

private data class PersistedLibraryFiltersWire(
    val includeTagIds: List<Long>? = null,
    val excludeTagIds: List<Long>? = null,
    val circles: List<String>? = null,
    val cvs: List<String>? = null,
    val source: String? = null
) {
    fun toFilters(): PersistedLibraryFilters {
        return PersistedLibraryFilters(
            includeTagIds = includeTagIds.orEmpty().toSet(),
            excludeTagIds = excludeTagIds.orEmpty().toSet(),
            circles = circles.orEmpty().toSet(),
            cvs = cvs.orEmpty().toSet(),
            source = LibrarySourceFilter.entries.firstOrNull { it.name == source }
        ).normalized()
    }

    companion object {
        fun fromFilters(filters: PersistedLibraryFilters): PersistedLibraryFiltersWire {
            val normalized = filters.normalized()
            return PersistedLibraryFiltersWire(
                includeTagIds = normalized.includeTagIds.toList(),
                excludeTagIds = normalized.excludeTagIds.toList(),
                circles = normalized.circles.toList(),
                cvs = normalized.cvs.toList(),
                source = normalized.source?.name
            )
        }
    }
}

class LibraryPreferencesStore(
    private val context: Context,
    private val gson: Gson = Gson(),
    private val dataStore: DataStore<Preferences> = context.libraryDataStore
) {
    private val presetsKey: Preferences.Key<String> = stringPreferencesKey("filter_presets_v1")
    private val sortKey: Preferences.Key<String> = stringPreferencesKey("library_sort_v1")
    private val filtersKey: Preferences.Key<String> = stringPreferencesKey("library_filters_v1")

    val presets: Flow<List<LibraryFilterPreset>> = dataStore.data.map { prefs ->
        val json = prefs[presetsKey].orEmpty()
        if (json.isBlank()) return@map emptyList()
        runCatching {
            val t = object : TypeToken<List<LibraryFilterPreset>>() {}.type
            gson.fromJson<List<LibraryFilterPreset>>(json, t).orEmpty()
        }.getOrDefault(emptyList())
    }

    val sort: Flow<LibrarySort> = dataStore.data.map { prefs ->
        LibrarySort.fromStoredName(prefs[sortKey])
    }

    val filters: Flow<PersistedLibraryFilters> = dataStore.data.map { prefs ->
        decodeFilters(prefs[filtersKey])
    }

    suspend fun setSort(sort: LibrarySort) {
        dataStore.edit { prefs ->
            prefs[sortKey] = sort.name
        }
    }

    suspend fun setFilters(filters: PersistedLibraryFilters) {
        dataStore.edit { prefs ->
            val normalized = filters.normalized()
            if (normalized.hasActiveFilters) {
                prefs[filtersKey] = gson.toJson(PersistedLibraryFiltersWire.fromFilters(normalized))
            } else {
                prefs.remove(filtersKey)
            }
        }
    }

    suspend fun savePreset(name: String, spec: LibraryQuerySpec): LibraryFilterPreset {
        val preset = LibraryFilterPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            spec = PersistedLibraryFilters.fromSpec(spec).toQuerySpec()
        )
        dataStore.edit { prefs ->
            val current = readPresetsFromPrefs(prefs)
            val updated = (current + preset)
                .distinctBy { it.id }
                .takeLast(50)
            prefs[presetsKey] = gson.toJson(updated)
        }
        return preset
    }

    suspend fun deletePreset(id: String) {
        dataStore.edit { prefs ->
            val current = readPresetsFromPrefs(prefs)
            val updated = current.filterNot { it.id == id }
            prefs[presetsKey] = gson.toJson(updated)
        }
    }

    private fun readPresetsFromPrefs(prefs: Preferences): List<LibraryFilterPreset> {
        val json = prefs[presetsKey].orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val t = object : TypeToken<List<LibraryFilterPreset>>() {}.type
            gson.fromJson<List<LibraryFilterPreset>>(json, t).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun decodeFilters(json: String?): PersistedLibraryFilters {
        if (json.isNullOrBlank()) return PersistedLibraryFilters.Empty
        return runCatching {
            gson.fromJson(json, PersistedLibraryFiltersWire::class.java)?.toFilters()
        }.getOrNull() ?: PersistedLibraryFilters.Empty
    }
}
