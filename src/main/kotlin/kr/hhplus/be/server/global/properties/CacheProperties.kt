package kr.hhplus.be.server.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cache")
data class CacheProperties(
    val keyPrefix: String = "concert-service:",
    val defaultTtl: Duration = Duration.ofMinutes(30),
    val concerts: CacheSetting = CacheSetting(Duration.ofHours(2)),
    val concertSchedules: CacheSetting = CacheSetting(Duration.ofHours(1)),
    val concertDetails: CacheSetting = CacheSetting(Duration.ofMinutes(30)),
    val availableConcerts: CacheSetting = CacheSetting(Duration.ofMinutes(5)),
    val availableSeats: CacheSetting = CacheSetting(Duration.ofMinutes(2)),
    val popularConcerts: CacheSetting = CacheSetting(Duration.ofMinutes(10)),
    val trendingConcerts: CacheSetting = CacheSetting(Duration.ofMinutes(3)),
    val systemTypes: CacheSetting = CacheSetting(Duration.ofHours(24)),
    val systemConfig: CacheSetting = CacheSetting(Duration.ofHours(12)),
    val userBalance: CacheSetting = CacheSetting(Duration.ofMinutes(5)),
    val tokenStatus: CacheSetting = CacheSetting(Duration.ofMinutes(1))
)

data class CacheSetting(
    val ttl: Duration,
    val maxSize: Long? = null,
    val enableEviction: Boolean = true
)
