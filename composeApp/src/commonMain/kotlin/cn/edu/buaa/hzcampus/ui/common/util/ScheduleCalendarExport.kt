package cn.edu.buaa.hzcampus.ui.common.util

import cn.edu.buaa.hzcampus.model.dto.Week
import cn.edu.buaa.hzcampus.model.dto.WeeklySchedule

/**
 * 课表导出到系统日历的结果。
 *
 * @property success 是否已成功生成 ICS 文件并唤起分享面板（或已落盘到下载目录）。
 * @property message 可直接展示给用户的中文提示，失败时也必须给出明确原因。
 * @property fileName 生成的 ICS 文件名，仅在成功时非空。
 * @property eventCount 生成的日历日程（VEVENT）数量。
 */
data class ScheduleCalendarExport(
    val success: Boolean,
    val message: String,
    val fileName: String? = null,
    val eventCount: Int = 0,
)

/**
 * 把整个学期的课表导出为 ICS 日历文件，并唤起系统分享/导入面板。
 *
 * 约定：
 * - 每门课按「星期 + 开始时间/结束时间」在它实际出现的**每一个周次**里生成一个日程，
 *   周次不连续时逐周生成比 RRULE 更可靠；日期取自 [Week.startDate]（该周周一）加上星期偏移。
 * - 只读调用方传入的本地课表数据，不联网。
 * - 生成的日程带提前 15 分钟的提醒（VALARM）。
 * - Android 端写入 cacheDir 后通过 FileProvider + `ACTION_SEND` 分享；分享面板不可用时
 *   退化为保存到下载目录。其它平台返回「不支持」的明确提示，不做静默空操作。
 */
expect fun exportScheduleToCalendar(
    termName: String,
    termCode: String,
    weeks: List<Week>,
    schedules: Map<Int, WeeklySchedule>,
): ScheduleCalendarExport
