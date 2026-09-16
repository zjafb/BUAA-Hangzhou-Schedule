package cn.edu.buaa.hzcampus.api

import kotlinx.coroutines.sync.Mutex

internal val localConnectionTestMutex = Mutex()
