package kr.hhplus.be.server.api.concert.dto

import kr.hhplus.be.server.domain.concert.models.Concert
import java.time.LocalDateTime

data class PopularConcertDto(
    val concertId: Long,
    val title: String,
    val artist: String,
    val imageUrl: String? = null,
    val popularityScore: Double,
    val viewCount: Long,
    val nextShowDate: LocalDateTime? = null,
    val upcomingScheduleCount: Int = 0,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val isHot: Boolean = false
) {
    companion object {
        fun from(concert: Concert, stats: PopularityStats): PopularConcertDto {
            return PopularConcertDto(
                concertId = concert.concertId,
                title = concert.title,
                artist = concert.artist,
                imageUrl = null,
                popularityScore = stats.viewCount.toDouble(),
                viewCount = stats.viewCount,
                isHot = stats.isHot()
            )
        }
    }
}

data class PopularityStats(
    val viewCount: Long = 0,
    val lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    fun isHot(): Boolean {
        return viewCount >= 100
    }

    companion object {
        fun empty(): PopularityStats = PopularityStats()
    }
}
