package com.asmr.player.ui.common.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asmr.player.domain.model.AppVolume
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.AudioOutputRouteKind

/** 硬件音量悬浮条；R3-B2 环1 消解自 main/MainChromeUi.kt 迁入（main 与 ui.player 共用）。 */
@Composable
internal fun HardwareVolumeOverlay(
    volumePercent: Int,
    audioOutputRouteKind: AudioOutputRouteKind,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onInteractionActiveChanged: (Boolean) -> Unit,
    warningSessionState: AppVolumeWarningSessionState,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val accentColor = colorScheme.primary
    val protectedVolumeChangeState = rememberProtectedAppVolumeChangeState(
        warningSessionState = warningSessionState,
        onApplyVolumeChange = onVolumeChange
    )

    Box(
        modifier = modifier
            .padding(12.dp)
            .graphicsLayer { clip = false }
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.92f else 0.96f),
            contentColor = colorScheme.onSurface,
            shadowElevation = if (colorScheme.isDark) 0.dp else 10.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudioOutputRouteIcon(
                    routeKind = audioOutputRouteKind,
                    isMuted = volumePercent == 0,
                    tint = accentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onToggleMute)
                )
                AppVolumeVerticalSlider(
                    valuePercent = volumePercent,
                    onValueChange = { nextPercent, source ->
                        protectedVolumeChangeState.requestVolumeChange(
                            currentPercent = volumePercent,
                            targetPercent = nextPercent,
                            source = source
                        )
                    },
                    accentColor = accentColor,
                    onInteractionActiveChanged = onInteractionActiveChanged
                )
                Text(
                    text = "${AppVolume.clampPercent(volumePercent)}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    AppVolumeHearingWarningDialog(state = protectedVolumeChangeState)
}
