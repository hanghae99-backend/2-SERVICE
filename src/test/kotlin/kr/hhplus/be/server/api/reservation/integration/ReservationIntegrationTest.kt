package kr.hhplus.be.server.api.reservation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.config.TestDataCleanupService
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.annotation.Rollback
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate

data class TestData(
    val user: User,
    val concert: Concert,
    val schedule: ConcertSchedule,
    val seat: Seat,
    val token: WaitingToken,
    val temporaryStatus: ReservationStatusType,
    val confirmedStatus: ReservationStatusType,
    val cancelledStatus: ReservationStatusType,
    val availableStatus: SeatStatusType,
    val reservedStatus: SeatStatusType
)

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReservationIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val testDataCleanupService: TestDataCleanupService,
    private val userRepository: UserRepository,
    private val pointRepository: PointRepository,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationStatusTypeRepository: ReservationStatusTypePojoRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory
) : DescribeSpec() {

    private lateinit var mockMvc: MockMvc

    // 각 테스트마다 새로운 데이터를 생성하는 헬퍼 함수
    private fun createTestData(): TestData {
        // 고유한 ID 생성 (나노초 + 랜덤으로 충돌 방지)
        val timestamp = System.nanoTime()
        val random = (1..1000).random()
        val uniqueUserId = (timestamp % 100000) + random

        // 테스트용 사용자 생성
        val testUser = userRepository.save(User.create(uniqueUserId))

        // 포인트 생성
        pointRepository.save(Point.create(testUser.userId, BigDecimal("500000")))

        // 콘서트 생성
        val testConcert = concertRepository.save(
            Concert.create(
                title = "테스트 콘서트 $timestamp",
                artist = "테스트 아티스트 $timestamp"
            )
        )

        // 콘서트 스케줄 생성
        val testSchedule = concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(30),
                venue = "테스트 공연장 $timestamp",
                totalSeats = 100
            )
        )

        // 좌석 상태 타입 생성 (기존 것이 있다면 재사용)
        val availableStatus = seatStatusTypeRepository.findByCode("AVAILABLE")
            ?: seatStatusTypeRepository.save(
                SeatStatusType(
                    code = "AVAILABLE",
                    name = "예약 가능",
                    description = "예약 가능한 좌석"
                )
            )

        val reservedStatus = seatStatusTypeRepository.findByCode("RESERVED")
            ?: seatStatusTypeRepository.save(
                SeatStatusType(
                    code = "RESERVED",
                    name = "예약됨",
                    description = "예약된 좌석"
                )
            )

        val occupiedStatus = seatStatusTypeRepository.findByCode("OCCUPIED")
            ?: seatStatusTypeRepository.save(
                SeatStatusType(
                    code = "OCCUPIED",
                    name = "점유됨",
                    description = "점유된 좌석"
                )
            )

        // 좌석 생성
        val testSeat = seatRepository.save(
            Seat.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A${random}", // 짧은 랜덤 번호 사용
                price = BigDecimal("50000"),
                availableStatus = availableStatus
            )
        )

        // 예약 상태 타입 생성 (기존 것이 있다면 재사용)
        val temporaryStatus = reservationStatusTypeRepository.findByCode("TEMPORARY")
            ?: reservationStatusTypeRepository.save(
                ReservationStatusType(
                    code = "TEMPORARY",
                    name = "임시 예약",
                    description = "임시 예약 상태"
                )
            )

        val confirmedStatus = reservationStatusTypeRepository.findByCode("CONFIRMED")
            ?: reservationStatusTypeRepository.save(
                ReservationStatusType(
                    code = "CONFIRMED",
                    name = "예약 확정",
                    description = "예약 확정 상태"
                )
            )

        val cancelledStatus = reservationStatusTypeRepository.findByCode("CANCELLED")
            ?: reservationStatusTypeRepository.save(
                ReservationStatusType(
                    code = "CANCELLED",
                    name = "예약 취소",
                    description = "예약 취소 상태"
                )
            )

        // 유효한 토큰 생성
        val validToken = tokenFactory.createWaitingToken(testUser.userId)
        tokenStore.activateToken(validToken.token)

        return TestData(
            user = testUser,
            concert = testConcert,
            schedule = testSchedule,
            seat = testSeat,
            token = validToken,
            temporaryStatus = temporaryStatus,
            confirmedStatus = confirmedStatus,
            cancelledStatus = cancelledStatus,
            availableStatus = availableStatus,
            reservedStatus = reservedStatus
        )
    }

    init {
        extension(SpringExtension)

        beforeEach {
            // 각 테스트 시작 전에만 데이터 정리
            testDataCleanupService.cleanupAllTestData()
            
            mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .build()
        }

        describe("예약 생성 API") {
            context("유효한 예약 요청을 할 때") {
                it("예약이 정상적으로 생성되어야 한다") {
                    // given
                    val testData = createTestData() 

                    // 디버깅: 사용자가 정말 저장되었는지 확인
                    val savedUser = userRepository.findById(testData.user.userId)

                    val request = ReservationCreateRequest(
                        userId = testData.user.userId,
                        concertId = testData.concert.concertId,
                        seatId = testData.seat.seatId,
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("좌석 예약 완료"))
                    .andExpect(jsonPath("$.data.userId").value(testData.user.userId))
                    .andExpect(jsonPath("$.data.concertId").value(testData.concert.concertId))
                    .andExpect(jsonPath("$.data.seatId").value(testData.seat.seatId))
                    .andExpect(jsonPath("$.data.statusCode").value("TEMPORARY"))
                    .andExpect(jsonPath("$.data.price").value(50000))
                }
            }

            context("존재하지 않는 사용자로 예약 요청을 할 때") {
                it("404 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()
                    val request = ReservationCreateRequest(
                        userId = 999L,
                        concertId = testData.concert.concertId,
                        seatId = testData.seat.seatId,
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }

            context("존재하지 않는 콘서트로 예약 요청을 할 때") {
                it("404 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()
                    val request = ReservationCreateRequest(
                        userId = testData.user.userId,
                        concertId = 999L,
                        seatId = testData.seat.seatId,
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }

            context("존재하지 않는 좌석으로 예약 요청을 할 때") {
                it("404 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()
                    val request = ReservationCreateRequest(
                        userId = testData.user.userId,
                        concertId = testData.concert.concertId,
                        seatId = 999L,
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }

            context("유효하지 않은 토큰으로 예약 요청을 할 때") {
                it("401 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()
                    val request = ReservationCreateRequest(
                        userId = testData.user.userId,
                        concertId = testData.concert.concertId,
                        seatId = testData.seat.seatId,
                        token = "invalid-token"
                    )

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }

            context("이미 예약된 좌석으로 예약 요청을 할 때") {
                it("400 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()

                    // 좌석을 먼저 예약함
                    reservationRepository.save(
                        Reservation.createTemporary(
                            userId = testData.user.userId,
                            concertId = testData.concert.concertId,
                            seatId = testData.seat.seatId,
                            seatNumber = testData.seat.seatNumber,
                            price = testData.seat.price,
                            temporaryStatus = testData.temporaryStatus
                        )
                    )

                    // 좌석 상태를 예약됨으로 변경
                    val updatedSeat = testData.seat.reserve(testData.reservedStatus)
                    seatRepository.save(updatedSeat)

                    val request = ReservationCreateRequest(
                        userId = testData.user.userId,
                        concertId = testData.concert.concertId,
                        seatId = testData.seat.seatId,
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                    )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }
        }

        describe("예약 취소 API") {
            context("유효한 취소 요청을 할 때") {
                it("예약이 정상적으로 취소되어야 한다") {
                    // given
                    val testData = createTestData()
                    val reservation = reservationRepository.save(
                        Reservation.createTemporary(
                            userId = testData.user.userId,
                            concertId = testData.concert.concertId,
                            seatId = testData.seat.seatId,
                            seatNumber = testData.seat.seatNumber,
                            price = testData.seat.price,
                            temporaryStatus = testData.temporaryStatus
                        )
                    )

                    val cancelRequest = ReservationCancelRequest(
                        userId = testData.user.userId,
                        cancelReason = "개인 사정으로 인한 취소",
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        delete("/api/v1/reservations/{reservationId}", reservation.reservationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelRequest))
                    )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("예약 취소 완료"))
                    .andExpect(jsonPath("$.data.reservationId").value(reservation.reservationId))
                    .andExpect(jsonPath("$.data.statusCode").value("CANCELLED"))
                }
            }

            context("존재하지 않는 예약을 취소할 때") {
                it("404 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()
                    val cancelRequest = ReservationCancelRequest(
                        userId = testData.user.userId,
                        cancelReason = "개인 사정으로 인한 취소",
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        delete("/api/v1/reservations/{reservationId}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelRequest))
                    )
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }

            context("다른 사용자의 예약을 취소할 때") {
                it("403 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()
                    val otherUserId = System.currentTimeMillis() + 1
                    val otherUser = userRepository.save(User.create(otherUserId))

                    val reservation = reservationRepository.save(
                        Reservation.createTemporary(
                            userId = otherUser.userId,
                            concertId = testData.concert.concertId,
                            seatId = testData.seat.seatId,
                            seatNumber = testData.seat.seatNumber,
                            price = testData.seat.price,
                            temporaryStatus = testData.temporaryStatus
                        )
                    )

                    val cancelRequest = ReservationCancelRequest(
                        userId = testData.user.userId, // 다른 사용자가 취소 시도
                        cancelReason = "개인 사정으로 인한 취소",
                        token = testData.token.token
                    )

                    // when & then
                    mockMvc.perform(
                        delete("/api/v1/reservations/{reservationId}", reservation.reservationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelRequest))
                    )
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }
        }

        describe("예약 조회 API") {
            context("존재하는 예약을 조회할 때") {
                it("예약 정보가 정상적으로 반환되어야 한다") {
                    // given
                    val testData = createTestData()
                    val reservation = reservationRepository.save(
                        Reservation.createTemporary(
                            userId = testData.user.userId,
                            concertId = testData.concert.concertId,
                            seatId = testData.seat.seatId,
                            seatNumber = testData.seat.seatNumber,
                            price = testData.seat.price,
                            temporaryStatus = testData.temporaryStatus
                        )
                    )

                    // when & then
                    mockMvc.perform(
                        get("/api/v1/reservations/{reservationId}", reservation.reservationId)
                            .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("예약 정보 조회 완료"))
                    .andExpect(jsonPath("$.data.reservation.reservationId").value(reservation.reservationId))
                    .andExpect(jsonPath("$.data.reservation.userId").value(testData.user.userId))
                    .andExpect(jsonPath("$.data.reservation.statusCode").value("TEMPORARY"))
                }
            }

            context("존재하지 않는 예약을 조회할 때") {
                it("404 오류가 발생해야 한다") {
                    // given
                    val nonExistentReservationId = 999L

                    // when & then
                    mockMvc.perform(
                        get("/api/v1/reservations/{reservationId}", nonExistentReservationId)
                            .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }

            context("유효하지 않은 예약 ID로 조회할 때") {
                it("400 오류가 발생해야 한다") {
                    // given
                    val invalidReservationId = -1L

                    // when & then
                    mockMvc.perform(
                        get("/api/v1/reservations/{reservationId}", invalidReservationId)
                            .contentType(MediaType.APPLICATION_JSON)
                    )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }
        }

        describe("검증 테스트") {
            context("필수 파라미터가 누락된 요청을 할 때") {
                it("400 오류가 발생해야 한다") {
                    // given
                    val testData = createTestData()
                    val invalidRequestJson = """
                    {
                        "userId": ${testData.user.userId},
                        "concertId": ${testData.concert.concertId},
                        "token": "${testData.token.token}"
                    }
                    """.trimIndent()

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequestJson)
                    )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                }
            }

            context("잘못된 JSON 형식으로 요청할 때") {
                it("400 오류가 발생해야 한다") {
                    // given
                    val invalidJson = "{ invalid json }"

                    // when & then
                    mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson)
                    )
                    .andExpect(status().isBadRequest)
                }
            }
        }
    }
}
