package kr.hhplus.be.server.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class SwaggerConfig {
    
    @Value("\${app.version:1.0.0}")
    private lateinit var appVersion: String
    
    @Value("\${server.port:8080}")
    private lateinit var serverPort: String
    
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("콘서트 예약 서비스 API")
                    .version(appVersion)
                    .description("""
                        콘서트 예약 서비스 API 문서입니다.
                        
                        ## 주요 기능
                        - 대기열 토큰 관리
                        - 콘서트 예약 및 결제
                        - 포인트 충전 및 관리
                        - 사용자 관리
                        
                        ## 인증
                        - 대부분의 API는 대기열 토큰이 필요합니다.
                        - `/api/v1/tokens` 에서 토큰을 발급받으세요.
                    """.trimIndent())
                    .contact(
                        Contact()
                            .name("개발팀")
                            .email("dev@hhplus.kr")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .servers(getServers())
            .addSecurityItem(SecurityRequirement().addList("TokenAuth"))
            .components(
                io.swagger.v3.oas.models.Components()
                    .addSecuritySchemes("TokenAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .name("X-Queue-Token")
                            .description("대기열 토큰")
                    )
            )
    }

    @Bean
    @Profile("!prod")
    fun testApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("test-api")
            .pathsToMatch("/api/test/**")
            .displayName("테스트 API")
            .build()
    }

    @Bean
    fun publicApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("public-api")
            .pathsToMatch("/api/v1/**")
            .pathsToExclude("/api/test/**")
            .displayName("공개 API")
            .build()
    }

    @Bean
    fun adminApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("admin-api")
            .pathsToMatch("/api/admin/**")
            .displayName("관리자 API")
            .build()
    }

    private fun getServers(): List<Server> {
        return listOf(
            Server()
                .url("http://localhost:$serverPort")
                .description("로컬 개발 서버"),
            Server()
                .url("https://api-dev.hhplus.kr")
                .description("개발 서버"),
            Server()
                .url("https://api-staging.hhplus.kr")
                .description("스테이징 서버"),
            Server()
                .url("https://api.hhplus.kr")
                .description("프로덕션 서버")
        )
    }
}
