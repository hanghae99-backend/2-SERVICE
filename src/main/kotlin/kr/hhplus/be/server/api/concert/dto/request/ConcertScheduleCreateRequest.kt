package kr.hhplus.be.server.api.concert.dto.request

import java.time.LocalDate

/**
 * 콘서트 스케줄 생성 요청
 */
data class ConcertScheduleCreateRequest(
    val concertId: Long,
    val concertDate: LocalDate,
    val venue: String
) {
    init {
        require(concertId > 0) { "콘서트 ID는 0보다 커야 합니다" }
        require(concertDate.isAfter(LocalDate.now().minusDays(1))) { "콘서트 날짜는 오늘 이후여야 합니다" }
        require(venue.isNotBlank()) { "공연장 이름은 필수입니다" }
    }
}