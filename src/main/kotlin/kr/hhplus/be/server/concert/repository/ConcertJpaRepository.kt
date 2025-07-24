package kr.hhplus.be.server.concert.repository

import kr.hhplus.be.server.concert.entity.Concert
import org.springframework.data.jpa.repository.JpaRepository

interface ConcertJpaRepository : JpaRepository<Concert, Long> {
    
    // 키워드로 제목이나 아티스트 검색 (Query Method)
    fun findByTitleContainingOrArtistContaining(title: String, artist: String): List<Concert>
    
    // 아티스트로 검색
    fun findByArtist(artist: String): List<Concert>
    
    // 제목으로 검색
    fun findByTitleContaining(title: String): List<Concert>
    
    // 아티스트 이름 포함 검색
    fun findByArtistContaining(artist: String): List<Concert>
    
    // 활성 상태 콘서트 조회
    fun findByIsActiveTrue(): List<Concert>
}
