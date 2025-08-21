package kr.hhplus.be.server.test.utils

import org.springframework.data.redis.core.RedisTemplate

/**
 * 테스트용 Redis 유틸리티 클래스
 * Deprecated 메서드를 대체하는 헬퍼 함수들을 제공합니다.
 */
object TestRedisUtils {
    
    /**
     * Redis 데이터베이스를 플러시합니다.
     * deprecated flushAll() 메서드를 대체합니다.
     */
    fun flushDatabase(redisTemplate: RedisTemplate<*, *>) {
        try {
            redisTemplate.connectionFactory?.connection?.use { connection ->
                connection.serverCommands()?.flushDb()
            }
        } catch (e: Exception) {
            // 테스트 환경에서 Redis 초기화 실패는 무시
        }
    }
    
    /**
     * 특정 패턴의 키들을 삭제합니다.
     * deprecated keys() 및 del() 메서드를 대체합니다.
     */
    fun deleteKeysByPattern(redisTemplate: RedisTemplate<*, *>, pattern: String) {
        try {
            redisTemplate.connectionFactory?.connection?.use { connection ->
                val keys = connection.keyCommands()?.keys(pattern.toByteArray()) ?: emptySet()
                if (keys.isNotEmpty()) {
                    connection.keyCommands()?.unlink(*keys.toTypedArray())
                }
            }
        } catch (e: Exception) {
            // 테스트 환경에서 Redis 키 삭제 실패는 무시
        }
    }
    
    /**
     * 모든 캐시를 클리어합니다.
     */
    fun clearAllCaches(cacheManager: org.springframework.cache.CacheManager?) {
        try {
            cacheManager?.cacheNames?.forEach { cacheName ->
                cacheManager.getCache(cacheName)?.clear()
            }
        } catch (e: Exception) {
            // 캐시 클리어 실패는 무시
        }
    }
}
