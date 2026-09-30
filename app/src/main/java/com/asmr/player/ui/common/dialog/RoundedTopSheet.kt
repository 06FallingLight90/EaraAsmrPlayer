package com.asmr.player.ui.common.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.list.StableWindowInsets

@Composable
internal fun RoundedTopSheet(
    onDismissRequest: () -> Unit,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable () -> Unit
) {
    EdgeToEdgeFullHeightSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 11.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = color,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
                .windowInsetsPadding(StableWindowInsets.navigationBars)
        ) {
            content()
        }
    }
}

