package kr.hhplus.be.server.api.payment.integration

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.concert.models.*
import kr.hhplus.be.server.domain.concert.repositories.*
import kr.hhplus.be.server.domain.payment.models.Payment
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.domain.payment.repository.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repository.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class PaymentIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var pointHistoryTypeRepository: PointHistoryTypePojoRepository

    @Autowired
    private lateinit var concertRepository: ConcertRepository

    @Autowired
    private lateinit var concertScheduleRepository: ConcertScheduleRepository

    @Autowired
    private lateinit var seatRepository: SeatRepository

    @Autowired
    private lateinit var seatStatusTypeRepository: SeatStatusTypePojoRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var reservationStatusTypeRepository: ReservationStatusTypePojoRepository

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var paymentStatusTypeRepository: PaymentStatusTypePojoRepository

    @Autowired
    private lateinit var tokenStore: TokenStore

    @Autowired
    private lateinit var tokenFactory: TokenFactory

    private lateinit var mockMvc: MockMvc
    private lateinit var testUser: User
    private lateinit var testConcert: Concert
    private lateinit var testSchedule: ConcertSchedule
    private lateinit var testSeat: Seat
    private lateinit var testReservation: Reservation
    private lateinit var validToken: WaitingToken

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
        paymentRepository.deleteAll()
        reservationRepository.deleteAll()
        seatRepository.deleteAll()
        concertScheduleRepository.deleteAll()
        concertRepository.deleteAll()
        pointRepository.deleteAll()
        userRepository.deleteAll()

        // 테스트 데이터 설정
        setupTestData()
    }

    private fun setupTestData() {
        // 테스트용 사용자 생성
        testUser = userRepository.save(User.create(1L))

        // 포인트 생성 (충분한 잔액)
        pointRepository.save(Point.create(testUser.userId, BigDecimal("500000")))

        // 포인트 이력 타입 생성
        pointHistoryTypeRepository.save(
            PointHistoryType(
                code = "DEDUCT",
                name = "사용",
                description = "포인트 사용"
            )
        )

        // 콘서트 생성
        testConcert = concertRepository.save(
            Concert.create(
                title = "테스트 콘서트",
                artist = "테스트 아티스트"
            )
        )

        // 콘서트 스케줄 생성
        testSchedule = concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(30),
                venue = "테스트 공연장",
                totalSeats = 100
            )
        )

        // 좌석 상태 타입 생성
        val availableStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "AVAILABLE",
                name = "예약 가능",
                description = "예약 가능한 좌석"
            )
        )

        val reservedStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "RESERVED",
                name = "예약됨",
                description = "예약된 좌석"
            )
        )

        // 좌석 생성
        testSeat = seatRepository.save(
            Seat.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A1",
                price = BigDecimal("50000"),
                availableStatus = reservedStatus
            )
        )

        // 예약 상태 타입 생성
        val temporaryStatus = reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "TEMPORARY",
                name = "임시 예약",
                description = "임시 예약 상태"
            )
        )

        reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "CONFIRMED",
                name = "예약 확정",
                description = "예약 확정 상태"
            )
        )

        // 임시 예약 생성
        testReservation = reservationRepository.save(
            Reservation.createTemporary(
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                seatNumber = testSeat.seatNumber,
                price = testSeat.price,
                temporaryStatus = temporaryStatus,
                tempMinutes = 10
            )
        )

        // 결제 상태 타입 생성
        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = "PEND",
                name = "결제 대기",
                description = "결제 처리 중"
            )
        )

        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = "COMP",
                name = "결제 완료",
                description = "결제 완료됨"
            )
        )

        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = "FAIL",
                name = "결제 실패",
                description = "결제 실패됨"
            )
        )

        // 유효한 토큰 생성
        validToken = tokenFactory.createWaitingToken(testUser.userId)
        tokenStore.activateToken(validToken.token)
    }

    @Nested
    @DisplayName("결제 처리 API 테스트")
    inner class PaymentProcessingTest {

        @Test
        @DisplayName("유효한 결제 요청 시 결제가 정상적으로 처리되어야 한다")
        fun processPayment_Success() {
            // given
            val request = PaymentRequest(
                userId = testUser.userId,
                reservationId = testReservation.reservationId,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("결제가 완료되었습니다"))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.statusCode").value("COMP"))
                .andExpect(jsonPath("$.data.amount").value(50000))
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 결제 요청 시 404 오류가 발생해야 한다")
        fun processPayment_Fail_UserNotFound() {
            // given
            val request = PaymentRequest(
                userId = 999L,
                reservationId = testReservation.reservationId,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("존재하지 않는 예약으로 결제 요청 시 404 오류가 발생해야 한다")
        fun processPayment_Fail_ReservationNotFound() {
            // given
            val request = PaymentRequest(
                userId = testUser.userId,
                reservationId = 999L,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("유효하지 않은 토큰으로 결제 요청 시 401 오류가 발생해야 한다")
        fun processPayment_Fail_InvalidToken() {
            // given
            val request = PaymentRequest(
                userId = testUser.userId,
                reservationId = testReservation.reservationId,
                token = "invalid-token"
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("결제 조회 API 테스트")
    inner class PaymentRetrievalTest {

        @Test
        @DisplayName("존재하는 결제를 조회할 때 결제 정보가 정상적으로 반환되어야 한다")
        fun getPayment_Success() {
            // given - 결제 데이터 생성
            val pendingStatus = paymentStatusTypeRepository.findByCode("PEND")!!
            val payment = paymentRepository.save(
                Payment(
                    paymentId = 0,
                    userId = testUser.userId,
                    amount = BigDecimal("50000"),
                    paymentMethod = "POINT",
                    status = pendingStatus,
                    paidAt = null
                )
            )

            // when & then
            mockMvc.perform(
                get("/api/v1/payments/{paymentId}", payment.paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("결제 정보 조회가 완료되었습니다"))
                .andExpect(jsonPath("$.data.paymentId").value(payment.paymentId))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.amount").value(50000))
        }

        @Test
        @DisplayName("존재하지 않는 결제를 조회할 때 404 오류가 발생해야 한다")
        fun getPayment_Fail_PaymentNotFound() {
            // given
            val nonExistentPaymentId = 999L

            // when & then
            mockMvc.perform(
                get("/api/v1/payments/{paymentId}", nonExistentPaymentId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("동시에 같은 좌석에 대해 결제 요청을 할 때 하나의 결제만 성공해야 한다")
        fun processPayment_Concurrency_OnlyOneSucceeds() {
            // given - 두 번째 사용자와 예약 생성
            val secondUser = userRepository.save(User.create(2L))
            pointRepository.save(Point.create(secondUser.userId, BigDecimal("500000")))

            val temporaryStatus = reservationStatusTypeRepository.findByCode("TEMPORARY")!!
            val secondReservation = reservationRepository.save(
                Reservation.createTemporary(
                    userId = secondUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    seatNumber = testSeat.seatNumber,
                    price = testSeat.price,
                    temporaryStatus = temporaryStatus,
                    tempMinutes = 10
                )
            )

            val secondToken = tokenFactory.createWaitingToken(secondUser.userId)
            tokenStore.activateToken(secondToken.token)

            val request1 = PaymentRequest(
                userId = testUser.userId,
                reservationId = testReservation.reservationId,
                token = validToken.token
            )

            val request2 = PaymentRequest(
                userId = secondUser.userId,
                reservationId = secondReservation.reservationId,
                token = secondToken.token
            )

            val executor = Executors.newFixedThreadPool(2)
            val successCount = AtomicInteger(0)

            // when - 동시 결제 요청
            val future1 = CompletableFuture.supplyAsync({
                try {
                    val result = mockMvc.perform(
                        post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1))
                    ).andReturn()

                    if (result.response.status == 201) {
                        successCount.incrementAndGet()
                    }
                    result.response.status
                } catch (e: Exception) {
                    500
                }
            }, executor)

            val future2 = CompletableFuture.supplyAsync({
                try {
                    val result = mockMvc.perform(
                        post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2))
                    ).andReturn()

                    if (result.response.status == 201) {
                        successCount.incrementAndGet()
                    }
                    result.response.status
                } catch (e: Exception) {
                    500
                }
            }, executor)

            future1.get()
            future2.get()

            // then - 하나만 성공해야 함 (같은 좌석)
            assert(successCount.get() == 1) { "동시 결제 시 하나만 성공해야 하지만 ${successCount.get()}개가 성공했습니다" }

            executor.shutdown()
        }
    }
}