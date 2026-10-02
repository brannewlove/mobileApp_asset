package com.antigravity.assetmanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antigravity.assetmanager.ui.theme.AppColors

/**
 * CSS .glass 및 .gradient-bg 스타일을 재현한 글래스모피즘 컴포저블
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = AppColors.BgCard,
        border = BorderStroke(1.dp, AppColors.BorderGlass)
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
