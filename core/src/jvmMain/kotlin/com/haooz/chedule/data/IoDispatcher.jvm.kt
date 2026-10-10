package com.haooz.chedule.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** JVM / 桌面：同为真正的 IO 池。 */
actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO
