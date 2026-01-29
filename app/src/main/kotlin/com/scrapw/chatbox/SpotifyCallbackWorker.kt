package com.scrapw.chatbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Compatibility worker left in place so builds don't break.
 * OAuth callback is handled by SpotifyCallbackActivity + SpotifyTokenExchange.
 */
class SpotifyCallbackWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return Result.success()
    }
}
