package kr.hhplus.be.server.domain.concert.models

import kr.hhplus.be.server.global.common.BaseEntity
import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.domain.reservation.model.Reservation
import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

@Entity
@Table(
    name = "concert",
    indexes = [
        Index(name = "idx_concert_active_title_artist", columnList = "is_active, title, artist"),
        Index(name = "idx_concert_is_active", columnList = "is_active")
    ]
)
class Concert(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var concertId: Long = 0,
    
    @Column(name = "title", nullable = false, length = 200)
    var title: String,
    
    @Column(name = "artist", nullable = false, length = 100)
    var artist: String,
    
    @JsonProperty("isActive")
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) : BaseEntity() {
    
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
