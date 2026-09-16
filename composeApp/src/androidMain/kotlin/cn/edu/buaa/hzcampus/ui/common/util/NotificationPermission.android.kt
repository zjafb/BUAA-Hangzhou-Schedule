package cn.edu.buaa.hzcampus.ui.common.util

import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

actual fun areNotificationsEnabled(): Boolean {
  val context = AppContextHolder.context ?: return false
  return runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
      .getOrDefault(false)
}

actual fun openNotificationSettings() {
  val context = AppContextHolder.context ?: return
  val intent =
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(intent) }
}
