package cn.edu.buaa.hzcampus.ui.screens.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal fun featureColumnCount(widthDp: Float, fontScale: Float): Int {
  val minimumCardWidth = 148f * fontScale.coerceAtLeast(1f)
  return ((widthDp - 32f + 12f) / (minimumCardWidth + 12f)).toInt().coerceIn(1, 4)
}

/** 两类功能入口共享尺寸策略，窄屏或大字体减少列数，避免挤压文字。 */
@Composable
internal fun FeatureGrid(modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
  val fontScale = LocalDensity.current.fontScale
  BoxWithConstraints(modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(featureColumnCount(maxWidth.value, fontScale)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
  }
}
