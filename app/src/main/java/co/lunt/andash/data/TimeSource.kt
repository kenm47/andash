package co.lunt.andash.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Single source of "now" so tests and the debug time-shift can control it. */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId
}

class SystemTimeSource : TimeSource {
    /** Debug-only shift applied to the system clock. */
    @Volatile
    var offset: Duration = Duration.ZERO

    override fun now(): Instant = Instant.now().plus(offset)
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
