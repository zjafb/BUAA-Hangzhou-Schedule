package cn.edu.buaa.hzcampus.ui.common.util

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import biweekly.ICalendar
import biweekly.ICalVersion
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.io.TimezoneAssignment
import biweekly.io.TimezoneInfo
import biweekly.parameter.Related
import biweekly.property.CalendarScale
import biweekly.property.Trigger
import biweekly.util.Duration
import cn.edu.buaa.hzcampus.model.dto.CourseClass
import cn.edu.buaa.hzcampus.model.dto.SectionTime
import cn.edu.buaa.hzcampus.model.dto.Week
import cn.edu.buaa.hzcampus.model.dto.WeeklySchedule
import cn.edu.buaa.hzcampus.model.dto.extractTeachers
import cn.edu.buaa.hzcampus.model.dto.scheduleSectionTimes
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** 导出文件存放的子目录（cacheDir 下），必须与 res/xml/file_paths.xml 的 cache-path 一致。 */
private const val EXPORT_DIR_NAME = "schedule-calendar"

/** 生成文件名的固定前缀：北航杭州课表-<学期>-<日期>.ics */
private const val EXPORT_FILE_PREFIX = "北航杭州课表"

/** 课前提醒提前量（分钟）。 */
private const val ALARM_MINUTES = 15

/** 上游没有结束时间时的兜底时长（分钟），保证 VEVENT 始终有合法的 DTEND。 */
private const val DEFAULT_DURATION_MINUTES = 45

private const val CALENDAR_TIMEZONE_ID = "Asia/Shanghai"

private val SHANGHAI: TimeZone = TimeZone.getTimeZone(CALENDAR_TIMEZONE_ID)

/** "HH:mm" 片段，容忍上游返回 "08:00:00" 或带空格的形式。 */
private val hourMinute = Regex("""(\d{1,2}):(\d{2})""")

/** 一条待写入日历的日程：某个周次里某门课的一次实际上课。 */
private data class CalendarEvent(
    val courseName: String,
    val place: String?,
    val description: String,
    val uid: String,
    val start: Date,
    val end: Date,
)

/**
 * Android 实现：用 biweekly 生成 ICS，写到 cacheDir，再通过 FileProvider + ACTION_SEND 分享。
 *
 * 分享面板不可用时退化为写入系统「下载」目录，并返回可读的中文说明；任何失败都会带上原因，不静默。
 */
actual fun exportScheduleToCalendar(
    termName: String,
    termCode: String,
    weeks: List<Week>,
    schedules: Map<Int, WeeklySchedule>,
): ScheduleCalendarExport {
  val context =
      AppContextHolder.context
          ?: return ScheduleCalendarExport(false, "导出失败：应用上下文未就绪，请重新打开应用后再试")
  if (weeks.isEmpty() || schedules.isEmpty()) {
    return ScheduleCalendarExport(false, "请先本地化课表（课表页点击“课表本地化”）后再导出到系统日历")
  }
  return try {
    val events = collectEvents(weeks, schedules, termCode)
    if (events.isEmpty()) {
      ScheduleCalendarExport(false, "没有可导出的课程：当前学期课表为空，或课程缺少上课时间")
    } else {
      val fileName = exportFileName(termName, termCode)
      val file = writeIcsFile(context, fileName, buildIcs(termName, events))
      val fallbackNote = shareOrSave(context, file)
      ScheduleCalendarExport(
          success = true,
          message =
              fallbackNote
                  ?: "已生成课表日历文件（${events.size} 个日程），请在分享面板中选择「日历」导入，或分享到微信/网盘",
          fileName = fileName,
          eventCount = events.size,
      )
    }
  } catch (e: Exception) {
    ScheduleCalendarExport(false, "导出失败：${e.message ?: e::class.simpleName ?: "未知错误"}")
  }
}

/**
 * 逐周生成日程：日期 = 该周周一 + (星期 - 1) 天，时间取课程自带的开始/结束时间，缺失时按节次作息表补全。
 * 周次可能不连续，逐周生成比 RRULE 更可靠；完全相同的一次课只保留一条。
 */
