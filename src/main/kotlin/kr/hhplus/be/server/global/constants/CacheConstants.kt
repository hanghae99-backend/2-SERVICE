package kr.hhplus.be.server.global.constants

import java.time.Duration

/**
 * 캐시 관련 상수 정의
 * 모든 캐시 이름과 TTL을 중앙 관리
 */
object CacheConstants {
    // ========== 캐시 이름 ==========
    // 상태 타입 캐시
    const val SEAT_STATUS_TYPES = "seat:status:types"
    const val RESERVATION_STATUS_TYPES = "reservation:status:types"
    const val PAYMENT_STATUS_TYPES = "payment:status:types"
    const val POINT_HISTORY_TYPES = "point:history:types"
    
    // 콘서트 관련 캐시
    const val CONCERTS = "concerts"
    const val CONCERT_SCHEDULES = "concerts:schedules"
    const val CONCERT_DETAILS = "concerts:details"
    const val AVAILABLE_CONCERTS = "concerts:available"
    const val POPULAR_CONCERTS = "concerts:popular"
    const val TRENDING_CONCERTS = "concerts:trending"
    
    // 좌석 관련 캐시
    const val AVAILABLE_SEATS = "seats:available"
    
    // 사용자 관련 캐시
    const val USER_BALANCE = "users:balance"
    const val TOKEN_STATUS = "tokens:status"
    
    // ========== TTL 설정 ==========
    // 상태 타입은 거의 변경되지 않음
    val STATUS_TYPE_TTL: Duration = Duration.ofHours(24)
    
    // 콘서트 정보는 자주 변경될 수 있음
    val CONCERT_TTL: Duration = Duration.ofMinutes(10)
    val CONCERT_SCHEDULE_TTL: Duration = Duration.ofMinutes(10)
    val CONCERT_DETAIL_TTL: Duration = Duration.ofMinutes(30)
    val AVAILABLE_CONCERT_TTL: Duration = Duration.ofMinutes(5)
    val POPULAR_CONCERT_TTL: Duration = Duration.ofHours(1)
    val TRENDING_CONCERT_TTL: Duration = Duration.ofMinutes(30)
    
    // 좌석 정보는 매우 자주 변경됨
    val SEAT_TTL: Duration = Duration.ofMinutes(1)
    
    // 사용자 정보
    val USER_BALANCE_TTL: Duration = Duration.ofMinutes(5)
    val TOKEN_TTL: Duration = Duration.ofMinutes(10)
    
    // 기본 TTL
    val DEFAULT_TTL: Duration = Duration.ofMinutes(10)
}
