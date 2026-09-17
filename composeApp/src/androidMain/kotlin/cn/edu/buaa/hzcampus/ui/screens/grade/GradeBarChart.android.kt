package cn.edu.buaa.hzcampus.ui.screens.grade

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.columnSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.ProvideVicoTheme
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import com.patrykandpatrick.vico.multiplatform.m3.common.rememberM3VicoTheme
import kotlin.math.abs

/**
 * Android 的成绩图表实现，使用 Vico Compose Multiplatform 版（2.4.4，与本项目的 Kotlin 2.3.20 / Compose Multiplatform
 * 1.10.3 / Material3 1.9.0 对齐）。
 *
 * 其余平台（JVM 桌面端 / iOS / JS / Wasm）沿用 commonMain 里零依赖的自绘 [CanvasBarChart]。
 */
@Composable
internal actual fun GradeBarChart(spec: GradeBarChartSpec, modifier: Modifier) {
  val colorScheme = MaterialTheme.colorScheme
  val vicoTheme =
      rememberM3VicoTheme(
          columnCartesianLayerColors = listOf(colorScheme.primary),
          lineCartesianLayerColors = listOf(colorScheme.primary),
          lineColor = colorScheme.outlineVariant,
          textColor = colorScheme.onSurfaceVariant,
      )

  val labelStyle = remember { TextStyle(color = colorScheme.onSurfaceVariant, fontSize = 10.sp) }
  val valueStyle = remember { TextStyle(color = colorScheme.onSurface, fontSize = 10.sp) }

  val currentValueFormatter by rememberUpdatedState(spec.valueFormatter)
  val valueFormatter = remember {
    CartesianValueFormatter { _, value, _ -> currentValueFormatter(value) }
  }
  val axisValueFormatter = remember {
    CartesianValueFormatter { _, value, _ ->
      if (abs(value - value.toInt()) < 0.05) value.toInt().toString() else formatNumber(value)
    }
  }
  val xLabels = spec.xLabels
  val xValueFormatter =
      remember(xLabels) {
        CartesianValueFormatter { _, value, _ -> xLabels.getOrElse(value.toInt()) { "" } }
      }

  val modelProducer = remember { CartesianChartModelProducer() }
  LaunchedEffect(spec.values, spec.xLabels, spec.yMax) {
    modelProducer.runTransaction { columnSeries { series(spec.values) } }
  }

  val columnLayer =
      rememberColumnCartesianLayer(
          columnProvider =
              ColumnCartesianLayer.ColumnProvider.series(
                  rememberLineComponent(fill = Fill(colorScheme.primary), thickness = 16.dp)
              ),
          dataLabel = rememberTextComponent(style = valueStyle),
          dataLabelValueFormatter = valueFormatter,
          rangeProvider =
              remember(spec.yMax) {
                CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = spec.yMax)
              },
      )
  val chart =
      rememberCartesianChart(
          columnLayer,
          startAxis =
              VerticalAxis.rememberStart(
                  label = rememberTextComponent(style = labelStyle),
                  valueFormatter = axisValueFormatter,
              ),
          bottomAxis =
              HorizontalAxis.rememberBottom(
                  label = rememberTextComponent(style = labelStyle),
                  valueFormatter = xValueFormatter,
                  labelRotationDegrees = 0f,
              ),
      )

  ProvideVicoTheme(vicoTheme) {
    CartesianChartHost(chart = chart, modelProducer = modelProducer, modifier = modifier)
  }
}
