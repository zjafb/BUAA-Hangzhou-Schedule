package cn.edu.buaa.hzcampus.ui.common.util

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.buaa.hzcampus.api.storage.ReminderStore

internal const val CLASS_REMINDER_CHANNEL = "class_reminder"
internal const val PLAN_REMINDER_CHANNEL = "plan_reminder"
internal const val DISMISS_REMINDER = "cn.edu.buaa.hzcampus.DISMISS_CLASS_REMINDER"

internal fun createReminderChannels(context: Context) {
  if (Build.VERSION.SDK_INT < 26) return
  val manager = context.getSystemService(NotificationManager::class.java)
  listOf(CLASS_REMINDER_CHANNEL to "课前提醒", PLAN_REMINDER_CHANNEL to "计划提醒").forEach { (id, title) ->
    // 保留用户原有渠道设置；不换渠道 ID 来绕过系统设置。
    manager.createNotificationChannel(
        NotificationChannel(id, title, NotificationManager.IMPORTANCE_HIGH).apply {
          description = "到时提醒；允许横幅后可在屏幕顶部显示"
          enableVibration(true)
        }
    )
  }
}

internal fun postReminder(
    context: Context,
    source: Intent,
    channel: String,
    heading: String,
    message: String,
    expiresAt: Long,
) {
  AppContextHolder.context = context.applicationContext
  createReminderChannels(context)
  val manager = NotificationManagerCompat.from(context)
  if (!manager.areNotificationsEnabled()) return
  val remaining = expiresAt - System.currentTimeMillis()
  if (remaining <= 0) return
  val id = source.getIntExtra("notificationId", 2901)
  val detail =
      PendingIntent.getActivity(
          context,
          id,
          Intent(context, ClassReminderActivity::class.java).apply {
            putExtras(source)
            putExtra("heading", heading)
            putExtra("message", message)
            putExtra("expiresAt", expiresAt)
            data = Uri.parse("hzcampus://reminder/$channel/$id")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
          },
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
  val dismiss =
      PendingIntent.getBroadcast(
          context,
          id,
          Intent(context, ClassReminderReceiver::class.java).apply {
            action = DISMISS_REMINDER
            putExtra("notificationId", id)
          },
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
  val builder =
      NotificationCompat.Builder(context, channel)
          .setSmallIcon(android.R.drawable.ic_dialog_info)
          .setContentTitle(heading)
          .setContentText(message)
          .setStyle(NotificationCompat.BigTextStyle().bigText(message))
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setDefaults(NotificationCompat.DEFAULT_ALL)
          .setCategory(NotificationCompat.CATEGORY_ALARM)
          .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
          .setContentIntent(detail)
          .addAction(0, "知道了", dismiss)
          .setTimeoutAfter(remaining)
          .setAutoCancel(true)
  if (
      shouldShowFullScreenReminder(
          ReminderStore.fullScreenEnabled(),
          canUseClassFullScreen(context),
          context.getSystemService(PowerManager::class.java).isInteractive,
          context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
      )
  ) {
    builder.setFullScreenIntent(detail, true)
  }
  try {
    manager.notify(id, builder.build())
  } catch (_: SecurityException) {
    // 权限可能在闹钟到达前被撤销。
  }
}
