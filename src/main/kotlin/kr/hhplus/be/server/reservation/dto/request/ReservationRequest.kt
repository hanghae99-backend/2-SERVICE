package kr.hhplus.be.server.reservation.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*

/**
 * 예약 생성 요청 DTO
 */
@Schema(description = "예약 생성 요청")
data class ReservationCreateRequest(
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    @Schema(description = "사용자 ID", example = "1", required = true)
    val userId: Long,
    
    @field:NotNull(message = "콘서트 ID는 필수입니다")
    @field:Positive(message = "콘서트 ID는 양수여야 합니다")
    @Schema(description = "콘서트 ID", example = "1", required = true)
    val concertId: Long,
    
    @field:NotNull(message = "좌석 ID는 필수입니다")
    @field:Positive(message = "좌석 ID는 양수여야 합니다")
    @Schema(description = "좌석 ID", example = "1", required = true)
    val seatId: Long,
    
    @field:NotBlank(message = "토큰은 필수입니다")
    @field:Size(min = 10, max = 200, message = "토큰 길이는 10-200자 사이여야 합니다")
    @Schema(description = "대기열 토큰", example = "abc123def456", required = true)
    val token: String
)

/**
 * 예약 취소 요청 DTO
 */
@Schema(description = "예약 취소 요청")
data class ReservationCancelRequest(
    @field:NotNull(message = "예약 ID는 필수입니다")
    @field:Positive(message = "예약 ID는 양수여야 합니다")
    @Schema(description = "예약 ID", example = "1", required = true)
    val reservationId: Long,
    
    @field:NotNull(message = "사용자 ID는 필수입니다")
    @field:Positive(message = "사용자 ID는 양수여야 합니다")
    @Schema(description = "사용자 ID", example = "1", required = true)
    val userId: Long,
    
    @field:Size(max = 500, message = "취소 사유는 500자를 초과할 수 없습니다")
    @Schema(description = "취소 사유", example = "개인 사정으로 인한 취소", required = false)
    val cancelReason: String? = null
)

/**
 * 예약 확정 요청 DTO
 */
@Schema(description = "예약 확정 요청")
data class ReservationConfirmRequest(
    @field:NotNull(message = "예약 ID는 필수입니다")
    @field:Positive(message = "예약 ID는 양수여야 합니다")
    @Schema(description = "예약 ID", example = "1", required = true)
    val reservationId: Long,
    
    @field:NotNull(message = "결제 ID는 필수입니다")
    @field:Positive(message = "결제 ID는 양수여야 합니다")
    @Schema(description = "결제 ID", example = "1", required = true)
    val paymentId: Long
)

/**
 * 목록 조회 요청 DTO
 */
@Schema(description = "목록 조회 요청")
data class ReservationListRequest(
    @field:Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
    @Schema(description = "페이지 번호", example = "1", defaultValue = "1")
    val pageNumber: Int = 1,
    
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
    @Schema(description = "페이지 크기", example = "20", defaultValue = "20")
    val pageSize: Int = 20,
    
    @Schema(description = "정렬 기준", example = "reservedAt", defaultValue = "reservedAt")
    val sortBy: String = "reservedAt",
    
    @Schema(description = "정렬 방향", example = "DESC", defaultValue = "DESC")
    val sortDirection: String = "DESC"
)

/**
 * 콘서트별 목록 조회 요청 DTO
 */
@Schema(description = "콘서트별 목록 조회 요청")
data class ReservationConcertListRequest(
    @Schema(description = "예약 상태 필터", example = "[\"CONFIRMED\", \"TEMPORARY\"]")
    val statusList: List<String>? = null,
    
    @field:Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
    @Schema(description = "페이지 번호", example = "1", defaultValue = "1")
    val pageNumber: Int = 1,
    
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
    @Schema(description = "페이지 크기", example = "20", defaultValue = "20")
    val pageSize: Int = 20,
    
    @Schema(description = "정렬 기준", example = "reservedAt", defaultValue = "reservedAt")
    val sortBy: String = "reservedAt",
    
    @Schema(description = "정렬 방향", example = "DESC", defaultValue = "DESC")
    val sortDirection: String = "DESC"
)

/**
 * 검색 요청 DTO
 */
@Schema(description = "예약 검색 요청")
data class ReservationSearchRequest(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long? = null,
    
    @Schema(description = "콘서트 ID", example = "1")
    val concertId: Long? = null,
    
    @Schema(description = "예약 상태 목록", example = "[\"TEMPORARY\", \"CONFIRMED\"]")
    val statusList: List<String>? = null,
    
    @Schema(description = "검색 시작일 (yyyy-MM-dd)", example = "2024-01-01")
    val startDate: String? = null,
    
    @Schema(description = "검색 종료일 (yyyy-MM-dd)", example = "2024-12-31")
    val endDate: String? = null,
    
    @field:Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
    @Schema(description = "페이지 번호", example = "1", defaultValue = "1")
    val pageNumber: Int = 1,
    
    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @field:Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
    @Schema(description = "페이지 크기", example = "20", defaultValue = "20")
    val pageSize: Int = 20,
    
    @Schema(description = "정렬 기준", example = "reservedAt", defaultValue = "reservedAt")
    val sortBy: String = "reservedAt",
    
    @Schema(description = "정렬 방향", example = "DESC", defaultValue = "DESC")
    val sortDirection: String = "DESC"
) {
    fun toSearchCondition(): ReservationSearchCondition {
        return ReservationSearchCondition(
            userId = userId,
            concertId = concertId,
            statusList = statusList,
            startDate = startDate,
            endDate = endDate,
            pageNumber = pageNumber,
            pageSize = pageSize,
            sortBy = sortBy,
            sortDirection = sortDirection
        )
    }
}

/**
 * 통계 요청 DTO
 */
@Schema(description = "통계 조회 요청")
data class ReservationStatsRequest(
    @Schema(description = "콘서트 ID (선택사항)", example = "1")
    val concertId: Long? = null,
    
    @Schema(description = "통계 시작일 (yyyy-MM-dd)", example = "2024-01-01")
    val startDate: String? = null,
    
    @Schema(description = "통계 종료일 (yyyy-MM-dd)", example = "2024-12-31")
    val endDate: String? = null
)

/**
 * 내부 변환용 검색 조건 (기존 SearchCondition 호환)
 */
data class ReservationSearchCondition(
    val userId: Long? = null,
    val concertId: Long? = null,
    val statusList: List<String>? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val pageNumber: Int = 1,
    val pageSize: Int = 20,
    val sortBy: String = "reservedAt",
    val sortDirection: String = "DESC"
) {
    fun getOffset(): Int = (pageNumber - 1) * pageSize
}
