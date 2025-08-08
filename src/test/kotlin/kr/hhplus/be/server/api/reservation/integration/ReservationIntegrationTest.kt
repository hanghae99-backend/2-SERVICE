package kr.hhplus.be.server.api.reservation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class ReservationIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
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
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testSeat: Seat
    lateinit var validToken: WaitingToken
    lateinit var temporaryStatus: ReservationStatusType
    lateinit var confirmedStatus: ReservationStatusType
    lateinit var cancelledStatus: ReservationStatusType

    beforeEach {
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

    describe("예약 생성 API") {
        context("유효한 예약 요청을 할 때") {
            it("예약이 정상적으로 생성되어야 한다") {
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
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("좌석 예약 완료"))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.concertId").value(testConcert.concertId))
                .andExpect(jsonPath("$.data.seatId").value(testSeat.seatId))
                .andExpect(jsonPath("$.data.statusCode").value("TEMPORARY"))
                .andExpect(jsonPath("$.data.price").value(50000))
            }
        }

        context("존재하지 않는 사용자로 예약 요청을 할 때") {
            it("404 오류가 발생해야 한다") {
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
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("존재하지 않는 콘서트로 예약 요청을 할 때") {
            it("404 오류가 발생해야 한다") {
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
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("존재하지 않는 좌석으로 예약 요청을 할 때") {
            it("404 오류가 발생해야 한다") {
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
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("유효하지 않은 토큰으로 예약 요청을 할 때") {
            it("401 오류가 발생해야 한다") {
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
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("이미 예약된 좌석으로 예약 요청을 할 때") {
            it("400 오류가 발생해야 한다") {
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
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("예약 취소 API") {
        context("유효한 취소 요청을 할 때") {
            it("예약이 정상적으로 취소되어야 한다") {
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
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("다른 사용자의 예약을 취소할 때") {
            it("403 오류가 발생해야 한다") {
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
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("예약 조회 API") {
        context("존재하는 예약을 조회할 때") {
            it("예약 정보가 정상적으로 반환되어야 한다") {
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
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("예약 정보 조회 완료"))
                .andExpect(jsonPath("$.data.reservation.reservationId").value(reservation.reservationId))
                .andExpect(jsonPath("$.data.reservation.userId").value(testUser.userId))
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
})
