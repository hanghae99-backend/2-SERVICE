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
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 테스트 데이터 상수 정의
 * 모든 테스트에서 사용하는 기본값들을 중앙 집중 관리
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
        const val INTEGRATION_TITLE = "통합 테스트 콘서트"
        const val COMPLETE_TITLE = "완전 테스트 환경"
        const val PAYMENT_TITLE = "결제 테스트 콘서트"
        const val RESERVATION_TITLE = "예약 테스트 콘서트"
    }
    
    object Schedule {
        const val DEFAULT_VENUE = "테스트 공연장"
        const val DEFAULT_DAYS_FROM_NOW = 30L
        const val DEFAULT_TOTAL_SEATS = 50
    }
    
    object Seat {
        val DEFAULT_PRICE = BigDecimal("50000")
        val PREMIUM_PRICE = BigDecimal("100000")
        val VIP_PRICE = BigDecimal("200000")
        const val DEFAULT_SEAT_COUNT = 50
        const val DEFAULT_START_NUMBER = 1
        const val SEAT_PREFIX = "A"
    }
    
    object User {
        const val DEFAULT_USER_COUNT = 5
        const val DEFAULT_START_USER_ID = 1L
    }
    
    object PointHistoryType {
        val USE = PointHistoryTypeData(
            DomainPointHistoryType.USE, 
            "사용", 
            "포인트 사용"
        )
        val CHARGE = PointHistoryTypeData(
            DomainPointHistoryType.CHARGE, 
            "충전", 
            "포인트 충전"
        )
        val REFUND = PointHistoryTypeData(
            DomainPointHistoryType.REFUND, 
            "환불", 
            "포인트 환불"
        )
        
        val ALL_TYPES = listOf(USE, CHARGE, REFUND)
    }
    
    object SeatStatusType {
        val AVAILABLE = SeatStatusTypeData(
            DomainSeatStatusType.AVAILABLE, 
            "예약가능", 
            "예약 가능한 좌석"
        )
        val RESERVED = SeatStatusTypeData(
            DomainSeatStatusType.RESERVED, 
            "예약완료", 
            "예약된 좌석"
        )
        val OCCUPIED = SeatStatusTypeData(
            DomainSeatStatusType.OCCUPIED, 
            "점유완료", 
            "결제 완료된 좌석"
        )
        
        val ALL_TYPES = listOf(AVAILABLE, RESERVED, OCCUPIED)
    }
    
    object ReservationStatusType {
        val TEMPORARY = ReservationStatusTypeData(
            DomainReservationStatusType.TEMPORARY,
            "임시예약",
            "임시 예약 상태"
        )
        val CONFIRMED = ReservationStatusTypeData(
            DomainReservationStatusType.CONFIRMED,
            "확정예약",
            "결제 완료된 확정 예약"
        )
        val CANCELLED = ReservationStatusTypeData(
            DomainReservationStatusType.CANCELLED,
            "취소됨",
            "취소된 예약"
        )
        
        val ALL_TYPES = listOf(TEMPORARY, CONFIRMED, CANCELLED)
    }
    
    object PaymentStatusType {
        val PENDING = PaymentStatusTypeData(
            DomainPaymentStatusType.PENDING,
            "결제대기",
            "결제 처리 대기중"
        )
        val COMPLETED = PaymentStatusTypeData(
            DomainPaymentStatusType.COMPLETED,
            "결제완료",
            "결제가 성공적으로 완료됨"
        )
        val FAILED = PaymentStatusTypeData(
            DomainPaymentStatusType.FAILED,
            "결제실패",
            "결제가 실패함"
        )
        
        val ALL_TYPES = listOf(PENDING, COMPLETED, FAILED)
    }
}

/**
 * 포인트 히스토리 타입 데이터
 */
data class PointHistoryTypeData(
    val code: String,
    val name: String,
    val description: String
)

/**
 * 좌석 상태 타입 데이터
 */
data class SeatStatusTypeData(
    val code: String,
    val name: String,
    val description: String
)

/**
 * 예약 상태 타입 데이터
 */
data class ReservationStatusTypeData(
    val code: String,
    val name: String,
    val description: String
)

/**
 * 결제 상태 타입 데이터
 */
