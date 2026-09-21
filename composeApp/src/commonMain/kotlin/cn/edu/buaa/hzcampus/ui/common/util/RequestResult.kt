package cn.edu.buaa.hzcampus.ui.common.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** API 返回前再次检查取消状态，防止旧请求或吞掉取消异常的后端覆盖新页面。 */
internal suspend fun <T> requestResult(block: suspend () -> Result<T>): Result<T> {
  val result =
      try {
        block()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        Result.failure(error)
      }
  currentCoroutineContext().ensureActive()
  (result.exceptionOrNull() as? CancellationException)?.let { throw it }
  return result
}
