package kr.hhplus.be.server.domain.concert.repositories

import kr.hhplus.be.server.domain.concert.models.Concert

interface ConcertRepository {
    fun save(concert: Concert): Concert
    fun findById(id: Long): Concert?
    fun findByIsActiveTrue(): List<Concert>
    fun findAll(): List<Concert>
    fun delete(concert: Concert)
}
