package cn.edu.buaa.hzcampus.repository

import cn.edu.buaa.hzcampus.api.feature.ScheduleApi
import cn.edu.buaa.hzcampus.api.feature.ScheduleApiBackend
import cn.edu.buaa.hzcampus.model.dto.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class TermRepositoryTest {
  @Test
  fun clearDuringRequestRejectsOldTermsAndAllowsReload() = runTest {
    val started = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()
    var calls = 0
    val backend =
        object : ScheduleApiBackend {
          override suspend fun getTerms(): Result<List<Term>> {
            val request = ++calls
            if (request == 1) {
              started.complete(Unit)
              finish.await()
            }
            return Result.success(listOf(Term("$request", "学期", true, request)))
          }

          override suspend fun getWeeks(termCode: String): Result<List<Week>> = error("unused")

          override suspend fun getWeeklySchedule(
              termCode: String,
              week: Int,
          ): Result<WeeklySchedule> = error("unused")

          override suspend fun getTodaySchedule(): Result<List<TodayClass>> = error("unused")

          override suspend fun getExamArrangement(termCode: String): Result<ExamArrangementData> =
              error("unused")
        }
    val repository = TermRepository(ScheduleApi(backend))
    val first = async { repository.getTerms() }
    started.await()
    repository.clear()
    finish.complete(Unit)
    assertTrue(first.await().isFailure)
    assertEquals("2", repository.getTerms().getOrThrow().single().itemCode)
    assertEquals("2", repository.getTerms().getOrThrow().single().itemCode)
    assertEquals(2, calls)
  }
}
