package cn.edu.buaa.hzcampus.api.core

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

actual fun getDefaultEngine(): HttpClientEngine = Js.create()
