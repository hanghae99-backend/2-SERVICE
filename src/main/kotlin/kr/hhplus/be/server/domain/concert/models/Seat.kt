package kr.hhplus.be.server.domain.concert.models

import kr.hhplus.be.server.global.common.BaseEntity
import kr.hhplus.be.server.domain.concert.exception.InvalidSeatStatusException
import kr.hhplus.be.server.global.exception.ParameterValidationException
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "seat",
    indexes = [
        Index(name = "idx_seat_schedule_status_number", columnList = "schedule_id, status_code, seat_number"),
        Index(name = "idx_seat_schedule_price", columnList = "schedule_id, price")
    ]
)
class Seat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var seatId: Long = 0,

    @Column(name = "schedule_id", nullable = false)
    var scheduleId: Long,

    @Column(name = "seat_number", nullable = false, length = 10)
    var seatNumber: String,

    @Column(name = "seat_grade", nullable = false, length = 20)
    var seatGrade: String = "STANDARD",

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    var price: BigDecimal,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code")
    var status: SeatStatusType,

    ) : BaseEntity() {

    companion object {
        const val STATUS_AVAILABLE = "AVAILABLE"
        const val STATUS_RESERVED = "RESERVED"
        const val STATUS_OCCUPIED = "OCCUPIED"
        const val STATUS_MAINTENANCE = "MAINTENANCE"

        fun create(scheduleId: Long, seatNumber: String, price: BigDecimal, availableStatus: SeatStatusType): Seat {
            if (scheduleId <= 0) throw ParameterValidationException("스케줄 ID는 0보다 커야 합니다: $scheduleId")
            if (seatNumber.isBlank()) throw ParameterValidationException("좌석 번호는 필수입니다")
            if (price <= BigDecimal.ZERO) throw ParameterValidationException("좌석 가격은 0보다 커야 합니다: $price")

            return Seat(
                scheduleId = scheduleId,
                seatNumber = seatNumber,
                price = price,
                status = availableStatus
            )
        }
    }

    fun reserve(reservedStatus: SeatStatusType) {
        validateCanReserve()
        this.status = reservedStatus
    }

    fun confirm(occupiedStatus: SeatStatusType) {
        validateCanConfirm()
        this.status = occupiedStatus
    }

    fun release(availableStatus: SeatStatusType) {
        validateCanRelease()
        this.status = availableStatus
    }

    fun setMaintenance(maintenanceStatus: SeatStatusType) {
        this.status = maintenanceStatus
    }

    private fun validateCanReserve() {
        if (!canBeReserved()) {
            throw InvalidSeatStatusException("예약 불가능한 좌석입니다: $seatNumber, 현재 상태: ${status.code}")
        }
    }

    private fun validateCanConfirm() {
        if (!isReserved()) {
            throw InvalidSeatStatusException("예약된 좌석이 아닙니다: $seatNumber, 현재 상태: ${status.code}")
        }
    }

    private fun validateCanRelease() {
        if (!isReserved()) {
            throw InvalidSeatStatusException("예약된 좌석이 아닙니다: $seatNumber, 현재 상태: ${status.code}")
        }
    }

    fun isAvailable(): Boolean = status.code == STATUS_AVAILABLE
    fun isReserved(): Boolean = status.code == STATUS_RESERVED
    fun isOccupied(): Boolean = status.code == STATUS_OCCUPIED
    fun canBeReserved(): Boolean = status.code == STATUS_AVAILABLE

    val statusName: String
        get() = status.name
}
