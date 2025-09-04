package kr.hhplus.be.server.config

import kr.hhplus.be.server.global.client.BalanceApiClient
import kr.hhplus.be.server.global.client.ReservationApiClient
import kr.hhplus.be.server.global.client.ConcertDataPlatformClient
import kr.hhplus.be.server.global.client.SeatApiClient
import kr.hhplus.be.server.config.mock.MockBalanceApiClient
import kr.hhplus.be.server.config.mock.MockReservationApiClient
import kr.hhplus.be.server.config.mock.MockConcertDataPlatformClient
import kr.hhplus.be.server.config.mock.MockSeatApiClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Mock 기반 테스트용 설정
 * 외부 API 클라이언트들을 Mock으로 대체
 */
@TestConfiguration
class MockTestConfiguration {
    
    @Bean
    @Primary
    fun mockBalanceApiClient(): BalanceApiClient {
        return MockBalanceApiClient()
    }
    
    @Bean
    @Primary
    fun mockReservationApiClient(): ReservationApiClient {
        return MockReservationApiClient()
    }
    
    @Bean
    @Primary
    fun mockConcertDataPlatformClient(): ConcertDataPlatformClient {
        return MockConcertDataPlatformClient()
    }
    
    @Bean
    @Primary
    fun mockSeatApiClient(): SeatApiClient {
        return MockSeatApiClient()
    }
}
