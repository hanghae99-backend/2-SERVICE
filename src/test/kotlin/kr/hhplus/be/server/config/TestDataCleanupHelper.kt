package kr.hhplus.be.server.config

import org.springframework.cache.CacheManager
import org.springframework.context.ApplicationContext
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 테스트 데이터 정리 헬퍼
 * 모든 테스트에서 공통으로 사용할 수 있는 정리 메서드들을 제공
 */
object TestDataCleanupHelper {
    
    /**
     * Redis 데이터베이스 초기화 (캐시 포함)
     */
    fun cleanupRedis(redisTemplate: RedisTemplate<*, *>) {
        try {
            redisTemplate.connectionFactory?.connection?.use { connection ->
                // flushDb() 대신 flushAll() 사용하여 모든 DB 초기화
                connection.serverCommands()?.flushAll()
            }
        } catch (e: Exception) {
            println("Redis cleanup failed: ${e.message}")
            // 테스트 환경에서 Redis 초기화 실패는 무시
        }
    }
    
    /**
     * 캐시 매니저 초기화
     */
    fun cleanupCacheManager(context: ApplicationContext) {
        try {
            val cacheManager = context.getBean(CacheManager::class.java)
            cacheManager.cacheNames.forEach { cacheName ->
                cacheManager.getCache(cacheName)?.clear()
            }
        } catch (e: Exception) {
            println("Cache manager cleanup failed: ${e.message}")
        }
    }
    
    /**
     * 데이터베이스 테이블 전체 정리 (외래키 순서 고려)
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
            jdbcTemplate.execute("DELETE FROM balance")
            jdbcTemplate.execute("DELETE FROM point")
            jdbcTemplate.execute("DELETE FROM users")
            jdbcTemplate.execute("DELETE FROM point_history_type")
        } catch (e: Exception) {
            // 테스트 환경에서 DB 정리 실패는 무시
        }
    }
    
    /**
     * 특정 도메인만 정리 (사용자 관련)
     */
    fun cleanupUserData(jdbcTemplate: JdbcTemplate) {
        try {
            jdbcTemplate.execute("DELETE FROM point_history")
            jdbcTemplate.execute("DELETE FROM balance")
            jdbcTemplate.execute("DELETE FROM point")
            jdbcTemplate.execute("DELETE FROM users")
            jdbcTemplate.execute("DELETE FROM point_history_type")
        } catch (e: Exception) {
            // 테스트 환경에서 DB 정리 실패는 무시
        }
    }
    
    /**
     * 특정 도메인만 정리 (콘서트 관련)
     */
    fun cleanupConcertData(jdbcTemplate: JdbcTemplate) {
        try {
            jdbcTemplate.execute("DELETE FROM seat")
            jdbcTemplate.execute("DELETE FROM concert_schedule")
            jdbcTemplate.execute("DELETE FROM concert")
        } catch (e: Exception) {
            // 테스트 환경에서 DB 정리 실패는 무시
        }
    }
    
    /**
     * 특정 도메인만 정리 (예약/결제 관련)
     */
    fun cleanupReservationData(jdbcTemplate: JdbcTemplate) {
        try {
            jdbcTemplate.execute("DELETE FROM payment")
            jdbcTemplate.execute("DELETE FROM reservation")
        } catch (e: Exception) {
            // 테스트 환경에서 DB 정리 실패는 무시
        }
    }
    
    /**
     * 시퀀스 초기화 (필요한 경우에만 사용)
     */
    fun resetSequences(jdbcTemplate: JdbcTemplate) {
        try {
            jdbcTemplate.execute("ALTER SEQUENCE users_user_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE concert_concert_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE concert_schedule_schedule_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE seat_seat_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE reservation_reservation_id_seq RESTART WITH 1")
            jdbcTemplate.execute("ALTER SEQUENCE payment_payment_id_seq RESTART WITH 1")
        } catch (e: Exception) {
            // 시퀀스 초기화 실패는 무시
        }
    }
    
    /**
     * Redis + DB 전체 정리 (일반적인 사용)
     */
    fun cleanupAll(redisTemplate: RedisTemplate<*, *>, jdbcTemplate: JdbcTemplate) {
        cleanupRedis(redisTemplate)
        cleanupDatabase(jdbcTemplate)
    }
    
    /**
     * Redis + DB 전체 정리 + 시퀀스 초기화 (필요한 경우)
     */
    fun cleanupAllWithSequenceReset(redisTemplate: RedisTemplate<*, *>, jdbcTemplate: JdbcTemplate) {
        cleanupRedis(redisTemplate)
        cleanupDatabase(jdbcTemplate)
        resetSequences(jdbcTemplate)
    }
    
    /**
     * 전체 테스트 환경 초기화 (캐시 포함)
     */
    fun cleanupCompleteTestEnvironment(
        context: ApplicationContext,
        redisTemplate: RedisTemplate<*, *>,
        jdbcTemplate: JdbcTemplate
    ) {
        cleanupRedis(redisTemplate)
        cleanupCacheManager(context)
        cleanupDatabase(jdbcTemplate)
        resetSequences(jdbcTemplate)
    }
}