package cn.edu.buaa.hzcampus.ui.common.util

import androidx.compose.runtime.Composable

/** 返回跳转钉钉「空间预约管理平台」的动作。Android 唤起钉钉 App；其它平台为空操作。 */
@Composable
expect fun rememberOpenDingTalkSpaceReservation(): () -> Unit
