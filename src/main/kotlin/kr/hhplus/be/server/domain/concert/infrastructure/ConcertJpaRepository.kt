package kr.hhplus.be.server.domain.concert.infrastructure

import kr.hhplus.be.server.domain.concert.models.Concert
import org.springframework.data.jpa.repository.JpaRepository

interface ConcertJpaRepository : JpaRepository<Concert, Long> {
    
    // 활성 상태 콘서트 조회
    fun findByIsActiveTrue(): List<Concert>
}
