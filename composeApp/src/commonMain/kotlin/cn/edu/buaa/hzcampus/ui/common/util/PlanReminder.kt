package cn.edu.buaa.hzcampus.ui.common.util

import cn.edu.buaa.hzcampus.model.dto.PlanTask

/** 为所有设置了提醒时间的计划安排通知（Android 用 AlarmManager，其它平台为空操作）。 */
expect fun schedulePlanReminders(tasks: List<PlanTask>)

/** 取消所有计划提醒。 */
expect fun cancelPlanReminders()
