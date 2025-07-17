package kr.hhplus.be.server.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("HH Plus 콘서트 예약 서비스 API")
                    .version("1.0.0")
                    .description("""
                        **콘서트 예약 서비스 API 문서**
                        
                        ## 주요 기능
                        - 대기열 기반 토큰 시스템
                        - 콘서트 좌석 예약 (1~50번)
                        - 포인트 기반 결제 시스템
                        - 5분간 임시 예약 지원
                        
                        ## API 구성
                        좌측 드롭다운에서 원하는 API 그룹을 선택하세요:
                        
                        ### 🚀 Production API
                        실제 서비스 운영용 API (구현 예정)
                        
                        ### 🧪 Mock API  
                        개발/테스트용 시뮬레이션 API (즉시 사용 가능)
                        
                        ### 기능별 API
                        - 🔐 대기열 토큰: 서비스 접근 제어
                        - 🎵 콘서트 예약: 날짜/좌석 조회 및 예약
                        - 💰 포인트 잔액: 충전/조회/이력 관리
                        - 💳 결제: 예약 결제 및 내역 관리
                        
                        ## 인증
                        모든 예약/결제 API는 활성화된 대기열 토큰이 필요합니다.
                    """.trimIndent())
            )
            .servers(
                listOf(
                    Server().url("http://localhost:8080").description("Development Server"),
                    Server().url("https://api.hhplus-concert.com").description("Production Server")
                )
            )
    }
    
    // === 환경별 API 그룹 ===
    
    @Bean
    fun productionApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("production")
            .displayName("🚀 Production API")
            .pathsToMatch("/api/v1/**")
            .build()
    }
    
    @Bean
    fun mockApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("mock")
            .displayName("🧪 Mock API (개발/테스트용)")
            .pathsToMatch("/mock/**")
            .build()
    }
    
    // === 기능별 API 그룹 ===
    
    @Bean
    fun authApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("auth")
            .displayName("🔐 대기열 토큰 API")
            .pathsToMatch("/api/v1/queue/**", "/mock/queue/**")
            .build()
    }
    
    @Bean
    fun concertApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("concert")
            .displayName("🎵 콘서트 예약 API")
            .pathsToMatch("/api/v1/concerts/**", "/mock/concerts/**")
            .build()
    }
    
    @Bean
    fun balanceApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("balance")
            .displayName("💰 포인트 잔액 API")
            .pathsToMatch("/api/v1/balance/**", "/mock/balance/**")
            .build()
    }
    
    @Bean
    fun paymentApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("payment")
            .displayName("💳 결제 API")
            .pathsToMatch("/api/v1/payments/**", "/mock/payments/**")
            .build()
    }
    
    // === 완전 분리된 페이지 ===
    
    @Bean
    fun productionOnlyApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("prod-only")
            .displayName("📋 Production Only")
            .pathsToMatch("/api/v1/**")
            .pathsToExclude("/mock/**")
            .build()
    }
    
    @Bean
    fun mockOnlyApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("mock-only")
            .displayName("🔧 Mock Only")
            .pathsToMatch("/mock/**")
            .pathsToExclude("/api/v1/**")
            .build()
    }
}
