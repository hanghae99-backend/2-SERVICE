package kr.hhplus.be.server.config

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistoryType as DomainPointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.concert.models.SeatStatusType as DomainSeatStatusType
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType as DomainPaymentStatusType
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType as DomainReservationStatusType
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.user.repositories.UserRepository
import org.springframework.context.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 테스트 데이터 상수 정의
 */
object TestDataConstants {
    
    object Point {
        val DEFAULT_AMOUNT = BigDecimal("1000000")
        val PREMIUM_AMOUNT = BigDecimal("5000000")
        val SMALL_AMOUNT = BigDecimal("50000")
    }
    
    object Concert {
        const val DEFAULT_TITLE = "테스트 콘서트"
        const val DEFAULT_ARTIST = "테스트 아티스트"
    }
    
    object Schedule {
        const val DEFAULT_VENUE = "테스트 공연장"
        const val DEFAULT_DAYS_FROM_NOW = 30L
        const val DEFAULT_TOTAL_SEATS = 50
    }
    
    object Seat {
        val DEFAULT_PRICE = BigDecimal("50000")
        const val DEFAULT_SEAT_COUNT = 50
        const val DEFAULT_START_NUMBER = 1
        const val SEAT_PREFIX = "A"
    }
    
    object User {
        const val DEFAULT_USER_COUNT = 5
        const val DEFAULT_START_USER_ID = 1L
    }
    
    object PointHistoryType {
        val USE = PointHistoryTypeData("USE", "사용", "포인트 사용")
        val CHARGE = PointHistoryTypeData("CHARGE", "충전", "포인트 충전")
        val REFUND = PointHistoryTypeData("REFUND", "환불", "포인트 환불")
        val ALL_TYPES = listOf(USE, CHARGE, REFUND)
    }
    
    object SeatStatusType {
        val AVAILABLE = SeatStatusTypeData("AVAILABLE", "예약가능", "예약 가능한 좌석")
        val RESERVED = SeatStatusTypeData("RESERVED", "예약완료", "예약된 좌석")
        val OCCUPIED = SeatStatusTypeData("OCCUPIED", "점유완료", "결제 완료된 좌석")
        val ALL_TYPES = listOf(AVAILABLE, RESERVED, OCCUPIED)
    }
    
    object ReservationStatusType {
        val TEMPORARY = ReservationStatusTypeData("TEMPORARY", "임시예약", "임시 예약 상태")
        val CONFIRMED = ReservationStatusTypeData("CONFIRMED", "확정예약", "결제 완료된 확정 예약")
        val CANCELLED = ReservationStatusTypeData("CANCELLED", "취소됨", "취소된 예약")
        val ALL_TYPES = listOf(TEMPORARY, CONFIRMED, CANCELLED)
    }
    
    object PaymentStatusType {
        val PENDING = PaymentStatusTypeData("PENDING", "결제대기", "결제 처리 대기중")
        val COMPLETED = PaymentStatusTypeData("COMPLETED", "결제완료", "결제가 성공적으로 완료됨")
        val FAILED = PaymentStatusTypeData("FAILED", "결제실패", "결제가 실패함")
        val ALL_TYPES = listOf(PENDING, COMPLETED, FAILED)
    }
}

data class PointHistoryTypeData(val code: String, val name: String, val description: String)
data class SeatStatusTypeData(val code: String, val name: String, val description: String)
data class ReservationStatusTypeData(val code: String, val name: String, val description: String)
data class PaymentStatusTypeData(val code: String, val name: String, val description: String)

/**
 * 테스트 데이터 픽스처
 */
object TestDataFixture {
    
    /**
     * 기본 테스트 사용자 생성
     */
    fun createTestUser(
        context: ApplicationContext,
        userId: Long = TestDataConstants.User.DEFAULT_START_USER_ID,
        withPoints: Boolean = true,
        pointAmount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT
    ): User {
        val userRepository = context.getBean(UserRepository::class.java)
        val user = userRepository.save(User(userId = userId))
        userRepository.flush()
        
        if (withPoints) {
            createTestPoints(context, user.userId, pointAmount)
        }
        
        return user
    }
    
    /**
     * 테스트 포인트 생성 (DB 저장)
     */
    fun createTestPoints(
        context: ApplicationContext,
        userId: Long,
        amount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT
    ): Point {
        val pointRepository = context.getBean(PointRepository::class.java)
        val point = pointRepository.save(Point.create(userId, amount))
        pointRepository.flush()
        return point
    }
    
    /**
     * 테스트 포인트 객체 생성 (단위 테스트용, DB 저장 없음)
     */
    fun createTestPoint(
        userId: Long,
        amount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT
    ): Point {
        return Point.create(userId, amount)
    }
    
    /**
     * 테스트 사용자 객체 생성 (단위 테스트용, DB 저장 없음)
     */
    fun createTestUserObject(userId: Long): User {
        return User.create(userId)
    }
    
