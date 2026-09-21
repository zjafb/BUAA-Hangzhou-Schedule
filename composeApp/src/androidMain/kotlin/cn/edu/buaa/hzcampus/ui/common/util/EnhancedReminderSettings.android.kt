package cn.edu.buaa.hzcampus.ui.common.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.edu.buaa.hzcampus.api.storage.ReminderStore

@Composable
actual fun EnhancedReminderSettings() {
  val context = LocalContext.current
  var fullScreen by remember { mutableStateOf(ReminderStore.fullScreenEnabled()) }
  var allowed by remember { mutableStateOf(canUseClassFullScreen(context)) }
  var issues by remember { mutableStateOf(reminderNotificationIssues(context)) }
  var result by remember { mutableStateOf<String?>(null) }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        allowed = canUseClassFullScreen(context)
        issues = reminderNotificationIssues(context)
      }
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("课程与今日计划提醒", style = MaterialTheme.typography.titleMedium)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("息屏 / 锁屏全屏提醒", Modifier.weight(1f))
        Switch(
            checked = fullScreen,
            onCheckedChange = {
              fullScreen = it
              ReminderStore.setFullScreenEnabled(it)
            },
        )
      }
      Text(
          "开启后，息屏或锁屏时请求亮屏显示全屏提醒；正在使用手机时仅显示横幅。关闭后使用普通横幅通知。两种提醒都适用于课程与今日计划。",
          style = MaterialTheme.typography.bodySmall,
      )
      if (fullScreen) {
        Text(
            if (allowed) "系统全屏通知权限：已允许" else "系统全屏通知权限：未允许，将使用普通通知",
            style = MaterialTheme.typography.bodySmall,
            color =
                if (allowed) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
        )
      }
      if (fullScreen && !allowed && Build.VERSION.SDK_INT >= 34) {
        TextButton(
            onClick = {
              runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                  }
                  .onFailure { result = "无法打开授权页，请在系统特殊应用权限中查找全屏通知。" }
            }
        ) {
          Text("允许全屏提醒")
        }
      }
      issues.forEach {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
      }
      Text(
          "横幅还需在系统中开启：应用通知的横幅，以及“课前提醒”“计划提醒”分类的横幅 / 弹出通知。勿扰模式和系统通知限制可能影响展示。",
          style = MaterialTheme.typography.bodySmall,
      )
      TextButton(onClick = { openNotificationSettings() }) { Text("应用通知与横幅设置") }
      if (Build.VERSION.SDK_INT >= 26) {
        TextButton(onClick = { openReminderChannel(context, CLASS_REMINDER_CHANNEL) }) {
          Text("课前提醒分类设置")
        }
        TextButton(onClick = { openReminderChannel(context, PLAN_REMINDER_CHANNEL) }) {
          Text("计划提醒分类设置")
        }
      }
      TextButton(onClick = { result = scheduleReminderTest(context, false) }) {
        Text("10 秒后测试课程提醒")
      }
      TextButton(onClick = { result = scheduleReminderTest(context, true) }) { Text("10 秒后测试今日计划") }
      result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      Text(
          "请允许本应用后台运行。即使未打开应用，也可由系统闹钟触发；强行停止应用后需重新打开，重启后需先解锁。",
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

private fun reminderNotificationIssues(context: Context): List<String> {
  createReminderChannels(context)
  val compat = NotificationManagerCompat.from(context)
  val manager = context.getSystemService(NotificationManager::class.java)
  return buildList {
    if (!compat.areNotificationsEnabled()) {
      add("应用通知已关闭，所有提醒都无法显示。")
    } else {
      val importance = compat.importance
      if (
          importance != NotificationManager.IMPORTANCE_UNSPECIFIED &&
              importance < NotificationManager.IMPORTANCE_DEFAULT
      ) {
        add("应用整体通知已设为低重要性，请检查应用通知中的静默和横幅设置。")
      }
      if (Build.VERSION.SDK_INT >= 26) {
        listOf(CLASS_REMINDER_CHANNEL, PLAN_REMINDER_CHANNEL).forEach { id ->
          manager.getNotificationChannel(id)?.let { channel ->
            if (channel.importance < NotificationManager.IMPORTANCE_HIGH) {
              add("“${channel.name}”分类未设为高重要性，请开启该分类的横幅 / 弹出通知。")
            }
          }
        }
      }
    }
    if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
      add("系统正在限制打扰，横幅、声音或全屏可能被勿扰模式拦截。")
    }
  }
}

private fun openReminderChannel(context: Context, channel: String) {
  runCatching {
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        )
      }
      .onFailure { openNotificationSettings() }
}

private fun scheduleReminderTest(context: Context, plan: Boolean): String {
  if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "请先允许应用通知，再进行测试。"
  return runCatching {
        val now = System.currentTimeMillis()
        val id = if (plan) 4901 else 2901
        val receiver =
            if (plan) PlanReminderReceiver::class.java else ClassReminderReceiver::class.java
        val testIntent =
            Intent(context, receiver).apply {
              putExtra("title", if (plan) "今日计划测试" else "课程提醒测试")
              putExtra("note", "这是一条测试提醒")
              putExtra("place", if (plan) "" else "测试通知，无需前往教室")
              putExtra("advance", 1)
              putExtra("startMillis", now + 70_000L)
              putExtra("remindAt", now + 10_000L)
              putExtra("notificationId", id)
            }
        val pending =
            PendingIntent.getBroadcast(
                context,
                id,
                testIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        scheduleClassReminderAlarm(
            context.getSystemService(AlarmManager::class.java),
            now + 10_000L,
            pending,
        )
        "测试已安排：请返回桌面验证横幅，或锁屏验证全屏；约 10 秒后提醒，未允许精确闹钟时可能延迟。"
      }
      .getOrElse { "无法安排测试，请检查系统提醒权限。" }
}
