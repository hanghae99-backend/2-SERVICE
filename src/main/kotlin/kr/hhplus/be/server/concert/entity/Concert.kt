package kr.hhplus.be.server.concert.entity

import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.reservation.entity.Reservation
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "concert")
class Concert(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val concertId: Long = 0,
    
    @Column(name = "title", nullable = false, length = 200)
    val title: String,
    
    @Column(name = "artist", nullable = false, length = 100)
    val artist: String,
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    companion object {
        fun create(title: String, artist: String): Concert {
            if (title.isBlank()) {
                throw ParameterValidationException("콘서트 제목은 필수입니다")
            }
            if (artist.isBlank()) {
                throw ParameterValidationException("아티스트 이름은 필수입니다")
            }
            
            return Concert(
                title = title,
                artist = artist
            )
        }
    }
}
