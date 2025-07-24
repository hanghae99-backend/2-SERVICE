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
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    // Private MutableList for internal JPA management
    @OneToMany(mappedBy = "concert", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    private var _schedules: MutableList<ConcertSchedule> = mutableListOf()
    
    @OneToMany(mappedBy = "concert", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    private var _seats: MutableList<Seat> = mutableListOf()
    
    @OneToMany(mappedBy = "concert", fetch = FetchType.LAZY)
    private var _reservations: MutableList<Reservation> = mutableListOf()
    
    // Public read-only access
    val schedules: List<ConcertSchedule>
        get() = _schedules.toList()
    
    val seats: List<Seat>
        get() = _seats.toList()
    
    val reservations: List<Reservation>
        get() = _reservations.toList()
    
    // Business methods for managing relationships
    fun addSchedule(schedule: ConcertSchedule) {
        if (!_schedules.contains(schedule)) {
            _schedules.add(schedule)
        }
    }
    
    fun removeSchedule(schedule: ConcertSchedule) {
        _schedules.remove(schedule)
    }
    
    fun addSeat(seat: Seat) {
        if (!_seats.contains(seat)) {
            _seats.add(seat)
        }
    }
    
    fun removeSeat(seat: Seat) {
        _seats.remove(seat)
    }
    
    fun addReservation(reservation: Reservation) {
        if (!_reservations.contains(reservation)) {
            _reservations.add(reservation)
        }
    }
    
    // Internal access for JPA (if needed)
    internal fun getInternalSchedules() = _schedules
    internal fun getInternalSeats() = _seats
    internal fun getInternalReservations() = _reservations
    
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
