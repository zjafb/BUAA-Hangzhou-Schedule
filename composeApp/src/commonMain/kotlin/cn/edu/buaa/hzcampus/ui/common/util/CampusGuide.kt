package cn.edu.buaa.hzcampus.ui.common.util

import androidx.compose.runtime.Composable

/** 返回打开「北航中法未来科技学院（杭州）学习生活自助指南」的动作。 Android 使用内置 WebView 打开该静态站点；其它平台为空操作。 */
@Composable expect fun rememberOpenCampusGuide(): () -> Unit
