package xyz.mdhv.riverwip.data

import kotlinx.coroutines.CoroutineDispatcher

/** `IoDispatcher`, which common code cannot name directly (it is a platform member on native). */
expect val IoDispatcher: CoroutineDispatcher
