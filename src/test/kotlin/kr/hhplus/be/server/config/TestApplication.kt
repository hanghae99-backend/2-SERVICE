package kr.hhplus.be.server.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * 테스트 전용 애플리케이션 설정
 * Redisson 자동설정을 제외한 Spring Boot 애플리케이션
 */
@SpringBootApplication(
    scanBasePackages = ["kr.hhplus.be.server"]
)
class TestApplication

fun main(args: Array<String>) {
    SpringApplication.run(TestApplication::class.java, *args)
}
