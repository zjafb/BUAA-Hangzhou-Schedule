package cn.edu.buaa.hzcampus.ui.screens.menu

import cn.edu.buaa.hzcampus.model.dto.Exam
import cn.edu.buaa.hzcampus.model.dto.Week
import kotlinx.datetime.LocalDate

/**
 * 首页「考试倒计时」卡片要显示的内容。
 *
 * @property courseName 课程名。
 * @property date 考试日期（已解析成 [LocalDate]）。
 * @property startTime 开始时间（HH:mm），可能为空。
 * @property endTime 结束时间（HH:mm），可能为空。
 * @property place 考场地点，可能为空。
 * @property seatNo 座位号，可能为空。
 * @property daysUntil 距离考试还有几天；0 表示就是今天，1 表示明天。
 */
data class HomeUpcomingExam(
    val courseName: String,
    val date: LocalDate,
    val startTime: String? = null,
    val endTime: String? = null,
    val place: String? = null,
    val seatNo: String? = null,
    val daysUntil: Int = 0,
) {
  /** 倒计时文案：「就是今天」/「明天」/「还有 N 天」。 */
  val countdownLabel: String
    get() = examCountdownLabel(daysUntil)

  /** 日期文案，例如「11月3日」。 */
  val dateLabel: String
    get() = "${date.month.ordinal + 1}月${date.day}日"

  /** 时间区间文案，例如「09:00-11:00」；两端都缺失时为 null。 */
  val timeRangeLabel: String?
    get() =
        when {
          startTime != null && endTime != null -> "$startTime-$endTime"
          startTime != null -> startTime
          else -> null
        }

  /** 卡片第二行详情：「11月3日 09:00-11:00 · 考场：教三-201 · 座位 12」。缺失字段自动省略。 */
  val detailLabel: String
    get() =
        buildList {
              add(listOfNotNull(dateLabel, timeRangeLabel).joinToString(" "))
              place?.let { add("考场：$it") }
              seatNo?.let { add("座位 $it") }
            }
            .joinToString(" · ")
}

/** 倒计时文案。负数按「就是今天」处理，避免时钟跨天时出现「还有 -1 天」。 */
fun examCountdownLabel(daysUntil: Int): String =
    when {
      daysUntil <= 0 -> "就是今天"
      daysUntil == 1 -> "明天"
      else -> "还有 $daysUntil 天"
    }

/**
 * 从考试安排里挑出最近的一场考试，供首页倒计时卡片使用。
 *
 * 筛选和排序规则：
 * 1. [Exam.examDate] 必须能解析成 `yyyy-MM-dd`；上游偶尔会给日期带上时间后缀（如 "2026-01-05 09:00"），
 *    先截断到第一个空格再解析。日期缺失或格式非法的那条直接跳过（不会因此让整张卡片消失）。
 * 2. 只保留日期 **大于等于** [today] 的考试。今天已经开考、甚至今天已经考完的考试仍然算「今天的考试」，
 *    当天一直显示「就是今天」，第二天自动跳到下一场；这比按考试结束时刻过滤更容易理解，也和首页日期口径一致。
 * 3. 多场候选按「日期升序 + 当天开始时间升序」取第一场；开始时间缺失的排在该天最后。
 *
 * 没有候选（考试还没加载、数据为空、全部考完、日期字段都不可用）时返回 null，
 * 调用方据此整块隐藏卡片，不会留下空白卡或一直转圈的占位。
 */
fun findUpcomingExam(exams: List<Exam>, today: LocalDate): HomeUpcomingExam? =
    exams
        .mapNotNull { exam -> exam.toHomeUpcomingExam(today) }
        .minWithOrNull(compareBy({ it.date }, { it.startTime ?: "99:99" }))

/**
 * 校历周次文案，例如「第 3 周 · 09-15 ~ 09-21」。
 *
 * 周次序号或起止日期任一不可用时返回 null（调用方就不显示），避免出现「第 0 周」这种误导信息。
 */
fun weekRangeLabel(week: Week): String? {
  val number = week.serialNumber.takeIf { it > 0 } ?: return null
  val start = shortMonthDayLabel(week.startDate) ?: return null
  val end = shortMonthDayLabel(week.endDate) ?: return null
  return "第 $number 周 · $start ~ $end"
}

/** 把 `yyyy-MM-dd` 压成 `MM-dd`；解析失败返回 null。 */
internal fun shortMonthDayLabel(isoDate: String): String? {
  val date = runCatching { LocalDate.parse(isoDate.trim()) }.getOrNull() ?: return null
  return "${padTwo(date.month.ordinal + 1)}-${padTwo(date.day)}"
}

private fun padTwo(value: Int): String = if (value < 10) "0$value" else value.toString()

private fun Exam.toHomeUpcomingExam(today: LocalDate): HomeUpcomingExam? {
  val rawDate = examDate?.substringBefore(' ')?.trim().orEmpty()
  if (rawDate.isEmpty()) return null
  val date = runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: return null
  if (date < today) return null
  return HomeUpcomingExam(
      courseName = courseName,
      date = date,
      startTime = startTime?.trim()?.takeIf { it.isNotEmpty() },
      endTime = endTime?.trim()?.takeIf { it.isNotEmpty() },
      place = examPlace?.trim()?.takeIf { it.isNotEmpty() },
      seatNo = examSeatNo?.trim()?.takeIf { it.isNotEmpty() },
      // toEpochDays 返回 Long，考试距今不超过几万天，转 Int 安全。
      daysUntil = (date.toEpochDays() - today.toEpochDays()).toInt(),
  )
}
