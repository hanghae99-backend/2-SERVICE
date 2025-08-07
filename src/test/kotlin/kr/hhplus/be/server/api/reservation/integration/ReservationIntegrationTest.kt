package kr.hhplus.be.server.api.reservation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.concert.models.*
import kr.hhplus.be.server.domain.concert.repositories.*
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
class ReservationIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

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
    private lateinit var tokenStore: TokenStore

    @Autowired
    private lateinit var tokenFactory: TokenFactory

    private lateinit var mockMvc: MockMvc
    private lateinit var testUser: User
    private lateinit var testConcert: Concert
    private lateinit var testSchedule: ConcertSchedule
    private lateinit var testSeat: Seat
    private lateinit var validToken: WaitingToken
    private lateinit var temporaryStatus: ReservationStatusType
    private lateinit var confirmedStatus: ReservationStatusType
    private lateinit var cancelledStatus: ReservationStatusType

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
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

        // 포인트 생성
        pointRepository.save(Point.create(testUser.userId, BigDecimal("500000")))

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

        seatStatusTypeRepository.save(
            SeatStatusType(
                code = "RESERVED",
                name = "예약됨",
                description = "예약된 좌석"
            )
        )

        seatStatusTypeRepository.save(
            SeatStatusType(
                code = "OCCUPIED",
                name = "점유됨",
                description = "점유된 좌석"
            )
        )

        // 좌석 생성
        testSeat = seatRepository.save(
            Seat.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A1",
                price = BigDecimal("50000"),
                availableStatus = availableStatus
            )
        )

        // 예약 상태 타입 생성
        temporaryStatus = reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "TEMPORARY",
                name = "임시 예약",
                description = "임시 예약 상태"
            )
        )

        confirmedStatus = reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "CONFIRMED",
                name = "예약 확정",
                description = "예약 확정 상태"
            )
        )

        cancelledStatus = reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "CANCELLED",
                name = "예약 취소",
                description = "예약 취소 상태"
            )
        )

        // 유효한 토큰 생성
        validToken = tokenFactory.createWaitingToken(testUser.userId)
        tokenStore.activateToken(validToken.token)
    }

    @Nested
    @DisplayName("예약 생성 API 테스트")
    inner class CreateReservationTest {

        @Test
        @DisplayName("유효한 예약 요청 시 예약이 정상적으로 생성되어야 한다")
        fun createReservation_Success() {
            // given
            val request = ReservationCreateRequest(
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("좌석 예약 완료"))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.concertId").value(testConcert.concertId))
                .andExpect(jsonPath("$.data.seatId").value(testSeat.seatId))
                .andExpect(jsonPath("$.data.statusCode").value("TEMPORARY"))
                .andExpect(jsonPath("$.data.price").value(50000))
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 예약 요청 시 404 오류가 발생해야 한다")
        fun createReservation_Fail_UserNotFound() {
            // given
            val request = ReservationCreateRequest(
                userId = 999L,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("존재하지 않는 콘서트로 예약 요청 시 404 오류가 발생해야 한다")
        fun createReservation_Fail_ConcertNotFound() {
            // given
            val request = ReservationCreateRequest(
                userId = testUser.userId,
                concertId = 999L,
                seatId = testSeat.seatId,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("존재하지 않는 좌석으로 예약 요청 시 404 오류가 발생해야 한다")
        fun createReservation_Fail_SeatNotFound() {
            // given
            val request = ReservationCreateRequest(
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seatId = 999L,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("유효하지 않은 토큰으로 예약 요청 시 401 오류가 발생해야 한다")
        fun createReservation_Fail_InvalidToken() {
            // given
            val request = ReservationCreateRequest(
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                token = "invalid-token"
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("이미 예약된 좌석으로 예약 요청 시 400 오류가 발생해야 한다")
        fun createReservation_Fail_SeatAlreadyReserved() {
            // given - 좌석을 먼저 예약함
            reservationRepository.save(
                Reservation.createTemporary(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    seatNumber = testSeat.seatNumber,
                    price = testSeat.price,
                    temporaryStatus = temporaryStatus
                )
            )

            // 좌석 상태를 예약됨으로 변경
            val reservedStatus = seatStatusTypeRepository.findByCode("RESERVED")!!
            val updatedSeat = testSeat.reserve(reservedStatus)
            seatRepository.save(updatedSeat)

            val request = ReservationCreateRequest(
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("예약 취소 API 테스트")
    inner class CancelReservationTest {

        @Test
        @DisplayName("유효한 취소 요청 시 예약이 정상적으로 취소되어야 한다")
        fun cancelReservation_Success() {
            // given - 임시 예약 생성
            val reservation = reservationRepository.save(
                Reservation.createTemporary(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    seatNumber = testSeat.seatNumber,
                    price = testSeat.price,
                    temporaryStatus = temporaryStatus
                )
            )

            val cancelRequest = ReservationCancelRequest(
                userId = testUser.userId,
                cancelReason = "개인 사정으로 인한 취소",
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                delete("/api/v1/reservations/{reservationId}", reservation.reservationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelRequest))
            )
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("예약 취소 완료"))
                .andExpect(jsonPath("$.data.reservationId").value(reservation.reservationId))
                .andExpect(jsonPath("$.data.statusCode").value("CANCELLED"))
        }

        @Test
        @DisplayName("존재하지 않는 예약 취소 시 404 오류가 발생해야 한다")
        fun cancelReservation_Fail_ReservationNotFound() {
            // given
            val cancelRequest = ReservationCancelRequest(
                userId = testUser.userId,
                cancelReason = "개인 사정으로 인한 취소",
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                delete("/api/v1/reservations/{reservationId}", 999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelRequest))
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("다른 사용자의 예약 취소 시 403 오류가 발생해야 한다")
        fun cancelReservation_Fail_Forbidden() {
            // given - 다른 사용자의 예약
            val otherUser = userRepository.save(User.create(2L))
            val reservation = reservationRepository.save(
                Reservation.createTemporary(
                    userId = otherUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    seatNumber = testSeat.seatNumber,
                    price = testSeat.price,
                    temporaryStatus = temporaryStatus
                )
            )

            val cancelRequest = ReservationCancelRequest(
                userId = testUser.userId, // 다른 사용자가 취소 시도
                cancelReason = "개인 사정으로 인한 취소",
                token = validToken.token
            )

            // when & then
            mockMvc.perform(
                delete("/api/v1/reservations/{reservationId}", reservation.reservationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelRequest))
            )
                .andDo(print())
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("예약 조회 API 테스트")
    inner class GetReservationTest {

        @Test
        @DisplayName("존재하는 예약 조회 시 예약 정보가 정상적으로 반환되어야 한다")
        fun getReservation_Success() {
            // given
            val reservation = reservationRepository.save(
                Reservation.createTemporary(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    seatNumber = testSeat.seatNumber,
                    price = testSeat.price,
                    temporaryStatus = temporaryStatus
                )
            )

            // when & then
            mockMvc.perform(
                get("/api/v1/reservations/{reservationId}", reservation.reservationId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("예약 정보 조회 완료"))
                .andExpect(jsonPath("$.data.reservation.reservationId").value(reservation.reservationId))
                .andExpect(jsonPath("$.data.reservation.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.reservation.statusCode").value("TEMPORARY"))
        }

        @Test
        @DisplayName("존재하지 않는 예약 조회 시 404 오류가 발생해야 한다")
        fun getReservation_Fail_ReservationNotFound() {
            // given
            val nonExistentReservationId = 999L

            // when & then
            mockMvc.perform(
                get("/api/v1/reservations/{reservationId}", nonExistentReservationId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("유효하지 않은 예약 ID로 조회 시 400 오류가 발생해야 한다")
        fun getReservation_Fail_InvalidReservationId() {
            // given
            val invalidReservationId = -1L

            // when & then
            mockMvc.perform(
                get("/api/v1/reservations/{reservationId}", invalidReservationId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("동시에 같은 좌석에 대해 예약 요청 시 하나의 예약만 성공해야 한다")
        fun createReservation_Concurrency_OnlyOneSucceeds() {
            // given - 두 번째 사용자 생성
            val secondUser = userRepository.save(User.create(2L))
            pointRepository.save(Point.create(secondUser.userId, BigDecimal("500000")))

            val secondToken = tokenFactory.createWaitingToken(secondUser.userId)
            tokenStore.activateToken(secondToken.token)

            val request1 = ReservationCreateRequest(
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                token = validToken.token
            )

            val request2 = ReservationCreateRequest(
                userId = secondUser.userId,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                token = secondToken.token
            )

            val executor = Executors.newFixedThreadPool(2)
            val successCount = AtomicInteger(0)

            // when - 동시 예약 요청
            val future1 = CompletableFuture.supplyAsync({
                try {
                    val result = mockMvc.perform(
                        post("/api/v1/reservations")
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
                        post("/api/v1/reservations")
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

            // then - 같은 좌석이므로 하나만 성공해야 함
            assert(successCount.get() == 1) {
                "동시 예약 시 하나만 성공해야 하지만 ${successCount.get()}개가 성공했습니다"
            }

            executor.shutdown()
        }
    }

    @Nested
    @DisplayName("검증 테스트")
    inner class ValidationTest {

        @Test
        @DisplayName("필수 파라미터 누락 시 400 오류가 발생해야 한다")
        fun createReservation_Fail_ValidationError() {
            // given - seatId가 누락된 잘못된 요청
            val invalidRequestJson = """
            {
                "userId": ${testUser.userId},
                "concertId": ${testConcert.concertId},
                "token": "${validToken.token}"
            }
            """.trimIndent()

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequestJson)
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("잘못된 JSON 형식으로 요청 시 400 오류가 발생해야 한다")
        fun createReservation_Fail_InvalidJson() {
            // given
            val invalidJson = "{ invalid json }"

            // when & then
            mockMvc.perform(
                post("/api/v1/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson)
            )
                .andDo(print())
                .andExpect(status().isBadRequest)
        }
    }
}