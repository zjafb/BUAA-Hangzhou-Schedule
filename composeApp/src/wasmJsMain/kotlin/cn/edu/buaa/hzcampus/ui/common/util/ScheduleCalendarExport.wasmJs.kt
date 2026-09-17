package cn.edu.buaa.hzcampus.ui.common.util

import cn.edu.buaa.hzcampus.model.dto.Week
import cn.edu.buaa.hzcampus.model.dto.WeeklySchedule

/** 浏览器端无法一键导入手机系统日历，明确返回「不支持」提示。 */
actual fun exportScheduleToCalendar(
    termName: String,
    termCode: String,
    weeks: List<Week>,
    schedules: Map<Int, WeeklySchedule>,
): ScheduleCalendarExport =
    ScheduleCalendarExport(success = false, message = "当前平台暂不支持导出课表到系统日历，请在 Android 手机上使用该功能")
