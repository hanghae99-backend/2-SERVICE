package kr.hhplus.be.server.test.utils

import org.springframework.data.redis.connection.RedisConnection
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 테스트 데이터베이스 초기화 유틸리티
 */
object TestDataCleanupUtils {
    
    /**
     * 데이터베이스 테이블을 순서대로 정리
     */
    fun cleanupDatabase(jdbcTemplate: JdbcTemplate) {
        try {
            // 외래키 제약 순서를 고려한 삭제
            jdbcTemplate.execute("DELETE FROM payment")
            jdbcTemplate.execute("DELETE FROM reservation")
            jdbcTemplate.execute("DELETE FROM seat")
            jdbcTemplate.execute("DELETE FROM concert_schedule")
            jdbcTemplate.execute("DELETE FROM concert")
            jdbcTemplate.execute("DELETE FROM point_history")
            jdbcTemplate.execute("DELETE FROM point")
            jdbcTemplate.execute("DELETE FROM users")
        } catch (e: Exception) {
            // 테스트 환경에서 DB 정리 실패는 무시
        }
    }
    
    /**
     * 시퀀스 초기화
     */
    fun resetSequences(jdbcTemplate: JdbcTemplate) {
        try {
            jdbcTemplate.execute("ALTER SEQUENCE IF EXISTS users_user_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE IF EXISTS concert_concert_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE IF EXISTS concert_schedule_schedule_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE IF EXISTS seat_seat_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE IF EXISTS reservation_reservation_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE IF EXISTS payment_payment_id_seq RESTART WITH 1")
        } catch (e: Exception) {
            // 시퀀스 초기화 실패는 무시
        }
    }
}
