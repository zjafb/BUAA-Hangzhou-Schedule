package cn.edu.buaa.hzcampus.ui.common.util

import cn.edu.buaa.hzcampus.model.dto.Week
import cn.edu.buaa.hzcampus.model.dto.WeeklySchedule

/** 桌面端暂不提供课表日历导出，明确返回「不支持」而不是静默失败。 */
actual fun exportScheduleToCalendar(
    termName: String,
    termCode: String,
    weeks: List<Week>,
    schedules: Map<Int, WeeklySchedule>,
): ScheduleCalendarExport =
    ScheduleCalendarExport(success = false, message = "当前平台暂不支持导出课表到系统日历，请在 Android 手机上使用该功能")