private fun collectEvents(
    weeks: List<Week>,
    schedules: Map<Int, WeeklySchedule>,
    termCode: String,
): List<CalendarEvent> {
  val sectionTimes = scheduleSectionTimes(schedules.values)
  val dateTime =
      SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = SHANGHAI
        isLenient = false
      }
  val seen = HashSet<String>()
  val usedUids = HashSet<String>()
  val events = mutableListOf<CalendarEvent>()

  for (week in weeks.sortedBy { it.serialNumber }) {
    val schedule = schedules[week.serialNumber] ?: continue
    val monday = runCatching { LocalDate.parse(week.startDate) }.getOrNull() ?: continue
    for (course in schedule.arrangedList) {
      val dayOfWeek = course.dayOfWeek ?: continue
      if (dayOfWeek !in 1..7) continue
      val times = resolveTimes(course, sectionTimes) ?: continue
      val date = monday.plus(DatePeriod(days = dayOfWeek - 1)).toString()
      val start = runCatching { dateTime.parse("$date ${times.first}") }.getOrNull() ?: continue
      val end =
          runCatching { dateTime.parse("$date ${times.second}") }.getOrNull()
              ?: Date(start.time + DEFAULT_DURATION_MINUTES * 60_000L)
      val key = "$date|${times.first}|${times.second}|${course.courseName}|${course.placeName}"
      if (!seen.add(key)) continue
      events +=
          CalendarEvent(
              courseName = course.courseName,
              place = course.placeName?.takeIf { it.isNotBlank() },
              description = describe(course, times, week.serialNumber),
              uid = uniqueUid(termCode = termCode, key = key, used = usedUids),
              start = start,
              end = if (end.after(start)) end else Date(start.time + DEFAULT_DURATION_MINUTES * 60_000L),
          )
    }
  }
  return events.sortedWith(compareBy<CalendarEvent>({ it.start.time }, { it.courseName }))
}

/** 开始/结束时间：优先课程自带时间，其次按节次作息表；都取不到时该课无法生成日程。 */
private fun resolveTimes(course: CourseClass, sectionTimes: List<SectionTime>): Pair<String, String>? {
  val beginSection = course.beginSection?.let { section -> sectionTimes.firstOrNull { it.section == section } }
  val endSection = course.endSection?.let { section -> sectionTimes.firstOrNull { it.section == section } }
  val begin = normalizeHourMinute(course.beginTime) ?: normalizeHourMinute(beginSection?.start) ?: return null
  val end = normalizeHourMinute(course.endTime) ?: normalizeHourMinute(endSection?.end)
  return begin to (end ?: shiftMinutes(begin, DEFAULT_DURATION_MINUTES))
}

/** 统一为 "HH:mm"；无法解析时返回 null（不猜测、不静默填错时间）。 */
private fun normalizeHourMinute(raw: String?): String? {
  val match = hourMinute.find(raw?.trim().orEmpty()) ?: return null
  val hour = match.groupValues[1].toIntOrNull() ?: return null
  val minute = match.groupValues[2].toIntOrNull() ?: return null
  if (hour !in 0..23 || minute !in 0..59) return null
  return String.format(Locale.US, "%02d:%02d", hour, minute)
}

