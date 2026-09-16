package cn.edu.buaa.hzcampus.api.feature

import cn.edu.buaa.hzcampus.api.ConnectionRuntime
import cn.edu.buaa.hzcampus.api.auth.ApiClientProvider
import cn.edu.buaa.hzcampus.api.auth.safeApiCall
import cn.edu.buaa.hzcampus.api.core.ApiClient
import cn.edu.buaa.hzcampus.model.dto.GradeData
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface GradeApiBackend {
  suspend fun getGrades(termCode: String): Result<GradeData>
}

class GradeApi(
    private val backendProvider: () -> GradeApiBackend = {
      ConnectionRuntime.apiFactory().gradeApi()
    }
) {
  internal constructor(backend: GradeApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelayGradeApiBackend(apiClient) })

  private fun currentBackend(): GradeApiBackend = backendProvider()

  suspend fun getGrades(termCode: String): Result<GradeData> = currentBackend().getGrades(termCode)
}

internal class RelayGradeApiBackend(private val apiClient: ApiClient = ApiClientProvider.shared) :
    GradeApiBackend {
  override suspend fun getGrades(termCode: String): Result<GradeData> {
    return safeApiCall {
      apiClient.getClient().get("api/v1/grade/list") { parameter("termCode", termCode) }
    }
  }
}