data class PaymentStatusTypeData(
    val code: String,
    val name: String,
    val description: String
)

/**
 * 테스트 데이터 픽스처
 * 공통으로 사용되는 테스트 데이터 생성 로직을 제공
 * 개선 사항:
 * - 중복 메서드 제거
 * - 타입 안정성 강화
 * - Repository 추상화
 * - 성능 최적화
 */
object TestDataFixture {
    
    // Repository 헬퍼 클래스 - 레이지 로딩으로 성능 최적화
    private class RepositoryHelper(private val context: ApplicationContext) {
        val userRepository: UserRepository by lazy { context.getBean(UserRepository::class.java) }
        val pointRepository: PointRepository by lazy { context.getBean(PointRepository::class.java) }
        val concertRepository: ConcertRepository by lazy { context.getBean(ConcertRepository::class.java) }
        val concertScheduleRepository: ConcertScheduleRepository by lazy { context.getBean(ConcertScheduleRepository::class.java) }
        val seatRepository: SeatRepository by lazy { context.getBean(SeatRepository::class.java) }
        val pointHistoryTypeRepository: PointHistoryTypePojoRepository by lazy { context.getBean(PointHistoryTypePojoRepository::class.java) }
        val seatStatusTypeRepository: SeatStatusTypePojoRepository by lazy { context.getBean(SeatStatusTypePojoRepository::class.java) }
        val reservationStatusTypeRepository: ReservationStatusTypePojoRepository by lazy { context.getBean(ReservationStatusTypePojoRepository::class.java) }
        val paymentStatusTypeRepository: PaymentStatusTypePojoRepository by lazy { context.getBean(PaymentStatusTypePojoRepository::class.java) }
    }
    
    // 상태 타입 캐시 - 메모리 사용량 최적화
    private val statusTypeCache = mutableMapOf<String, Any>()
    
    private fun getRepositories(context: ApplicationContext) = RepositoryHelper(context)
    
    /**
     * 기본 테스트 사용자 생성
     */
    fun createTestUser(
        context: ApplicationContext,
        userId: Long = TestDataConstants.User.DEFAULT_START_USER_ID,
        withPoints: Boolean = true,
        pointAmount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT
    ): User {
        val repos = getRepositories(context)
        val user = repos.userRepository.save(User(userId = userId))
        repos.userRepository.flush()
        
        if (withPoints) {
            createTestPoints(context, user.userId, pointAmount)
        }
        
        return user
    }
    
    /**
     * 여러 테스트 사용자 생성
     */
    fun createTestUsers(
        context: ApplicationContext,
        count: Int = TestDataConstants.User.DEFAULT_USER_COUNT,
        startUserId: Long = TestDataConstants.User.DEFAULT_START_USER_ID,
        withPoints: Boolean = true,
        pointAmount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT
    ): List<User> {
        return (0 until count).map { index ->
            createTestUser(
                context = context,
                userId = startUserId + index,
                withPoints = withPoints,
                pointAmount = pointAmount
            )
        }
    }
    
