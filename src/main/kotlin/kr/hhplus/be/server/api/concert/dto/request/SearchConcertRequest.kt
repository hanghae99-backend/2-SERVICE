package kr.hhplus.be.server.api.concert.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import java.time.LocalDate

/**
 * 콘서트 검색 요청 (실제 사용됨)
 */
@Schema(description = "콘서트 검색 요청")
data class SearchConcertRequest(
    @Size(min = 1, max = 100, message = "검색 키워드는 1-100자 사이여야 합니다")
    @Schema(description = "검색 키워드 (제목 또는 아티스트)", example = "아이유")
    val keyword: String? = null,
    
    @Schema(description = "시작 날짜", example = "2024-01-01")
    val startDate: LocalDate? = null,
    
    @Schema(description = "종료 날짜", example = "2024-12-31")
    val endDate: LocalDate? = null,
    
    @Schema(description = "아티스트 필터", example = "아이유")
    val artist: String? = null,
    
    @Schema(description = "예약 가능한 콘서트만 조회", example = "true")
    val availableOnly: Boolean = false
) {
    init {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw IllegalArgumentException("시작 날짜는 종료 날짜보다 이전이어야 합니다")
        }
    }
}
