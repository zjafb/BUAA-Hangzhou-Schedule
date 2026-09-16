package cn.edu.buaa.hzcampus.model.dto

import kotlinx.serialization.Serializable

/** 今日计划任务。 */
@Serializable
data class PlanTask(
    val id: String,
    val title: String,
    val date: String, // yyyy-MM-dd
    val startTime: String? = null, // HH:mm
    val endTime: String? = null,
    val note: String = "",
    val reminderAt: String? = null, // HH:mm
    val color: Long = 0xFF4CAF50,
)
