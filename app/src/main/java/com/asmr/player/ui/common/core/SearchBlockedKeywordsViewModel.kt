package com.asmr.player.ui.common.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 搜索屏蔽词的轻量 ViewModel：屏蔽词属搜索域，供搜索屏蔽词设置分区与
 * 专辑详情的“加入屏蔽词”快捷操作共用，避免 ui.library ↔ ui.settings 双向引用；置于 common/core 以通过 feature-to-feature 白名单。
 * 数据本体在 SettingsRepository（DataStore 持久化）。
 */
@HiltViewModel
class SearchBlockedKeywordsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val searchBlockedKeywords: StateFlow<List<String>> =
        settingsRepository.searchBlockedKeywords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSearchBlockedKeyword(keyword: String) {
        viewModelScope.launch { settingsRepository.addSearchBlockedKeyword(keyword) }
    }

    fun removeSearchBlockedKeyword(keyword: String) {
        viewModelScope.launch { settingsRepository.removeSearchBlockedKeyword(keyword) }
    }
}