/** "HH:mm" 偏移若干分钟。 */
private fun shiftMinutes(time: String, minutes: Int): String {
  val parts = time.split(":")
  val total = ((parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0) + minutes).coerceIn(0, 24 * 60 - 1)
  return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

/** 日程详情：教师（从 weeksAndTeachers 提取）+ 节次/时间 + 周次。 */
private fun describe(course: CourseClass, times: Pair<String, String>, weekNumber: Int): String {
  val lines = mutableListOf<String>()
  val teacher = extractTeachers(course.weeksAndTeachers)?.takeIf { it.isNotBlank() }
  if (teacher != null) lines += "教师：$teacher"
  val sections = sectionLabel(course.beginSection, course.endSection)
  lines += listOfNotNull(sections, "${times.first}-${times.second}").joinToString(" ")
  lines += "第${weekNumber}周"
  course.teachingTarget?.takeIf { it.isNotBlank() }?.let { lines += "教学对象：$it" }
  return lines.joinToString("\n")
}

private fun sectionLabel(beginSection: Int?, endSection: Int?): String? {
  val begin = beginSection ?: return null
  val end = endSection ?: begin
  return if (end > begin) "第${begin}-${end}节" else "第${begin}节"
}

/** UID 只用 ASCII，保证跨客户端可解析，同时稳定且唯一。 */
private fun uniqueUid(termCode: String, key: String, used: MutableSet<String>): String {
  val base = "${termCode.ifBlank { "term" }}-${String.format(Locale.US, "%08x", key.hashCode())}"
  var candidate = "$base@hzcampus.buaa.edu.cn"
  var suffix = 2
  while (!used.add(candidate)) {
    candidate = "$base-${suffix++}@hzcampus.buaa.edu.cn"
  }
  return candidate
}

/** 用 biweekly 组装 VCALENDAR：必需字段齐全，文本由库负责 ICS 转义与折行。 */
private fun buildIcs(termName: String, events: List<CalendarEvent>): String {
  val calendarName = "$EXPORT_FILE_PREFIX ${termName.ifBlank { "学期" }}"
  val calendar =
      ICalendar().apply {
        setVersion(ICalVersion.V2_0)
        setCalendarScale(CalendarScale.gregorian())
        setProductId("-//BUAA Hangzhou//HzCampus Schedule Export//CN")
        addName(calendarName)
        addExperimentalProperty("X-WR-CALNAME", calendarName)
        addExperimentalProperty("X-WR-TIMEZONE", CALENDAR_TIMEZONE_ID)
        val timezoneInfo = TimezoneInfo()
        // 第二个参数传 null 时 biweekly 用 TimeZone 的 ID 作为 TZID（Asia/Shanghai）。
        timezoneInfo.setDefaultTimezone(TimezoneAssignment(SHANGHAI, null as String?))
        setTimezoneInfo(timezoneInfo)
      }
  val stamp = Date()
  for (item in events) {
    val event =
        VEvent().apply {
          setUid(item.uid)
          setDateTimeStamp(stamp)
          setDateStart(item.start, true)
          setDateEnd(item.end, true)
          setSummary(item.courseName)
          item.place?.let { setLocation(it) }
          setDescription(item.description)
          addAlarm(
              VAlarm.display(
                  Trigger(Duration.builder().minutes(ALARM_MINUTES).build(), Related.START),
                  null,
              )
          )
        }
    calendar.addEvent(event)
  }
  return withShanghaiVTimezone(calendar.write())
}

/** Asia/Shanghai 自 1991 年起没有夏令时，补一个固定 +0800 的 VTIMEZONE，让 TZID 自带定义。 */
private val SHANGHAI_VTIMEZONE =
    listOf(
            "BEGIN:VTIMEZONE",
            "TZID:$CALENDAR_TIMEZONE_ID",
            "BEGIN:STANDARD",
            "DTSTART:19700101T000000",
            "TZOFFSETFROM:+0800",
            "TZOFFSETTO:+0800",
            "TZNAME:CST",
            "END:STANDARD",
            "END:VTIMEZONE",
        )
        .joinToString("\r\n", postfix = "\r\n")

private fun withShanghaiVTimezone(ics: String): String {
  if (ics.contains("BEGIN:VTIMEZONE")) return ics
  val marker = ics.indexOf("BEGIN:VEVENT")
  if (marker < 0) return ics
  return ics.substring(0, marker) + SHANGHAI_VTIMEZONE + ics.substring(marker)
}

/** 文件名：北航杭州课表-<学期>-<日期>.ics。 */
private fun exportFileName(termName: String, termCode: String): String {
  val term =
      (termName.ifBlank { termCode })
          .replace(Regex("""[\\/:*?"<>|\s]+"""), "-")
          .trim('-')
          .ifEmpty { "学期" }
  val date =
      SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = SHANGHAI }.format(Date())
  return "$EXPORT_FILE_PREFIX-$term-$date.ics"
}

private fun writeIcsFile(context: Context, fileName: String, ics: String): File {
  val directory = File(context.cacheDir, EXPORT_DIR_NAME)
  if (!directory.exists() && !directory.mkdirs()) error("无法创建导出目录")
  val file = File(directory, fileName)
  OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { it.write(ics) }
  check(file.length() > 0) { "导出文件为空" }
  return file
}

/**
 * 唤起系统分享面板（type=text/calendar，用户可选「日历」导入或分享到微信/网盘）。
 * 返回 null 表示已唤起；否则返回替代方案的说明（保存到下载目录）。
 */
private fun shareOrSave(context: Context, file: File): String? {
  val authority = "${context.packageName}.fileprovider"
  val uri = FileProvider.getUriForFile(context, authority, file)
  val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/calendar"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        putExtra(Intent.EXTRA_TEXT, "北航杭州课表日历文件，可用系统「日历」导入")
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
  val chooser =
      Intent.createChooser(send, "导入或分享课表日历文件").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
  if (runCatching { context.startActivity(chooser) }.isSuccess) return null
  return saveToDownloads(context, file)
}

/** 分享面板不可用时的退路：把 ICS 复制到系统「下载」目录；失败也要给出可读提示。 */
private fun saveToDownloads(context: Context, file: File): String =
    runCatching {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                  put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                  put(MediaStore.MediaColumns.MIME_TYPE, "text/calendar")
                  put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            val uri =
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法在下载目录创建文件")
            context.contentResolver.openOutputStream(uri)?.use { output ->
              file.inputStream().use { it.copyTo(output) }
            } ?: error("无法写入下载目录")
            "手机上没有可用的分享面板，已把日历文件保存到「下载」目录：${file.name}"
          } else {
            val directory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val target = File(directory, file.name)
            file.copyTo(target, overwrite = true)
            "手机上没有可用的分享面板，已把日历文件保存到「下载」目录：${target.name}"
          }
        }
        .getOrElse { "手机上没有可用的分享面板，日历文件已生成在应用缓存目录：${file.absolutePath}" }
