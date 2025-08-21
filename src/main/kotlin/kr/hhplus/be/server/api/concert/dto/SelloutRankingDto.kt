package kr.hhplus.be.server.api.concert.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import kr.hhplus.be.server.domain.concert.models.Concert
import java.time.LocalDateTime

data class SelloutRankingDto(
    val concertId: Long,
    val title: String,
    val artist: String,
    val reservationsInLastHour: Long,
    val totalSeats: Int,
    val availableSeats: Int,
    val selloutRate: Double,
    val ranking: Int,
    val isHot: Boolean = false,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val nextShowDate: LocalDateTime? = null
) {
    companion object {
        fun from(
            concert: Concert,
            reservationCount: Long,
            totalSeats: Int,
            availableSeats: Int,
            ranking: Int,
            nextShowDate: LocalDateTime? = null
        ): SelloutRankingDto {
            val selloutRate = if (totalSeats > 0) {
                ((totalSeats - availableSeats).toDouble() / totalSeats.toDouble()) * 100
            } else {
                0.0
            }
            
            return SelloutRankingDto(
                concertId = concert.concertId,
                title = concert.title,
                artist = concert.artist,
                reservationsInLastHour = reservationCount,
                totalSeats = totalSeats,
                availableSeats = availableSeats,
                selloutRate = selloutRate,
                ranking = ranking,
                isHot = reservationCount >= 10, // 1시간에 10석 이상이면 HOT
                nextShowDate = nextShowDate
            )
        }
    }
}