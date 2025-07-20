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
                    .title("콘서트 예약 서비스 API")
                    .version("1.0.0")
                    .description("콘서트 예약 서비스 API 문서입니다.")
            )
            .servers(
                listOf(
                    Server().url("http://localhost:8080").description("Development Server"),
                )
            )
    }


    @Bean
    fun publicApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("web-api")
            .pathsToMatch("/api/**")
            .build()
    }

    @Bean
    fun mockApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("mock-api")
            .pathsToMatch("/mock/**")
            .build()
    }
}
