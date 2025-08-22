package kr.hhplus.be.server.global.config

import kr.hhplus.be.server.domain.common.ConcertBusinessRules
import kr.hhplus.be.server.domain.common.ReservationBusinessRules
import kr.hhplus.be.server.global.properties.ConcertProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ConcertProperties::class)
class BusinessRulesConfig {
    
    @Bean
    fun reservationBusinessRules(concertProperties: ConcertProperties): ReservationBusinessRules {
        return ReservationBusinessRules(concertProperties)
    }
    
    @Bean
    fun concertBusinessRules(concertProperties: ConcertProperties): ConcertBusinessRules {
        return ConcertBusinessRules(concertProperties)
    }
}
