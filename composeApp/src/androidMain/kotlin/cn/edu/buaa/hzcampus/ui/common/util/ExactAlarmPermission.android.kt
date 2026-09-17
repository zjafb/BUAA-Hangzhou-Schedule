package cn.edu.buaa.hzcampus.ui.common.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 本应用在 Manifest 里声明的「核心功能即提醒」型精确闹钟权限（API 33+，安装即授予）。 */
private const val USE_EXACT_ALARM = "android.permission.USE_EXACT_ALARM"

/**
 * 是否可以使用精确闹钟。
 * - API 31 以下没有这个开关，始终可用。
 * - API 33 起应用声明了 `USE_EXACT_ALARM`（本应用的课表/课前提醒属于该权限的适用场景）时安装即授予， 此时系统允许
 *   `setExactAndAllowWhileIdle`；但 `AlarmManager.canScheduleExactAlarms()` 只反映
 *   `SCHEDULE_EXACT_ALARM` 的 app-op，仍可能是 `default` 而返回 false。因此这里以权限为准， 否则会误判成「未授权」而退化到非精确闹钟（Doze
 *   下推迟几十分钟）。
 * - 其余情况回落到 `canScheduleExactAlarms()`，用户可在系统「闹钟和提醒」里手动授权。
 */
private fun canScheduleExactAlarms(context: Context): Boolean {
  if (Build.VERSION.SDK_INT < 31) return true
  if (hasUseExactAlarmPermission(context)) return true
  val alarmManager =
      context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
  return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
}

private fun hasUseExactAlarmPermission(context: Context): Boolean {
  if (Build.VERSION.SDK_INT < 33) return false
  return runCatching {
        context.checkSelfPermission(USE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED
      }
      .getOrDefault(false)
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
