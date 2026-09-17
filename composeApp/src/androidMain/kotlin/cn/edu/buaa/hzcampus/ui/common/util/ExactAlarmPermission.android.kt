package cn.edu.buaa.hzcampus.ui.common.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** API 31 起精确闹钟必须显式授权；低版本没有这个开关，视为始终可用。 */
private fun canScheduleExactAlarms(context: Context): Boolean {
  if (Build.VERSION.SDK_INT < 31) return true
  val alarmManager =
      context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
  return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
}

actual fun canScheduleExactAlarms(): Boolean {
  val context = AppContextHolder.context ?: return true
  return canScheduleExactAlarms(context)
}

actual fun openExactAlarmSettings() {
  val context = AppContextHolder.context ?: return
  if (Build.VERSION.SDK_INT < 31) return
  val intent =
      Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
          .setData(Uri.fromParts("package", context.packageName, null))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(intent) }
}
