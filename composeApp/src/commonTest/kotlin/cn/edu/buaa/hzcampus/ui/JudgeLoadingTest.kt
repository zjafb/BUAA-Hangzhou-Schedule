package cn.edu.buaa.hzcampus.ui

import cn.edu.buaa.hzcampus.api.feature.JudgeApi
import cn.edu.buaa.hzcampus.model.dto.JudgeAssignmentsResponse
import cn.edu.buaa.hzcampus.ui.screens.judge.JudgeViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class JudgeLoadingTest {
  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun failureCanRetryAndConcurrentLoadsAreDeduplicated() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    var calls = 0
    val model =
        JudgeViewModel(
            object : JudgeApi() {
              override suspend fun getAssignments(
                  includeExpired: Boolean,
                  userKey: String?,
              ): Result<JudgeAssignmentsResponse> {
                calls++
                delay(100)
                return if (calls == 1) Result.failure(IllegalStateException("offline"))
                else Result.success(JudgeAssignmentsResponse(emptyList()))
              }
            }
        )
    model.ensureAssignmentsLoaded()
    model.ensureAssignmentsLoaded()
    advanceUntilIdle()
    assertEquals(1, calls)
    assertFalse(model.hasAssignmentsLoaded())
    model.ensureAssignmentsLoaded()
    advanceUntilIdle()
    assertEquals(2, calls)
    assertTrue(model.hasAssignmentsLoaded())
    assertNull(model.uiState.value.error)
  }

  @Test
  fun resetRejectsLateResultEvenWhenBackendIgnoresCancellation() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val model =
        JudgeViewModel(
            object : JudgeApi() {
              override suspend fun getAssignments(
                  includeExpired: Boolean,
                  userKey: String?,
              ): Result<JudgeAssignmentsResponse> {
                withContext(NonCancellable) { delay(100) }
                return Result.success(JudgeAssignmentsResponse(emptyList()))
              }
            }
        )
    model.ensureAssignmentsLoaded()
    runCurrent()
    model.resetLoadedState()
    advanceUntilIdle()
    assertNull(model.uiState.value.assignmentsResponse)
    assertFalse(model.hasAssignmentsLoaded())
    assertFalse(model.uiState.value.isLoading)
  }
}
