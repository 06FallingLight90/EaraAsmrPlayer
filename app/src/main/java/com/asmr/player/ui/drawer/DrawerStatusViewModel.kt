package com.asmr.player.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.BuildConfig
import com.asmr.player.util.SiteLatencyProbe
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.util.ASMR_ONE_SITE_TEST_FAILURE_MESSAGE
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SiteStatusType { Unknown, Testing, Ok, Fail }

data class SiteStatus(
    val type: SiteStatusType = SiteStatusType.Unknown,
    val latencyMs: Long? = null
)

@HiltViewModel
class DrawerStatusViewModel @Inject constructor(
    private val siteLatencyProbe: SiteLatencyProbe,
    private val settingsRepository: SettingsRepository,
    private val messageManager: MessageManager
) : ViewModel() {
    val asmrOneSite: StateFlow<Int> = settingsRepository.asmrOneSite
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 200)

    private val _dlsite = MutableStateFlow(SiteStatus())
    val dlsite: StateFlow<SiteStatus> = _dlsite

    private val _asmr = MutableStateFlow(SiteStatus())
    val asmr: StateFlow<SiteStatus> = _asmr
    private var asmrTestJob: Job? = null

    fun testDlsite() {
        viewModelScope.launch(Dispatchers.IO) {
            _dlsite.value = SiteStatus(type = SiteStatusType.Testing)
            val latency = measure("https://www.dlsite.com/")
            _dlsite.value = latency.toStatus()
        }
    }

    fun testAsmrOne() {
        val site = asmrOneSite.value
        // 直连站点使用搜索接口，备用站点使用其 tracks 接口，确保测试的是当前选择的目标。
        val url = AsmrOneEndpoint.directBaseUrl(site)
            ?.let { baseUrl -> "${baseUrl}search/RJ01000000" }
            ?: BuildConfig.LISTEN_TOGETHER_BASE_URL
                .trim()
                .trimEnd('/')
                .takeIf { it.isNotBlank() }
                ?.let { baseUrl -> "$baseUrl/api/asmr-one/tracks?rj=RJ01000000" }
        asmrTestJob?.cancel()
        asmrTestJob = viewModelScope.launch(Dispatchers.IO) {
            _asmr.value = SiteStatus(type = SiteStatusType.Testing)
            val latency = url?.let { measure(it, suppressAutomaticError = true) }
            coroutineContext.ensureActive()
            _asmr.value = latency.toStatus()
            if (latency == null) {
                messageManager.showError(ASMR_ONE_SITE_TEST_FAILURE_MESSAGE)
            }
        }
    }

    fun setAsmrOneSite(site: Int) {
        if (site == asmrOneSite.value) return
        asmrTestJob?.cancel()
        _asmr.value = SiteStatus()
        viewModelScope.launch {
            settingsRepository.setAsmrOneSite(site)
        }
    }

    private fun measure(url: String, suppressAutomaticError: Boolean = false): Long? =
        siteLatencyProbe.measure(url, suppressAutomaticError)
}

private fun Long?.toStatus(): SiteStatus {
    return if (this == null) SiteStatus(type = SiteStatusType.Fail) else SiteStatus(type = SiteStatusType.Ok, latencyMs = this)
}

