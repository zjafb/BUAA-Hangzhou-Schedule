package cn.edu.buaa.hzcampus.ui.screens.menu

import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentSummaryDto
import cn.edu.buaa.hzcampus.model.dto.JudgeSubmissionStatus
import cn.edu.buaa.hzcampus.model.dto.Week
import cn.edu.buaa.hzcampus.model.dto.YgdkOverviewResponse
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

internal enum class HomeTodoSource(val label: String) {
  JUDGE("希冀"),
  YGDK("阳光打卡"),
}

internal sealed interface HomeTodoAction {
  data class OpenJudgeAssignment(val courseId: String, val assignmentId: String) : HomeTodoAction

  data object OpenYgdkHome : HomeTodoAction
}

internal data class HomeTodoItem(
    val id: String,
    val source: HomeTodoSource,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val timeLabel: String,
    val sortTime: LocalDateTime?,
    val action: HomeTodoAction,
) {
  val actionLabel: String?
    get() = null
}

internal fun buildHomeTodoItems(
    judgeAssignments: List<JudgeAssignmentSummaryDto>,
    ygdkOverview: YgdkOverviewResponse? = null,
    currentWeek: Week? = null,
    ygdkReminderEnabled: Boolean = true,
    ygdkWeekDone: Boolean = false,
    ygdkTermDone: Boolean = false,
    now: LocalDateTime,
): List<HomeTodoItem> {
  return buildList {
        addAll(buildJudgeTodoItems(judgeAssignments, now))
        buildYgdkTodoItem(
                overview = ygdkOverview,
                currentWeek = currentWeek,
                reminderEnabled = ygdkReminderEnabled,
                weekDone = ygdkWeekDone,
                termDone = ygdkTermDone,
                now = now,
            )
            ?.let(::add)
      }
      .sortedWith(compareBy<HomeTodoItem> { it.sortTime == null }.thenBy { it.sortTime })
}

internal fun parseHomeDateTime(value: String?, today: LocalDate? = null): LocalDateTime? {
  val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  if ('-' in normalized && (' ' in normalized || 'T' in normalized)) {
    return runCatching { LocalDateTime.parse(normalized.replace(" ", "T")) }.getOrNull()
  }

  val time = parseClockTime(normalized) ?: return null
  val targetDate = today ?: return null
  return LocalDateTime(
      year = targetDate.year,
      month = targetDate.month,
      day = targetDate.day,
      hour = time.hour,
      minute = time.minute,
      second = time.second,
      nanosecond = time.nanosecond,
  )
}

internal fun formatHomeDateTime(value: LocalDateTime?): String =
    value?.let {
      "${it.month.ordinal + 1}月${it.day}日 ${it.hour.toPaddedString()}:${it.minute.toPaddedString()}"
    } ?: "时间待定"

private fun buildJudgeTodoItems(
    assignments: List<JudgeAssignmentSummaryDto>,
    now: LocalDateTime,
): List<HomeTodoItem> =
    assignments.mapNotNull { assignment ->
      val dueTime = parseHomeDateTime(assignment.dueTime)
      val unfinished =
          assignment.submissionStatus == JudgeSubmissionStatus.UNSUBMITTED ||
              assignment.submissionStatus == JudgeSubmissionStatus.PARTIAL
      if (!unfinished || dueTime == null || dueTime <= now) {
        return@mapNotNull null
      }

      val subtitle = assignment.courseName.takeIf { it.isNotBlank() } ?: "希冀作业"

      HomeTodoItem(
          id = "judge:${assignment.courseId}:${assignment.assignmentId}",
          source = HomeTodoSource.JUDGE,
          title = assignment.title,
          subtitle = subtitle,
          statusLabel =
              if (assignment.submissionStatus == JudgeSubmissionStatus.PARTIAL) {
                "待完成"
              } else {
                "待提交"
              },
          timeLabel = "截止 ${formatHomeDateTime(dueTime)}",
          sortTime = dueTime,
          action = HomeTodoAction.OpenJudgeAssignment(assignment.courseId, assignment.assignmentId),
      )
    }

internal fun buildYgdkTodoItem(
    overview: YgdkOverviewResponse?,
    currentWeek: Week?,
    reminderEnabled: Boolean,
    weekDone: Boolean,
    termDone: Boolean,
    now: LocalDateTime,
): HomeTodoItem? {
  if (!reminderEnabled || weekDone || termDone) return null
  val week = currentWeek ?: return null
  val weekNumber = week.serialNumber
  if (weekNumber !in 11..14) return null
  val summary = overview?.summary ?: return null
  val weekCount = summary.weekCount ?: return null
  val termCount = summary.termCount
  if (weekCount >= 4 || termCount >= 16) return null

  val dueTime = now.currentWeekSundayEnd()
  return HomeTodoItem(
      id = "ygdk:${week.term}:$weekNumber",
      source = HomeTodoSource.YGDK,
      title = "本周阳光打卡未达标",
      subtitle = "本周已打卡 $weekCount / 4 次",
      statusLabel = "待打卡",
      timeLabel = "截止 ${formatHomeDateTime(dueTime)}",
      sortTime = dueTime,
      action = HomeTodoAction.OpenYgdkHome,
  )
}

private fun parseClockTime(value: String): LocalTime? {
  val parts = value.split(":")
  if (parts.size !in 2..3) return null
  val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
  val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
  val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
  return runCatching { LocalTime(hour, minute, second) }.getOrNull()
}

private fun LocalDateTime.currentWeekSundayEnd(): LocalDateTime =
    LocalDateTime(
        date = date.plus(DatePeriod(days = date.daysUntilSunday())),
        time = LocalTime(hour = 23, minute = 59, second = 59),
    )

private fun LocalDate.daysUntilSunday(): Int =
    when (dayOfWeek) {
      DayOfWeek.MONDAY -> 6
      DayOfWeek.TUESDAY -> 5
      DayOfWeek.WEDNESDAY -> 4
      DayOfWeek.THURSDAY -> 3
      DayOfWeek.FRIDAY -> 2
      DayOfWeek.SATURDAY -> 1
      DayOfWeek.SUNDAY -> 0
    }

private fun Int.toPaddedString(): String = toString().padStart(2, '0')
