package cn.edu.buaa.hzcampus.ui.screens.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.api.storage.MailAccountsStore
import cn.edu.buaa.hzcampus.api.storage.ReminderStore
import cn.edu.buaa.hzcampus.ui.common.util.EnhancedReminderSettings
import cn.edu.buaa.hzcampus.ui.common.util.areNotificationsEnabled
import cn.edu.buaa.hzcampus.ui.common.util.canScheduleExactAlarms
import cn.edu.buaa.hzcampus.ui.common.util.openExactAlarmSettings
import cn.edu.buaa.hzcampus.ui.common.util.openNotificationSettings

@Composable
fun SettingsScreen(
    currentMode: ConnectionMode,
    availableModes: List<ConnectionMode>,
    onModeSelected: (ConnectionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
  var pendingMode by remember { mutableStateOf<ConnectionMode?>(null) }
  var advanceMinutes by remember { mutableStateOf(ReminderStore.getAdvanceMinutes()) }
  val presets = listOf(10, 15, 20, 30)
  var customEditing by remember { mutableStateOf(advanceMinutes !in presets) }
  var customText by remember {
    mutableStateOf(if (advanceMinutes !in presets) advanceMinutes.toString() else "")
  }

  // 精确闹钟授权状态：从系统「闹钟和提醒」页返回时（前台恢复）重新读取一次。
  var exactAlarmAllowed by remember { mutableStateOf(canScheduleExactAlarms()) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        exactAlarmAllowed = canScheduleExactAlarms()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(
        text = "课前提醒提前（分钟）",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      presets.forEach { minutes ->
        FilterChip(
            selected = !customEditing && advanceMinutes == minutes,
            onClick = {
              customEditing = false
              advanceMinutes = minutes
              ReminderStore.setAdvanceMinutes(minutes)
            },
            label = { Text("$minutes") },
        )
      }
      FilterChip(
          selected = customEditing,
          onClick = { customEditing = true },
          label = { Text("自定义") },
      )
    }
    if (customEditing) {
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
          value = customText,
          onValueChange = { text ->
            val digits = text.filter { it.isDigit() }
            customText = digits
            digits
                .toIntOrNull()
                ?.takeIf { it in 1..240 }
                ?.let {
                  advanceMinutes = it
                  ReminderStore.setAdvanceMinutes(it)
                }
          },
          label = { Text("自定义提前分钟数") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
      )
    }

    Spacer(Modifier.height(16.dp))
    MailPageSizeSetting()
    Spacer(Modifier.height(16.dp))
    EnhancedReminderSettings()
    Spacer(Modifier.height(16.dp))
    if (!areNotificationsEnabled()) {
      Card(
          modifier = Modifier.fillMaxWidth().clickable { openNotificationSettings() },
          colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "通知权限未开启",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "课程提醒和计划提醒需要系统通知权限，点击前往开启",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }
    // Android 12+ 未授权精确闹钟时，课程提醒会被系统推迟（Doze/待机下可能几十分钟），
    // 这里给出提示与一键跳转；已授权或系统版本低于 31 时不显示。
    if (!exactAlarmAllowed) {
      Card(
          modifier = Modifier.fillMaxWidth(),
          colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "精确提醒未开启",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "课程提醒需要「闹钟和提醒」权限才能准时送达，否则可能延迟。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = { openExactAlarmSettings() }) { Text("去授权") }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "连接模式",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "当前模式：${currentMode.displayName}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))

    availableModes.forEach { mode ->
      Card(
          modifier =
              Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                if (mode != currentMode) {
                  pendingMode = mode
                }
              },
          colors =
              CardDefaults.cardColors(
                  containerColor =
                      if (mode == currentMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                      } else {
                        MaterialTheme.colorScheme.surface
                      }
              ),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == currentMode,
                onClick = null,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
              Text(text = mode.displayName, style = MaterialTheme.typography.titleMedium)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                  text = mode.description,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }

  pendingMode?.let { mode ->
    AlertDialog(
        onDismissRequest = { pendingMode = null },
        title = { Text("切换连接模式") },
        text = { Text("将退出当前登录并清除本模式会话，需重新登录。") },
        confirmButton = {
          TextButton(
              onClick = {
                pendingMode = null
                onModeSelected(mode)
              }
          ) {
            Text("确认切换")
          }
        },
        dismissButton = { TextButton(onClick = { pendingMode = null }) { Text("取消") } },
    )
  }
}

@Composable
private fun MailPageSizeSetting() {
  var size by remember { mutableStateOf(MailAccountsStore.pageSize()) }
  val presets = listOf(10, 15, 20, 30)
  var custom by remember { mutableStateOf(size !in presets) }
  var input by remember { mutableStateOf(size.toString()) }
  Text("邮件默认显示数量", style = MaterialTheme.typography.titleMedium)
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    presets.forEach { count ->
      FilterChip(
          selected = !custom && size == count,
          onClick = {
            size = count
            custom = false
            MailAccountsStore.setPageSize(count)
          },
          label = { Text("$count") },
      )
    }
    FilterChip(selected = custom, onClick = { custom = true }, label = { Text("自定义") })
  }
  if (custom) {
    val number = input.toIntOrNull()
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("邮件数量（1–500）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = number == null || number !in 1..500,
    )
    TextButton(
        enabled = number != null && number in 1..500,
        onClick = {
          number?.let {
            size = it
            MailAccountsStore.setPageSize(it)
          }
        },
    ) {
      Text(if (size == number) "已保存" else "保存")
    }
  }
  Text("每次加载 $size 封，收件箱底部可继续加载历史邮件。", style = MaterialTheme.typography.bodySmall)
}
