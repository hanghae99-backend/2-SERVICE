package kr.hhplus.be.server.domain.concert.models

import kr.hhplus.be.server.global.common.BaseEntity
import kr.hhplus.be.server.global.exception.ParameterValidationException
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*

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
    var isActive: Boolean = true,
    
    @Column(name = "description", nullable = true, length = 1000)
    var description: String? = null,
    
    @Column(name = "genre", nullable = true, length = 50)
    var genre: String? = null,
    
    @Column(name = "venue", nullable = true, length = 200)
    var venue: String? = null
) : BaseEntity() {
    
    companion object {
        fun create(title: String, artist: String, description: String? = null, genre: String? = null, venue: String? = null): Concert {
            validateCreateParameters(title, artist)
            
            return Concert(
                title = title.trim(),
                artist = artist.trim(),
                description = description?.trim(),
                genre = genre?.trim(),
                venue = venue?.trim()
            )
        }
        
        private fun validateCreateParameters(title: String, artist: String) {
            if (title.isBlank()) {
                throw ParameterValidationException("콘서트 제목은 필수입니다")
            }
            if (artist.isBlank()) {
                throw ParameterValidationException("아티스트 이름은 필수입니다")
            }
            if (title.length > 200) {
                throw ParameterValidationException("콘서트 제목은 200자를 초과할 수 없습니다")
            }
            if (artist.length > 100) {
                throw ParameterValidationException("아티스트 이름은 100자를 초과할 수 없습니다")
            }
        }
    }
    
    fun updateInfo(title: String?, artist: String?, description: String?, genre: String?, venue: String?) {
        title?.let {
            if (it.isNotBlank() && it.length <= 200) {
                this.title = it.trim()
            }
        }
        artist?.let {
            if (it.isNotBlank() && it.length <= 100) {
                this.artist = it.trim()
            }
        }
        description?.let { this.description = it.trim().takeIf { text -> text.isNotBlank() } }
        genre?.let { this.genre = it.trim().takeIf { text -> text.isNotBlank() } }
        venue?.let { this.venue = it.trim().takeIf { text -> text.isNotBlank() } }
    }
    
    fun activate() {
        this.isActive = true
    }
    
    fun deactivate() {
        this.isActive = false
    }
    
    fun canBeBooked(): Boolean = isActive
    
    fun getDisplayName(): String = "$title by $artist"
}
