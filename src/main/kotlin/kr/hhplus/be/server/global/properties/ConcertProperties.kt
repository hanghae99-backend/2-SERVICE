package kr.hhplus.be.server.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "concert")
data class ConcertProperties(
    val defaultSeatsPerSchedule: Int = 50,
    val maxSeatsPerSchedule: Int = 1000,
    val reservationTimeoutMinutes: Long = 5L,
    val maxReservationsPerUser: Int = 10,
    val maxConcurrentReservations: Int = 3,
    val minConcertDurationMinutes: Int = 30,
    val maxConcertDurationMinutes: Int = 480,
    val minScheduleAdvanceHours: Long = 1L,
    val queue: QueueProperties = QueueProperties()
) {
    val reservationTimeout: Duration
        get() = Duration.ofMinutes(reservationTimeoutMinutes)
    
    val minScheduleAdvance: Duration
        get() = Duration.ofHours(minScheduleAdvanceHours)
}

data class QueueProperties(
    val maxActiveTokens: Long = 100L,
    val tokenExpiryMinutes: Long = 5L,
    val waitingTimeMultiplier: Int = 2,
    val tokensPerMinute: Int = 10
) {
    val tokenExpiry: Duration
        get() = Duration.ofMinutes(tokenExpiryMinutes)
}
