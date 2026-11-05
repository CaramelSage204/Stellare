package com.example.stellare.ui.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CooldownTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val durationMs: Long
) {
    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    private var endsAt = 0L
    private var ticker: Job? = null

    fun isRunning(): Boolean = clock() < endsAt

    fun start() {
        endsAt = clock() + durationMs
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val remainingMs = endsAt - clock()
                val remainingSeconds = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
                _seconds.value = remainingSeconds
                if (remainingSeconds == 0) break
                delay(1000)
            }
        }
    }
}
