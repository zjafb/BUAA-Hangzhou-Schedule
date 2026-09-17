package cn.edu.buaa.hzcampus.model.dto

import kotlinx.datetime.LocalDate

/**
 * 中国法定节假日安排的类型。
 *
 * @property HOLIDAY 法定放假日，包含因调休拼假产生的休息日（例如国庆 7 天里的周末）。
 * @property MAKEUP_WORKDAY 调休上班日，原本是周末但因拼假需要上班。
 */
enum class ChinaHolidayKind {
  HOLIDAY,
  MAKEUP_WORKDAY,
}

/**
 * 某一天的中国节假日标记。
 *
 * @property date 日期。
 * @property kind 类型（放假 / 调休上班）。
 * @property name 所属节日名称，如「国庆节」。
 */
data class ChinaHolidayMark(
    val date: LocalDate,
    val kind: ChinaHolidayKind,
    val name: String,
)

/**
 * 中国法定节假日与调休安排的离线数据表。
 *
 * 数据年份：**2026 年**。
 *
 * 来源：国务院办公厅《关于 2026 年部分节假日安排的通知》，国办发明电〔2025〕7 号，2025 年 11 月 4 日发布。
 * 原文见中国政府网（https://www.gov.cn/gongbao/2025/issue_12406/202511/content_7048922.html），要点摘录：
 * - 元旦：1 月 1 日（周四）至 3 日（周六）放假调休，共 3 天；1 月 4 日（周日）上班。
 * - 春节：2 月 15 日（周日）至 23 日（周一）放假调休，共 9 天；2 月 14 日（周六）、2 月 28 日（周六）上班。
 * - 清明节：4 月 4 日（周六）至 6 日（周一）放假，共 3 天。
 * - 劳动节：5 月 1 日（周五）至 5 日（周二）放假调休，共 5 天；5 月 9 日（周六）上班。
 * - 端午节：6 月 19 日（周五）至 21 日（周日）放假，共 3 天。
 * - 中秋节：9 月 25 日（周五）至 27 日（周日）放假，共 3 天。
 * - 国庆节：10 月 1 日（周四）至 7 日（周三）放假调休，共 7 天；9 月 20 日（周日）、10 月 10 日（周六）上班。
 *
 * ⚠️ 这张表是硬编码的离线数据，**需要每年更新**：国务院办公厅一般在上一年的 11 月发布下一年的安排， 届时照着新通知在 [marks] 里增删条目即可，数据结构和 UI 都不用改。
 * 表外的年份不做任何猜测（宁可不显示标记，也不显示错误标记）。
 *
 * 关于 2025 年年底：2025 年 12 月 31 日（周三）是正常工作日，2026 年元旦假期从 2026 年 1 月 1 日开始， 因此这里不需要 2025 年的条目。
 */
object ChinaHolidays {
  /** 日期 -> 标记。查询是纯内存的，离线可用。 */
  private val marks: Map<LocalDate, ChinaHolidayMark> =
      buildList {
            // 一、元旦：1 月 1 日至 3 日放假，1 月 4 日（周日）上班。
            holiday("元旦", "2026-01-01", "2026-01-02", "2026-01-03")
            makeupWorkday("元旦", "2026-01-04")

            // 二、春节：2 月 15 日至 23 日放假，2 月 14 日、2 月 28 日（均为周六）上班。
            holiday(
                "春节",
                "2026-02-15",
                "2026-02-16",
                "2026-02-17",
                "2026-02-18",
                "2026-02-19",
                "2026-02-20",
                "2026-02-21",
                "2026-02-22",
                "2026-02-23",
            )
            makeupWorkday("春节", "2026-02-14", "2026-02-28")

            // 三、清明节：4 月 4 日至 6 日放假，不调休。
            holiday("清明节", "2026-04-04", "2026-04-05", "2026-04-06")

            // 四、劳动节：5 月 1 日至 5 日放假，5 月 9 日（周六）上班。
            holiday("劳动节", "2026-05-01", "2026-05-02", "2026-05-03", "2026-05-04", "2026-05-05")
            makeupWorkday("劳动节", "2026-05-09")

            // 五、端午节：6 月 19 日至 21 日放假，不调休。
            holiday("端午节", "2026-06-19", "2026-06-20", "2026-06-21")

            // 六、中秋节：9 月 25 日至 27 日放假，不调休。
            holiday("中秋节", "2026-09-25", "2026-09-26", "2026-09-27")

            // 七、国庆节：10 月 1 日至 7 日放假，9 月 20 日（周日）、10 月 10 日（周六）上班。
            holiday(
                "国庆节",
                "2026-10-01",
                "2026-10-02",
                "2026-10-03",
                "2026-10-04",
                "2026-10-05",
                "2026-10-06",
                "2026-10-07",
            )
            makeupWorkday("国庆节", "2026-09-20", "2026-10-10")
          }
          .associateBy { it.date }

  /** 查 [date] 当天的节假日标记；当天不在数据表里（普通工作日或表外年份）时返回 null。 */
  fun markOn(date: LocalDate): ChinaHolidayMark? = marks[date]

  /** 首页小标签文案，例如「今天：国庆节」「今天：调休上班（春节）」。 */
  fun label(mark: ChinaHolidayMark): String =
      when (mark.kind) {
        ChinaHolidayKind.HOLIDAY -> "今天：${mark.name}"
        ChinaHolidayKind.MAKEUP_WORKDAY -> "今天：调休上班（${mark.name}）"
      }

  private fun MutableList<ChinaHolidayMark>.holiday(name: String, vararg dates: String) {
    dates.forEach { date ->
      add(ChinaHolidayMark(LocalDate.parse(date), ChinaHolidayKind.HOLIDAY, name))
    }
  }

  private fun MutableList<ChinaHolidayMark>.makeupWorkday(name: String, vararg dates: String) {
    dates.forEach { date ->
      add(ChinaHolidayMark(LocalDate.parse(date), ChinaHolidayKind.MAKEUP_WORKDAY, name))
    }
  }
}