    /**
     * 테스트용 포인트 히스토리 타입 생성 (단일)
     */
    fun createPointHistoryType(
        code: String = TestDataConstants.PointHistoryType.USE.code,
        name: String = TestDataConstants.PointHistoryType.USE.name,
        description: String = TestDataConstants.PointHistoryType.USE.description
    ): DomainPointHistoryType {
        return DomainPointHistoryType(
            code = code,
            name = name,
            description = description
        )
    }
    
    /**
     * 기본 테스트 콘서트 생성
     */
    fun createTestConcert(
        context: ApplicationContext,
        title: String = TestDataConstants.Concert.DEFAULT_TITLE,
        artist: String = TestDataConstants.Concert.DEFAULT_ARTIST
    ): Concert {
        val concertRepository = context.getBean(ConcertRepository::class.java)
        val concert = concertRepository.save(
            Concert.create(title = title, artist = artist)
        )
        concertRepository.flush()
        return concert
    }
    
    /**
     * 테스트 콘서트 스케줄 생성
     */
    fun createTestConcertSchedule(
        context: ApplicationContext,
        concertId: Long,
        daysFromNow: Long = TestDataConstants.Schedule.DEFAULT_DAYS_FROM_NOW,
        venue: String = TestDataConstants.Schedule.DEFAULT_VENUE,
        totalSeats: Int = TestDataConstants.Schedule.DEFAULT_TOTAL_SEATS
    ): ConcertSchedule {
        val concertScheduleRepository = context.getBean(ConcertScheduleRepository::class.java)
        val schedule = concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = concertId,
                concertDate = LocalDate.now().plusDays(daysFromNow),
                venue = venue,
                totalSeats = totalSeats
            )
        )
        concertScheduleRepository.flush()
        return schedule
    }
    
    /**
     * 테스트 좌석들 생성 (DB 저장)
     */
    fun createTestSeats(
        context: ApplicationContext,
        scheduleId: Long,
        seatCount: Int = TestDataConstants.Seat.DEFAULT_SEAT_COUNT,
        startNumber: Int = TestDataConstants.Seat.DEFAULT_START_NUMBER,
        price: BigDecimal = TestDataConstants.Seat.DEFAULT_PRICE
    ): List<Seat> {
        val seatRepository = context.getBean(SeatRepository::class.java)
        val seatStatusTypeRepository = context.getBean(SeatStatusTypePojoRepository::class.java)
        
        // AVAILABLE 상태 타입 생성 또는 조회
        val availableStatus = getOrCreateSeatStatusType(
            seatStatusTypeRepository,
            TestDataConstants.SeatStatusType.AVAILABLE
        )
        
        val seats = (startNumber until startNumber + seatCount).map { seatNum ->
            seatRepository.save(
                Seat.create(
                    scheduleId = scheduleId,
                    seatNumber = "${TestDataConstants.Seat.SEAT_PREFIX}$seatNum",
                    price = price,
                    availableStatus = availableStatus
                )
            )
        }
        seatRepository.flush()
        return seats
    }
    
    /**
     * 테스트용 예약 상태 타입 생성
     */
    fun createReservationStatusType(
        code: String = TestDataConstants.ReservationStatusType.TEMPORARY.code,
        name: String = TestDataConstants.ReservationStatusType.TEMPORARY.name,
        description: String = TestDataConstants.ReservationStatusType.TEMPORARY.description
    ): DomainReservationStatusType {
        return DomainReservationStatusType(
            code = code,
            name = name,
            description = description
        )
    }
    
    /**
     * 테스트용 좌석 상태 타입 생성
     */
    fun createSeatStatusType(
        code: String = TestDataConstants.SeatStatusType.AVAILABLE.code,
        name: String = TestDataConstants.SeatStatusType.AVAILABLE.name,
        description: String = TestDataConstants.SeatStatusType.AVAILABLE.description
    ): DomainSeatStatusType {
        return DomainSeatStatusType(
            code = code,
            name = name,
            description = description
        )
    }
    
    /**
     * 테스트용 결제 상태 타입 생성
     */
    fun createPaymentStatusType(
        code: String = TestDataConstants.PaymentStatusType.PENDING.code,
        name: String = TestDataConstants.PaymentStatusType.PENDING.name,
        description: String = TestDataConstants.PaymentStatusType.PENDING.description
    ): DomainPaymentStatusType {
        return DomainPaymentStatusType(
            code = code,
            name = name,
            description = description
        )
    }
    
    /**
     * 좌석 상태 타입 생성 또는 조회
     */
    private fun getOrCreateSeatStatusType(
        repository: SeatStatusTypePojoRepository,
        typeData: SeatStatusTypeData
    ): DomainSeatStatusType {
        val existing = try {
            repository.findByCode(typeData.code)
        } catch (e: Exception) {
            null
        }
        
        return existing ?: repository.save(
            DomainSeatStatusType(
                code = typeData.code,
                name = typeData.name,
                description = typeData.description
            )
        )
    }
}
