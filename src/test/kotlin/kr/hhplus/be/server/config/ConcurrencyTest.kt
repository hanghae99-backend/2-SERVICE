package kr.hhplus.be.server.config

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import org.springframework.context.annotation.Import

/**
 * 모든 동시성 테스트의 공통 베이스 어노테이션
 * 메모리 기반 Mock을 사용하는 동시성 테스트 설정
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(MockTestConfiguration::class)
annotation class ConcurrencyTest
