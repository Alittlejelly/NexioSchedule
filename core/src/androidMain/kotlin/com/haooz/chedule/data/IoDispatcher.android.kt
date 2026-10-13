package com.haooz.chedule.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Android：真正的 IO 池，与迁移前逐字等价，行为零变化。 */
actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO
