package cn.edu.buaa.hzcampus.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val WHEEL_HEIGHT = 180.dp
private val ITEM_HEIGHT = 36.dp

/** 时分滚轮选择器：小时 0..23 与分钟 0..59 两列垂直滚动。 [value] 为空时展示默认值，滚动后即生成 HH:mm；点击「清除」恢复为空。 */
@Composable
fun TimeWheelPicker(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    defaultHour: Int = 8,
    defaultMinute: Int = 0,
) {
  var hour by
      remember(value) { mutableStateOf(value.substringBefore(":").toIntOrNull() ?: defaultHour) }
  var minute by
      remember(value) {
        mutableStateOf(value.substringAfter(":", "").toIntOrNull() ?: defaultMinute)
      }

  fun emit() {
    onValueChange("${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
  }

  Column(modifier = modifier) {
    if (label.isNotBlank()) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
          TextButton(onClick = { onValueChange("") }) { Text("清除", fontSize = 12.sp) }
        }
      }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(WHEEL_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      WheelColumn(
          values = (0..23).toList(),
          value = hour,
          onValueChange = {
            hour = it
            emit()
          },
          modifier = Modifier.weight(1f),
      )
      Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold)
      WheelColumn(
          values = (0..59).toList(),
          value = minute,
          onValueChange = {
            minute = it
            emit()
          },
          modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun WheelColumn(
    values: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  val listState =
      rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(value).coerceAtLeast(0))
  var selected by remember { mutableStateOf(value) }
  var lastEmitted by remember { mutableStateOf(value) }
  val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

  // 外部值变化（如「下一节课下课」按钮）时滚动到对应项；自身滚动引发的回写不再重复滚动。
  LaunchedEffect(value) {
    if (value != lastEmitted) {
      listState.scrollToItem(values.indexOf(value).coerceAtLeast(0))
      selected = value
    }
  }

  // 依据滚动位置推导视口中心项，作为当前选中值。
  LaunchedEffect(listState, values.size) {
    snapshotFlow { listState.layoutInfo }
        .collect { layoutInfo ->
          val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2f
          val centerItem =
              layoutInfo.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2f - viewportCenter)
              }
          val newValue = centerItem?.index?.let { values.getOrNull(it) } ?: return@collect
          if (newValue != selected) {
            selected = newValue
            lastEmitted = newValue
            onValueChange(newValue)
          }
        }
  }

  Box(modifier = modifier.fillMaxSize()) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .align(Alignment.Center)
                .height(ITEM_HEIGHT)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    )
    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = (WHEEL_HEIGHT - ITEM_HEIGHT) / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      items(values) { itemValue ->
        val isSelected = itemValue == selected
        Box(
            modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = itemValue.toString().padStart(2, '0'),
              fontSize = if (isSelected) 18.sp else 14.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color =
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}
