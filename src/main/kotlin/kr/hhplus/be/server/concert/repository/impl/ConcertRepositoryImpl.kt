package kr.hhplus.be.server.concert.repository.impl

import kr.hhplus.be.server.concert.entity.Concert
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.concert.repository.ConcertRepository
import org.springframework.stereotype.Repository

@Repository
class ConcertRepositoryImpl(
    private val concertRepository: ConcertJpaRepository
) : ConcertRepository {
    
    override fun save(concert: Concert): Concert {
        return concertRepository.save(concert)
    }
    
    override fun findById(id: Long): Concert? {
        return concertRepository.findById(id).orElse(null)
    }
    
    override fun findByIsActiveTrue(): List<Concert> {
        return concertRepository.findByIsActiveTrue()
    }
    
    override fun findAll(): List<Concert> {
        return concertRepository.findAll()
    }
    
    override fun delete(concert: Concert) {
        concertRepository.delete(concert)
    }
}
