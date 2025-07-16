package kr.hhplus.be.server.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("API 문서")
                .description("콘서트 예약 서비스 API Swagger 문서")
                .version("v1.0.0")
        )

    @Bean
    fun publicApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("public")
        .pathsToMatch("/api/**")
        .build()
}