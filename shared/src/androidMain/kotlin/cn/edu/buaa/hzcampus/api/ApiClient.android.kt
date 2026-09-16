package cn.edu.buaa.hzcampus.api.core

import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*

actual fun getDefaultEngine(): HttpClientEngine = OkHttp.create()
