package xyz.mdhv.riverwip.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val IoDispatcher: CoroutineDispatcher = Dispatchers.IO
