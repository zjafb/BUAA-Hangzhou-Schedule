package cn.edu.buaa.hzcampus.ui.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.edu.buaa.hzcampus.api.storage.PlanStore
import cn.edu.buaa.hzcampus.model.dto.PlanTask
import cn.edu.buaa.hzcampus.model.dto.TodayClass
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val PLAN_COLORS =
    listOf(0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFF44336, 0xFF9C27B0, 0xFF607D8B)

/** 校验 HH:mm 格式的 24 小时制时间。 */
private fun isHHmm(s: String): Boolean {
  if (s.length != 5 || s[2] != ':') return false
  val hour = s.substring(0, 2).toIntOrNull() ?: return false
  val minute = s.substring(3, 5).toIntOrNull() ?: return false
  return hour in 0..23 && minute in 0..59
}

/** 解析今日课程的时间描述（HH:mm-HH:mm），返回 (开始, 结束)。 */
private fun classStartEnd(time: String?): Pair<String, String>? {
  val t = time?.trim().orEmpty()
  if (t.isEmpty()) return null
  val parts = t.split("-")
  if (parts.size < 2) return null
  val start = parts.first().trim()
  val end = parts.last().trim()
  if (!isHHmm(start) || !isHHmm(end)) return null
  return start to end
}

/** HH:mm 减去 minutes 分钟，返回 HH:mm（跨 0 点回绕）。 */
private fun minusMinutes(time: String, minutes: Int): String {
  val parts = time.split(":")
  val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
  val minute = parts.getOrNull(1)?.toIntOrNull() ?: return time
  val total = (hour * 60 + minute - minutes).mod(24 * 60)
  return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}

/** 找到 reference 之后（不含）最早的课程，返回其下课时间；无后续课程返回 null。 */
private fun nextClassEndAfter(reference: String, classes: List<TodayClass>): String? =
    classes
        .mapNotNull { classStartEnd(it.time) }
        .filter { it.first > reference }
        .minByOrNull { it.first }
        ?.second

/** 计划新增/编辑对话框。existing 非空为编辑模式。 */
@Composable
fun PlanEditDialog(
    existing: PlanTask?,
    prefillDate: String?,
    prefillStartTime: String?,
    prefillEndTime: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    todayClasses: List<TodayClass> = emptyList(),
) {
  var title by remember { mutableStateOf(existing?.title ?: "") }
  var date by remember { mutableStateOf(existing?.date ?: prefillDate ?: "") }
  var startTime by remember { mutableStateOf(existing?.startTime ?: prefillStartTime ?: "") }
  var endTime by remember { mutableStateOf(existing?.endTime ?: prefillEndTime ?: "") }
  var note by remember { mutableStateOf(existing?.note ?: "") }
  var reminderAt by remember { mutableStateOf(existing?.reminderAt ?: "") }
  var color by remember { mutableStateOf(existing?.color ?: PLAN_COLORS.first()) }
  var showStartPicker by remember { mutableStateOf(false) }
  var showEndPicker by remember { mutableStateOf(false) }
  var showReminderPicker by remember { mutableStateOf(false) }
  var reminderManuallySet by remember { mutableStateOf(existing?.reminderAt != null) }

  fun save() {
    if (title.isBlank() || date.isBlank()) return
    PlanStore.upsert(
        PlanTask(
            id = existing?.id ?: Clock.System.now().toEpochMilliseconds().toString(),
            title = title.trim(),
            date = date.trim(),
            startTime = startTime.trim().takeIf { it.isNotBlank() },
            endTime = endTime.trim().takeIf { it.isNotBlank() },
            note = note.trim(),
            reminderAt = reminderAt.trim().takeIf { it.isNotBlank() },
            color = color,
        )
    )
    onSaved()
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
      Column(
          modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("日期 yyyy-MM-dd") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        TimePickerField(
            label = "开始时间",
            value = startTime,
            onClick = { showStartPicker = true },
            modifier = Modifier.fillMaxWidth(),
        )

        TimePickerField(
            label = "结束时间",
            value = endTime,
            onClick = { showEndPicker = true },
            modifier = Modifier.fillMaxWidth(),
        )

        if (todayClasses.isNotEmpty()) {
          val nextClassEnd =
              nextClassEndAfter(
                  startTime.trim().ifBlank {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
                  },
                  todayClasses,
              )
          TextButton(
              onClick = { nextClassEnd?.let { endTime = it } },
              enabled = nextClassEnd != null,
          ) {
            Text(nextClassEnd?.let { "下一节课下课：$it" } ?: "今天没有后续课程")
          }
        }

        TimePickerField(
            label = "提醒时间（可选，留空则不提醒）",
            value = reminderAt,
            onClick = { showReminderPicker = true },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("颜色", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PLAN_COLORS.forEach { c ->
            val selected = color == c
            Box(
                modifier =
                    Modifier.size(28.dp)
                        .clip(CircleShape)
                        .background(Color(c.toInt()))
                        .then(
                            if (selected)
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                )
                            else Modifier
                        )
                        .clickable { color = c },
            )
          }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.End),
        ) {
          TextButton(onClick = onDismiss) { Text("取消") }
          Button(onClick = { save() }) { Text("保存") }
        }
      }
    }

    if (showStartPicker) {
      TimeWheelPickerDialog(
          title = "开始时间",
          value = startTime,
          onDismiss = { showStartPicker = false },
          onConfirm = {
            startTime = it
            if (!reminderManuallySet && it.isNotBlank()) {
              reminderAt = minusMinutes(it, 15)
            }
            showStartPicker = false
          },
      )
    }
    if (showEndPicker) {
      TimeWheelPickerDialog(
          title = "结束时间",
          value = endTime,
          onDismiss = { showEndPicker = false },
          onConfirm = {
            endTime = it
            showEndPicker = false
          },
      )
    }
    if (showReminderPicker) {
      TimeWheelPickerDialog(
          title = "提醒时间",
          value = reminderAt,
          onDismiss = { showReminderPicker = false },
          onConfirm = {
            reminderAt = it
            reminderManuallySet = true
            showReminderPicker = false
          },
      )
    }
  }
}