    /**
     * 테스트 포인트 생성 (DB 저장)
     */
    fun createTestPoints(
        context: ApplicationContext,
        userId: Long,
        amount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT
    ): Point {
        val repos = getRepositories(context)
        val point = repos.pointRepository.save(Point.create(userId, amount))
        repos.pointRepository.flush()
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
     * 테스트 좌석들 생성 (DB 저장)
     */
    fun createTestSeats(
        context: ApplicationContext,
        scheduleId: Long,
        seatCount: Int = TestDataConstants.Seat.DEFAULT_SEAT_COUNT,
        startNumber: Int = TestDataConstants.Seat.DEFAULT_START_NUMBER,
        price: BigDecimal = TestDataConstants.Seat.DEFAULT_PRICE
    ): List<Seat> {
        val repos = getRepositories(context)
        
        // AVAILABLE 상태 타입 생성 또는 조회
        val availableStatus = getOrCreateSeatStatusType(
            repos.seatStatusTypeRepository,
            TestDataConstants.SeatStatusType.AVAILABLE
        )
        
        val seats = (startNumber until startNumber + seatCount).map { seatNum ->
            repos.seatRepository.save(
                Seat.create(
                    scheduleId = scheduleId,
                    seatNumber = "${TestDataConstants.Seat.SEAT_PREFIX}$seatNum",
                    price = price,
                    availableStatus = availableStatus
                )
            )
        }
        repos.seatRepository.flush()
        return seats
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
     * 포인트 히스토리 타입 생성
     */
    fun createPointHistoryTypes(context: ApplicationContext) {
        val repos = getRepositories(context)
        createStatusTypesIfNotExists(
            repository = repos.pointHistoryTypeRepository,
            typeDataList = TestDataConstants.PointHistoryType.ALL_TYPES,
            entityCreator = { typeData ->
                DomainPointHistoryType(
                    code = typeData.code,
                    name = typeData.name,
                    description = typeData.description
                )
            }
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
        val repos = getRepositories(context)
        val concert = repos.concertRepository.save(
            Concert.create(
                title = title,
                artist = artist
            )
        )
        repos.concertRepository.flush()
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
        val repos = getRepositories(context)
        val schedule = repos.concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = concertId,
                concertDate = LocalDate.now().plusDays(daysFromNow),
                venue = venue,
                totalSeats = totalSeats
            )
        )
        repos.concertScheduleRepository.flush()
        return schedule
    }
    
    
    /**
     * 완전한 테스트 환경 구성 (콘서트 + 스케줄 + 좌석)
     */
    fun createFullConcertEnvironment(
        context: ApplicationContext,
        concertTitle: String = TestDataConstants.Concert.INTEGRATION_TITLE,
        seatCount: Int = TestDataConstants.Seat.DEFAULT_SEAT_COUNT,
        daysFromNow: Long = TestDataConstants.Schedule.DEFAULT_DAYS_FROM_NOW
    ): ConcertTestEnvironment {
        val concert = createTestConcert(context, concertTitle)
        val schedule = createTestConcertSchedule(
            context = context,
            concertId = concert.concertId,
            daysFromNow = daysFromNow,
            totalSeats = seatCount
        )
        val seats = createTestSeats(
            context = context,
            scheduleId = schedule.scheduleId,
            seatCount = seatCount
        )
        
        return ConcertTestEnvironment(
            concert = concert,
            schedule = schedule,
            seats = seats
        )
    }
    
    /**
     * 완전한 테스트 환경 구성 (사용자 + 콘서트 환경)
     */
    fun createCompleteTestEnvironment(
        context: ApplicationContext,
        userCount: Int = 1,
        concertTitle: String = TestDataConstants.Concert.COMPLETE_TITLE,
        seatCount: Int = TestDataConstants.Seat.DEFAULT_SEAT_COUNT
    ): CompleteTestEnvironment {
        createAllStatusTypes(context)
        
        val users = if (userCount == 1) {
            listOf(createTestUser(context))
        } else {
            createTestUsers(context, userCount)
        }
        
        val concertEnv = createFullConcertEnvironment(
            context = context,
            concertTitle = concertTitle,
            seatCount = seatCount
        )
        
        return CompleteTestEnvironment(
            users = users,
            concertEnvironment = concertEnv
        )
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
     * 상태 타입 생성 또는 조회 (타입 안전)
     */
    private fun <T> createStatusTypesIfNotExists(
        repository: Any,
        typeDataList: List<T>,
        entityCreator: (T) -> Any
    ) {
        try {
            @Suppress("UNCHECKED_CAST")
            val jpaRepository = repository as JpaRepository<Any, String>
            typeDataList.forEach { typeData ->
                jpaRepository.save(entityCreator(typeData))
            }
            jpaRepository.flush()
        } catch (e: Exception) {
            // 이미 존재하는 경우 무시
        }
    }
    
    /**
     * 좌석 상태 타입 생성 또는 조회 (캐시 사용)
     */
    private fun getOrCreateSeatStatusType(
        repository: SeatStatusTypePojoRepository,
        typeData: SeatStatusTypeData
    ): DomainSeatStatusType {
        val cacheKey = "seat_${typeData.code}"
        return statusTypeCache.getOrPut(cacheKey) {
            try {
                repository.findByCode(typeData.code)
            } catch (e: Exception) {
                null
            } ?: repository.save(
                DomainSeatStatusType(
                    code = typeData.code,
                    name = typeData.name,
                    description = typeData.description
                )
            )
        } as DomainSeatStatusType
    }
    
    /**
     * 예약 상태 타입 생성 또는 조회 (캐시 사용)
     */
    private fun getOrCreateReservationStatusType(
        repository: ReservationStatusTypePojoRepository,
        typeData: ReservationStatusTypeData
    ): DomainReservationStatusType {
        val cacheKey = "reservation_${typeData.code}"
        return statusTypeCache.getOrPut(cacheKey) {
            try {
                repository.findByCode(typeData.code)
            } catch (e: Exception) {
                null
            } ?: repository.save(
                DomainReservationStatusType(
                    code = typeData.code,
                    name = typeData.name,
                    description = typeData.description
                )
            )
        } as DomainReservationStatusType
    }
    
    /**
     * 테스트용 예약 상태 타입을 DB에 저장
     */
    fun createAndSaveReservationStatusTypes(context: ApplicationContext) {
        val repos = getRepositories(context)
        createStatusTypesIfNotExists(
            repository = repos.reservationStatusTypeRepository,
            typeDataList = TestDataConstants.ReservationStatusType.ALL_TYPES,
            entityCreator = { typeData ->
                DomainReservationStatusType(
                    code = typeData.code,
                    name = typeData.name,
                    description = typeData.description
                )
            }
        )
    }
    
    /**
     * 테스트용 좌석 상태 타입을 DB에 저장
     */
    fun createAndSaveSeatStatusTypes(context: ApplicationContext) {
        val repos = getRepositories(context)
        createStatusTypesIfNotExists(
            repository = repos.seatStatusTypeRepository,
            typeDataList = TestDataConstants.SeatStatusType.ALL_TYPES,
            entityCreator = { typeData ->
                DomainSeatStatusType(
                    code = typeData.code,
                    name = typeData.name,
                    description = typeData.description
                )
            }
        )
    }
    
    /**
     * 테스트용 결제 상태 타입을 DB에 저장
     */
    fun createAndSavePaymentStatusTypes(context: ApplicationContext) {
        val repos = getRepositories(context)
        createStatusTypesIfNotExists(
            repository = repos.paymentStatusTypeRepository,
            typeDataList = TestDataConstants.PaymentStatusType.ALL_TYPES,
            entityCreator = { typeData ->
                DomainPaymentStatusType(
                    code = typeData.code,
                    name = typeData.name,
                    description = typeData.description
                )
            }
        )
    }
    
    /**
     * 모든 기본 상태 타입들을 DB에 저장
     */
    fun createAllStatusTypes(context: ApplicationContext) {
        createPointHistoryTypes(context)
        createAndSaveReservationStatusTypes(context)
        createAndSaveSeatStatusTypes(context)
        createAndSavePaymentStatusTypes(context)
    }
    
    /**
     * 좌석을 예약 상태로 변경하고 예약 생성
     */
    fun createReservationForSeat(
        context: ApplicationContext,
        userId: Long,
        concertId: Long,
        seat: Seat,
        tempMinutes: Long = 10
    ): kr.hhplus.be.server.domain.reservation.models.Reservation {
        val repos = getRepositories(context)
        val reservationRepository = context.getBean(
            kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository::class.java
        )
        
        // 좌석을 예약 상태로 변경
        val reservedStatus = getOrCreateSeatStatusType(
            repos.seatStatusTypeRepository,
            TestDataConstants.SeatStatusType.RESERVED
        )
        
        seat.reserve(reservedStatus)
        repos.seatRepository.save(seat)
        repos.seatRepository.flush()
        
        // 예약 생성
        val temporaryStatus = getOrCreateReservationStatusType(
            repos.reservationStatusTypeRepository,
            TestDataConstants.ReservationStatusType.TEMPORARY
        )
        
        val reservation = reservationRepository.save(
            kr.hhplus.be.server.domain.reservation.models.Reservation.createTemporary(
                userId = userId,
                concertId = concertId,
                seatId = seat.seatId,
                seatNumber = seat.seatNumber,
                price = seat.price,
                temporaryStatus = temporaryStatus,
                tempMinutes = tempMinutes
            )
        )
        reservationRepository.flush()
        
        return reservation
    }
    
    /**
     * 간단한 사용자 생성 (포인트 없이)
     */
    fun createSimpleUser(
        context: ApplicationContext,
        userId: Long = TestDataConstants.User.DEFAULT_START_USER_ID
    ): User {
        val repos = getRepositories(context)
        val user = repos.userRepository.save(User(userId = userId))
        repos.userRepository.flush()
        return user
    }
    
    /**
     * 토큰 생성 및 활성화
     */
    fun createAndActivateToken(
        context: ApplicationContext,
        userId: Long
    ): kr.hhplus.be.server.domain.auth.models.WaitingToken {
        val tokenFactory = context.getBean(kr.hhplus.be.server.domain.auth.factory.TokenFactory::class.java)
        val tokenStore = context.getBean(kr.hhplus.be.server.domain.auth.repositories.TokenStore::class.java)
        
        val token = tokenFactory.createWaitingToken(userId)
        tokenStore.save(token)
        tokenStore.activateToken(token.token)
        return token
    }
    
    /**
     * 간단한 다중 사용자 생성 (포인트 없이)
     */
    fun createSimpleUsers(
        context: ApplicationContext,
        userCount: Int,
        startUserId: Long = 1L
    ): List<User> {
        val repos = getRepositories(context)
        
        return (0 until userCount).map { index ->
            val userId = startUserId + index
            val user = repos.userRepository.save(User(userId = userId))
            repos.userRepository.flush()
            user
        }
    }
    
    /**
     * 동시성 테스트용 다중 사용자 생성 (포인트 포함)
     */
    fun createConcurrencyUsers(
        context: ApplicationContext,
        userCount: Int = 5,
        startUserId: Long = 1L,
        pointAmount: BigDecimal = BigDecimal("200000")
    ): List<User> {
        val repos = getRepositories(context)
        
        return (0 until userCount).map { index ->
            val userId = startUserId + index
            val user = repos.userRepository.save(User(userId = userId))
            repos.userRepository.flush()
            
            // 포인트도 함께 생성
            repos.pointRepository.save(Point.create(userId, pointAmount))
            repos.pointRepository.flush()
            
            user
        }
    }
    
    /**
     * 동시성 테스트용 다중 토큰 생성 및 활성화
     */
    fun createActiveTokensForUsers(
        context: ApplicationContext,
        users: List<User>
    ): List<kr.hhplus.be.server.domain.auth.models.WaitingToken> {
        val tokenFactory = context.getBean(kr.hhplus.be.server.domain.auth.factory.TokenFactory::class.java)
        val tokenStore = context.getBean(kr.hhplus.be.server.domain.auth.repositories.TokenStore::class.java)
        
        return users.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.save(token)
            tokenStore.activateToken(token.token)
            token
        }
    }
    
    /**
     * 동시성 테스트용 완전한 환경 구성 (사용자들, 콘서트, 토큰들)
     */
    fun createConcurrencyTestEnvironment(
        context: ApplicationContext,
        userCount: Int = 5,
        concertTitle: String = "동시성 테스트 콘서트",
        seatCount: Int = 50,
        userPointAmount: BigDecimal = BigDecimal("200000")
    ): ConcurrencyTestEnvironment {
        // 모든 상태 타입들 생성
        createAllStatusTypes(context)
        
        // 다중 사용자 생성 (포인트 포함)
        val users = createConcurrencyUsers(
            context = context,
            userCount = userCount,
            pointAmount = userPointAmount
        )
        
        // 콘서트 환경 생성
        val concertEnv = createFullConcertEnvironment(
            context = context,
            concertTitle = concertTitle,
            seatCount = seatCount
        )
        
        // 토큰들 생성 및 활성화
        val tokens = createActiveTokensForUsers(context, users)
        
        return ConcurrencyTestEnvironment(
            users = users,
            concertEnvironment = concertEnv,
            tokens = tokens
        )
    }
    
    /**
     * 각 사용자별로 개별 좌석과 예약 생성 (결제 테스트용)
     */
    fun createReservationsForUsers(
        context: ApplicationContext,
        users: List<User>,
        concertId: Long,
        scheduleId: Long,
        seatPrice: BigDecimal = BigDecimal("50000")
    ): List<kr.hhplus.be.server.domain.reservation.models.Reservation> {
        val repos = getRepositories(context)
        val reservationRepository = context.getBean(
            kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository::class.java
        )
        
        val availableStatus = getOrCreateSeatStatusType(
            repos.seatStatusTypeRepository,
            TestDataConstants.SeatStatusType.AVAILABLE
        )
        val reservedStatus = getOrCreateSeatStatusType(
            repos.seatStatusTypeRepository,
            TestDataConstants.SeatStatusType.RESERVED
        )
        val temporaryStatus = getOrCreateReservationStatusType(
            repos.reservationStatusTypeRepository,
            TestDataConstants.ReservationStatusType.TEMPORARY
        )
        
        return users.mapIndexed { index, user ->
            // 각 사용자별로 다른 좌석 생성
            val userSeat = repos.seatRepository.save(
                Seat.create(
                    scheduleId = scheduleId,
                    seatNumber = "A${index + 1}",
                    price = seatPrice,
                    availableStatus = availableStatus
                )
            )
            repos.seatRepository.flush()
            
            // 좌석 예약 처리 (상태를 RESERVED로 변경)
            userSeat.reserve(reservedStatus)
            repos.seatRepository.save(userSeat)
            repos.seatRepository.flush()
            
            // 예약 생성
            val reservation = reservationRepository.save(
                kr.hhplus.be.server.domain.reservation.models.Reservation.createTemporary(
                    userId = user.userId,
                    concertId = concertId,
                    seatId = userSeat.seatId,
                    seatNumber = userSeat.seatNumber,
                    price = userSeat.price,
                    temporaryStatus = temporaryStatus,
                    tempMinutes = 10
                )
            )
            reservationRepository.flush()
            
            reservation
        }
    }
    
    /**
     * 캐시 제거 - 테스트 간 데이터 반영을 위해
     */
    fun clearCache() {
        statusTypeCache.clear()
    }
    
    // ========== 빌더 패턴 API ==========
    
    /**
     * 사용성을 향상시킨 빌더 패턴 API
     * 사용 예시:
     * ```
     * val env = TestDataFixture.builder(context)
     *     .withUsers(5, pointAmount = BigDecimal("100000"))
     *     .withConcert("테스트 콘서트")
     *     .withSeats(10)
     *     .build()
     * ```
     */
    fun builder(context: ApplicationContext): TestEnvironmentBuilder {
        return TestEnvironmentBuilder(context)
    }
    
    class TestEnvironmentBuilder(private val context: ApplicationContext) {
        private var userCount: Int = 1
        private var startUserId: Long = 1L
        private var pointAmount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT
        private var withPoints: Boolean = true
        private var concertTitle: String = TestDataConstants.Concert.DEFAULT_TITLE
        private var artist: String = TestDataConstants.Concert.DEFAULT_ARTIST
        private var seatCount: Int = TestDataConstants.Seat.DEFAULT_SEAT_COUNT
        private var venue: String = TestDataConstants.Schedule.DEFAULT_VENUE
        private var daysFromNow: Long = TestDataConstants.Schedule.DEFAULT_DAYS_FROM_NOW
        
        fun withUsers(
            count: Int,
            startUserId: Long = 1L,
            pointAmount: BigDecimal = TestDataConstants.Point.DEFAULT_AMOUNT,
            withPoints: Boolean = true
        ): TestEnvironmentBuilder {
            this.userCount = count
            this.startUserId = startUserId
            this.pointAmount = pointAmount
            this.withPoints = withPoints
            return this
        }
        
        fun withConcert(
            title: String = TestDataConstants.Concert.DEFAULT_TITLE,
            artist: String = TestDataConstants.Concert.DEFAULT_ARTIST
        ): TestEnvironmentBuilder {
            this.concertTitle = title
            this.artist = artist
            return this
        }
        
        fun withSchedule(
            venue: String = TestDataConstants.Schedule.DEFAULT_VENUE,
            daysFromNow: Long = TestDataConstants.Schedule.DEFAULT_DAYS_FROM_NOW
        ): TestEnvironmentBuilder {
            this.venue = venue
            this.daysFromNow = daysFromNow
            return this
        }
        
        fun withSeats(count: Int): TestEnvironmentBuilder {
            this.seatCount = count
            return this
        }
        
        fun build(): CompleteTestEnvironment {
            createAllStatusTypes(context)
            
            val users = if (userCount == 1) {
                listOf(createTestUser(context, startUserId, withPoints, pointAmount))
            } else {
                createTestUsers(context, userCount, startUserId, withPoints, pointAmount)
            }
            
            val concertEnv = createFullConcertEnvironment(
                context = context,
                concertTitle = concertTitle,
                seatCount = seatCount,
                daysFromNow = daysFromNow
            )
            
            return CompleteTestEnvironment(
                users = users,
                concertEnvironment = concertEnv
            )
        }
    }
    
    // ========== 유틸리티 메서드 ==========
    
    /**
     * 빠른 단일 사용자 + 콘서트 환경 생성
     */
    fun quickTestEnvironment(context: ApplicationContext): CompleteTestEnvironment {
        return builder(context).build()
    }
    
    /**
     * 동시성 테스트용 빠른 환경 생성
     */
    fun quickConcurrencyEnvironment(
        context: ApplicationContext,
        userCount: Int = 5
    ): ConcurrencyTestEnvironment {
        return createConcurrencyTestEnvironment(context, userCount)
    }
}

/**
 * 콘서트 테스트 환경 데이터 클래스
 */
data class ConcertTestEnvironment(
    val concert: Concert,
    val schedule: ConcertSchedule,
    val seats: List<Seat>
) {
    /**
     * 사용 가능한 좌석 수
     */
    fun availableSeatCount(): Int {
        return seats.count { it.status.code == TestDataConstants.SeatStatusType.AVAILABLE.code }
    }
    
    /**
     * 좌석 번호로 좌석 찾기
     */
    fun getSeatByNumber(seatNumber: String): Seat? {
        return seats.find { it.seatNumber == seatNumber }
    }
}

/**
 * 완전한 테스트 환경 데이터 클래스
 */
data class CompleteTestEnvironment(
    val users: List<User>,
    val concertEnvironment: ConcertTestEnvironment
) {
    val primaryUser: User get() = users.first()
    val concert: Concert get() = concertEnvironment.concert
    val schedule: ConcertSchedule get() = concertEnvironment.schedule
    val seats: List<Seat> get() = concertEnvironment.seats
    val firstSeat: Seat get() = seats.first()
    
    /**
     * 사용자 ID로 사용자 찾기
     */
    fun getUserById(userId: Long): User? {
        return users.find { it.userId == userId }
    }
    
    /**
     * 사용 가능한 좌석들 가져오기
     */
    fun getAvailableSeats(): List<Seat> {
        return seats.filter { it.status.code == TestDataConstants.SeatStatusType.AVAILABLE.code }
    }
    
    /**
     * 특정 범위의 좌석들 가져오기
     */
    fun getSeatsInRange(startIndex: Int, count: Int): List<Seat> {
        return seats.drop(startIndex).take(count)
    }
}

/**
 * 동시성 테스트 환경 데이터 클래스
 */
data class ConcurrencyTestEnvironment(
    val users: List<User>,
    val concertEnvironment: ConcertTestEnvironment,
    val tokens: List<kr.hhplus.be.server.domain.auth.models.WaitingToken>
) {
    val primaryUser: User get() = users.first()
    val concert: Concert get() = concertEnvironment.concert
    val schedule: ConcertSchedule get() = concertEnvironment.schedule
    val seats: List<Seat> get() = concertEnvironment.seats
    val firstSeat: Seat get() = seats.first()
    val primaryToken: kr.hhplus.be.server.domain.auth.models.WaitingToken get() = tokens.first()
    
    /**
     * 사용자 ID로 토큰 찾기
     */
    fun getTokenForUser(userId: Long): kr.hhplus.be.server.domain.auth.models.WaitingToken? {
        return tokens.find { it.userId == userId }
    }
    
    /**
     * 사용 가능한 좌석들 가져오기
     */
    fun getAvailableSeats(): List<Seat> {
        return seats.filter { it.status.code == TestDataConstants.SeatStatusType.AVAILABLE.code }
    }
}