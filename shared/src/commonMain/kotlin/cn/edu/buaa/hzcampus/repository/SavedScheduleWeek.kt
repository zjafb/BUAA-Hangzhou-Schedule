package cn.edu.buaa.hzcampus.repository

import cn.edu.buaa.hzcampus.model.dto.CourseClass
import cn.edu.buaa.hzcampus.model.dto.TodayClass
import cn.edu.buaa.hzcampus.model.dto.Week
import cn.edu.buaa.hzcampus.model.dto.WeeklySchedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** 桌面小组件和点击入口共用已保存的学期、周次，不发起网络请求。 */
data class SavedScheduleWeek(val semester: SemesterSchedule, val week: Week)

fun selectSavedScheduleWeek(
    semesters: List<SemesterSchedule>,
    today: LocalDate,
    termCode: String? = null,
    weekNumber: Int? = null,
): SavedScheduleWeek? {
  val date = today.toString()
  val available = semesters.filter { it.weeks.isNotEmpty() }
  val semester =
      available.firstOrNull { it.termCode == termCode }
          ?: available
              .filter { it.weeks.any { week -> date >= week.startDate && date <= week.endDate } }
              .maxByOrNull { it.weeks.minOf { week -> week.startDate } }
          ?: available.maxByOrNull { it.weeks.minOf { week -> week.startDate } }
          ?: return null
  val weeks = semester.weeks.sortedBy { it.serialNumber }
  val week =
      weeks.firstOrNull { it.serialNumber == weekNumber && semester.termCode == termCode }
          ?: weeks.firstOrNull { date >= it.startDate && date <= it.endDate }
          ?: if (date < weeks.first().startDate) weeks.first() else weeks.last()
  return SavedScheduleWeek(semester, week)
}

data class SavedAgendaItem(val date: LocalDate, val course: CourseClass)

/**
 * 带日期的课程（`yyyy-MM-dd` + 课程），用于「未来若干天」的课前提醒排程。
 *
 * 与 [TodayClass] 的区别只在于多了一个确定的日期：提醒需要知道这节课是哪一天的同一时刻。
 */
data class DatedClass(val date: String, val course: TodayClass)

/**
 * 已保存课表中覆盖 [today] 的周课表；[today] 不在任何周次范围内（含只有旧学期缓存的过期数据）时返回 null。
 *
 * [selectSavedScheduleWeek] 会把范围外日期收敛到最近的周次，小组件需要这种行为，首页定位今天的课不需要。
 */
fun weeklyScheduleCovering(
    semesters: List<SemesterSchedule>,
    today: LocalDate,
): WeeklySchedule? {
  val saved = selectSavedScheduleWeek(semesters, today) ?: return null
  if (today.toString() !in saved.week.startDate..saved.week.endDate) return null
  return saved.semester.schedules[saved.week.serialNumber]
}

/** 首页点击今日课程时用已本地化课表兜底：只读当前账号的本地快照，不联网；没有本地数据或缓存损坏时为 null。 */
fun savedWeeklyScheduleFor(today: LocalDate): WeeklySchedule? =
    runCatching {
          val account = ScheduleStore.account() ?: return@runCatching null
          weeklyScheduleCovering(ScheduleStore.read(account), today)
        }
        .getOrNull()

fun savedAgenda(
    semesters: List<SemesterSchedule>,
    today: LocalDate,
    days: Int,
): List<SavedAgendaItem> =
    (0 until days).flatMap { offset ->
      val date = today.plus(offset, DateTimeUnit.DAY)
      val selected = selectSavedScheduleWeek(semesters, date) ?: return@flatMap emptyList()
      if (date.toString() !in selected.week.startDate..selected.week.endDate)
          return@flatMap emptyList()
      selected.semester.schedules[selected.week.serialNumber]
          ?.arrangedList
          .orEmpty()
          .filter { it.dayOfWeek == date.dayOfWeek.ordinal + 1 }
          .sortedBy { it.beginTime }
          .map { SavedAgendaItem(date, it) }
    }
