package cn.edu.buaa.hzcampus.api.core

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*

actual fun getDefaultEngine(): HttpClientEngine = CIO.create()
