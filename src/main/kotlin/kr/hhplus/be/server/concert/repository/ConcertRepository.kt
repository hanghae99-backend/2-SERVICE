package kr.hhplus.be.server.concert.repository

import kr.hhplus.be.server.concert.entity.Concert

interface ConcertRepository {
    fun save(concert: Concert): Concert
    fun findById(id: Long): Concert?
    fun findByIsActiveTrue(): List<Concert>
    fun findAll(): List<Concert>
    fun delete(concert: Concert)
}
