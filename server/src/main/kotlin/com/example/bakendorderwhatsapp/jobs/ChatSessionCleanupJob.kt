package com.example.bakendorderwhatsapp.jobs

import com.example.bakendorderwhatsapp.domain.usecase.CleanupInactiveChatSessionsUseCase
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

fun Application.startChatSessionCleanupJob(
    cleanupInactiveChatSessions: CleanupInactiveChatSessionsUseCase,
    intervalMinutes: Long = 1
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val intervalMs = TimeUnit.MINUTES.toMillis(intervalMinutes)

    scope.launch {
        log.info("Chat session cleanup job started (every {} min)", intervalMinutes)
        while (isActive) {
            runCatching {
                cleanupInactiveChatSessions()
            }.onFailure {
                log.error("Chat session cleanup failed", it)
            }
            delay(intervalMs)
        }
    }
}
