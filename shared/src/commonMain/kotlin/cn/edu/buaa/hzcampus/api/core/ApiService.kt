package cn.edu.buaa.hzcampus.api.core

import cn.edu.buaa.hzcampus.api.auth.safeApiCall
import cn.edu.buaa.hzcampus.model.dto.*
import io.ktor.client.request.*

/** 核心 API 服务接口，定义了应用所需的主要数据获取契约。 整合了课表、考试等核心业务功能。 */
interface ApiService {
  /**
   * 获取所有学期列表。
   *
   * @return 包含学期列表的 [Result]。若失败则包含异常信息。
   */
  suspend fun getTerms(): Result<List<Term>>

  /**
   * 获取指定学期的周次列表。
   *
   * @return 包含周次列表的 [Result]。若失败则包含异常信息。
   */
  suspend fun getWeeks(termCode: String): Result<List<Week>>

  /**
   * 获取指定周次的课程表。
   *
   * @return 包含周课表的 [Result]。若失败则包含异常信息。
   */
  suspend fun getWeeklySchedule(termCode: String, week: Int): Result<WeeklySchedule>

  /**
   * 获取今日课程摘要。
   *
   * @return 包含今日课程列表的 [Result]。若失败则包含异常信息。
   */
  suspend fun getTodaySchedule(): Result<List<TodayClass>>

  /**
   * 获取考试安排数据。
   *
   * @return 包含考试安排的 [Result]。若失败则包含异常信息。
   */
  suspend fun getExamArrangement(termCode: String): Result<ExamArrangementData>
}

/** ApiService 的标准实现类，通过 ApiClient 与后端通信。 */
class ApiServiceImpl(private val apiClient: ApiClient) : ApiService {

  override suspend fun getTerms(): Result<List<Term>> = safeApiCall {
    apiClient.getClient().get("api/v1/schedule/terms")
  }

  override suspend fun getWeeks(termCode: String): Result<List<Week>> = safeApiCall {
    apiClient.getClient().get("api/v1/schedule/weeks") { parameter("termCode", termCode) }
  }

  override suspend fun getWeeklySchedule(termCode: String, week: Int): Result<WeeklySchedule> =
      safeApiCall {
        apiClient.getClient().get("api/v1/schedule/week") {
          parameter("termCode", termCode)
          parameter("week", week)
        }
      }

  override suspend fun getTodaySchedule(): Result<List<TodayClass>> = safeApiCall {
    apiClient.getClient().get("api/v1/schedule/today")
  }

  override suspend fun getExamArrangement(termCode: String): Result<ExamArrangementData> =
      safeApiCall {
        apiClient.getClient().get("api/v1/exam/list") { parameter("termCode", termCode) }
      }
}
