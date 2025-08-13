package kr.hhplus.be.server.config

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.context.annotation.Import

/**
 * 모든 통합/동시성 테스트의 공통 베이스 어노테이션
 * Redisson 없이 Redis만 사용하는 테스트 설정
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(MockTestConfiguration::class)
annotation class IntegrationTest
