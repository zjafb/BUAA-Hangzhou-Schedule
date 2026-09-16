package cn.edu.buaa.hzcampus.api.core

import cn.edu.buaa.hzcampus.api.ConnectionMode
import cn.edu.buaa.hzcampus.api.ConnectionRuntime
import cn.edu.buaa.hzcampus.api.auth.AuthServiceBackend
import cn.edu.buaa.hzcampus.api.auth.RelayAuthServiceBackend
import cn.edu.buaa.hzcampus.api.auth.RelayUserServiceBackend
import cn.edu.buaa.hzcampus.api.auth.UserServiceBackend
import cn.edu.buaa.hzcampus.api.feature.ClassroomApiBackend
import cn.edu.buaa.hzcampus.api.feature.EvaluationServiceBackend
import cn.edu.buaa.hzcampus.api.feature.GradeApiBackend
import cn.edu.buaa.hzcampus.api.feature.JudgeApiBackend
import cn.edu.buaa.hzcampus.api.feature.RelayClassroomApiBackend
import cn.edu.buaa.hzcampus.api.feature.RelayEvaluationServiceBackend
import cn.edu.buaa.hzcampus.api.feature.RelayGradeApiBackend
import cn.edu.buaa.hzcampus.api.feature.RelayJudgeApiBackend
import cn.edu.buaa.hzcampus.api.feature.RelayScheduleApiBackend
import cn.edu.buaa.hzcampus.api.feature.RelayYgdkApiBackend
import cn.edu.buaa.hzcampus.api.feature.ScheduleApiBackend
import cn.edu.buaa.hzcampus.api.feature.YgdkApiBackend
import cn.edu.buaa.hzcampus.api.local.LocalAuthServiceBackend
import cn.edu.buaa.hzcampus.api.local.LocalClassroomApiBackend
import cn.edu.buaa.hzcampus.api.local.LocalEvaluationServiceBackend
import cn.edu.buaa.hzcampus.api.local.LocalGradeApiBackend
import cn.edu.buaa.hzcampus.api.local.LocalJudgeApiBackend
import cn.edu.buaa.hzcampus.api.local.LocalScheduleApiBackend
import cn.edu.buaa.hzcampus.api.local.LocalUserServiceBackend
import cn.edu.buaa.hzcampus.api.local.LocalYgdkApiBackend

interface ApiFactory {
  fun authService(): AuthServiceBackend

  fun userService(): UserServiceBackend

  fun scheduleApi(): ScheduleApiBackend

  fun ygdkApi(): YgdkApiBackend

  fun classroomApi(): ClassroomApiBackend

  fun evaluationService(): EvaluationServiceBackend

  fun gradeApi(): GradeApiBackend

  fun judgeApi(): JudgeApiBackend
}

internal object DefaultApiFactory : ApiFactory {
  private val directBackends = LocalBackendSet()
  private val webVpnBackends = LocalBackendSet()

  private fun mode(): ConnectionMode =
      ConnectionRuntime.currentMode() ?: ConnectionMode.SERVER_RELAY

  fun clearCachedBackends() {
    directBackends.clearCache()
    webVpnBackends.clearCache()
  }

  override fun authService(): AuthServiceBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).authService
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).authService
        ConnectionMode.SERVER_RELAY -> RelayAuthServiceBackend()
      }

  override fun userService(): UserServiceBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).userService
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).userService
        ConnectionMode.SERVER_RELAY -> RelayUserServiceBackend()
      }

  override fun scheduleApi(): ScheduleApiBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).scheduleApi
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).scheduleApi
        ConnectionMode.SERVER_RELAY -> RelayScheduleApiBackend()
      }

  override fun ygdkApi(): YgdkApiBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).ygdkApi
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).ygdkApi
        ConnectionMode.SERVER_RELAY -> RelayYgdkApiBackend()
      }

  override fun classroomApi(): ClassroomApiBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).classroomApi
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).classroomApi
        ConnectionMode.SERVER_RELAY -> RelayClassroomApiBackend()
      }

  override fun evaluationService(): EvaluationServiceBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).evaluationService
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).evaluationService
        ConnectionMode.SERVER_RELAY -> RelayEvaluationServiceBackend()
      }

  override fun gradeApi(): GradeApiBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).gradeApi
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).gradeApi
        ConnectionMode.SERVER_RELAY -> RelayGradeApiBackend()
      }

  override fun judgeApi(): JudgeApiBackend =
      when (mode()) {
        ConnectionMode.DIRECT -> localBackends(ConnectionMode.DIRECT).judgeApi
        ConnectionMode.WEBVPN -> localBackends(ConnectionMode.WEBVPN).judgeApi
        ConnectionMode.SERVER_RELAY -> RelayJudgeApiBackend()
      }

  private fun localBackends(mode: ConnectionMode): LocalBackendSet =
      when (mode) {
        ConnectionMode.DIRECT -> directBackends
        ConnectionMode.WEBVPN -> webVpnBackends
        ConnectionMode.SERVER_RELAY -> error("Server relay mode does not use local backends")
      }

  private class LocalBackendSet {
    val authService = LocalAuthServiceBackend()
    val userService = LocalUserServiceBackend()
    val scheduleApi = LocalScheduleApiBackend()
    val ygdkApi = LocalYgdkApiBackend()
    val classroomApi = LocalClassroomApiBackend()
    val evaluationService = LocalEvaluationServiceBackend()
    val gradeApi = LocalGradeApiBackend()
    val judgeApi = LocalJudgeApiBackend()

    fun clearCache() {
      ygdkApi.clearCache()
      classroomApi.clearCache()
      evaluationService.clearCache()
      judgeApi.clearCache()
    }
  }
}

internal object RelayApiFactory : ApiFactory {
  override fun authService(): AuthServiceBackend = RelayAuthServiceBackend()

  override fun userService(): UserServiceBackend = RelayUserServiceBackend()

  override fun scheduleApi(): ScheduleApiBackend = RelayScheduleApiBackend()

  override fun ygdkApi(): YgdkApiBackend = RelayYgdkApiBackend()

  override fun classroomApi(): ClassroomApiBackend = RelayClassroomApiBackend()

  override fun evaluationService(): EvaluationServiceBackend = RelayEvaluationServiceBackend()

  override fun gradeApi(): GradeApiBackend = RelayGradeApiBackend()

  override fun judgeApi(): JudgeApiBackend = RelayJudgeApiBackend()
}
