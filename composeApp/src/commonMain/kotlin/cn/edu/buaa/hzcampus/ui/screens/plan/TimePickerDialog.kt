package cn.edu.buaa.hzcampus.ui.screens.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** 点击式时间字段：只读输入框外观，点击后通过 onClick 弹出滚轮弹窗。 */
@Composable
fun TimePickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
    Box(
        Modifier.matchParentSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
          onClick()
        },
    )
  }
}

/** 小号滚轮弹窗：滚动选择 HH:mm，确认后回写；支持清除/取消。 */
@Composable
fun TimeWheelPickerDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  var current by remember(value) { mutableStateOf(value) }
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
      Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TimeWheelPicker(
            label = "",
            value = current,
            onValueChange = { current = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.End),
        ) {
          TextButton(onClick = { onConfirm("") }) { Text("清除") }
          TextButton(onClick = onDismiss) { Text("取消") }
          Button(onClick = { onConfirm(current) }) { Text("确定") }
        }
      }
    }
  }
}
