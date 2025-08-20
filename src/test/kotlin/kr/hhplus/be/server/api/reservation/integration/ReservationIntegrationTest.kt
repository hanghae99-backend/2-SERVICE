package kr.hhplus.be.server.api.reservation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.models.User
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class ReservationIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val userJpaRepository: UserJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val objectMapper: ObjectMapper,
) : DescribeSpec({

    extension(SpringExtension)

    lateinit var mockMvc: MockMvc

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }

    describe("좌석 예약 API") {
        context("유효한 예약 요청 시") {
            it("예약이 성공적으로 생성되어야 한다") {
                // given
                val user = User.createWithId(1L, "테스트 사용자")
                userJpaRepository.save(user)
                
                val schedule = ConcertSchedule(
                    scheduleId = 1L,
                    concertId = 1L,
                    concertDate = LocalDate.now().plusDays(30),
                    venue = "테스트 공연장",
                    totalSeats = 100,
                    availableSeats = 100
                )
                concertScheduleJpaRepository.save(schedule)
                
                val seat = Seat.create(
                    scheduleId = 1L, 
                    seatNumber = "A1", 
                    price = BigDecimal("50000"),
                    availableStatus = SeatStatusType("AVAILABLE", "예약가능")
                )
                seat.seatId = 1L
                seatJpaRepository.save(seat)
                seatJpaRepository.flush()

                val request = ReservationCreateRequest(
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    token = "test-token"
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(1))
                    .andExpect(jsonPath("$.data.seatId").value(1))
                    .andExpect(jsonPath("$.data.status").value("RESERVED"))
            }
        }
        
        context("이미 예약된 좌석에 대한 예약 요청 시") {
            it("409 Conflict를 반환해야 한다") {
                // given
                val user1 = User.createWithId(2L, "사용자1")
                val user2 = User.createWithId(3L, "사용자2")
                userJpaRepository.saveAll(listOf(user1, user2))
                
                val schedule = ConcertSchedule(
                    scheduleId = 2L,
                    concertId = 1L,
                    concertDate = LocalDate.now().plusDays(30),
                    venue = "테스트 공연장",
                    totalSeats = 100,
                    availableSeats = 100
                )
                concertScheduleJpaRepository.save(schedule)
                
                val seat = Seat.create(
                    scheduleId = 2L, 
                    seatNumber = "B1", 
                    price = BigDecimal("50000"),
                    availableStatus = SeatStatusType("AVAILABLE", "예약가능")
                )
                seat.seatId = 2L
                seatJpaRepository.save(seat)
                seatJpaRepository.flush()

                val request = ReservationCreateRequest(
                    userId = 2L,
                    concertId = 2L,
                    seatId = 2L,
                    token = "test-token-2"
                )

                // 첫 번째 예약 (성공)
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .header("X-User-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isCreated)

                // 두 번째 예약 시도 (실패)
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .header("X-User-Id", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isConflict)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("예약 취소 API") {
        context("유효한 예약 취소 요청 시") {
            it("예약이 성공적으로 취소되어야 한다") {
                // given
                val user = User.createWithId(4L, "테스트 사용자")
                userJpaRepository.save(user)
                
                val schedule = ConcertSchedule(
                    scheduleId = 3L,
                    concertId = 1L,
                    concertDate = LocalDate.now().plusDays(30),
                    venue = "테스트 공연장",
                    totalSeats = 100,
                    availableSeats = 100
                )
                concertScheduleJpaRepository.save(schedule)
                
                val seat = Seat.create(
                    scheduleId = 3L, 
                    seatNumber = "C1", 
                    price = BigDecimal("50000"),
                    availableStatus = SeatStatusType("AVAILABLE", "예약가능")
                )
                seat.seatId = 3L
                seatJpaRepository.save(seat)
                seatJpaRepository.flush()

                // 먼저 예약 생성
                val reservationRequest = ReservationCreateRequest(
                    userId = 4L,
                    concertId = 3L,
                    seatId = 3L,
                    token = "test-token-4"
                )
                val reservationResult = mockMvc.perform(
                    post("/api/v1/reservations")
                        .header("X-User-Id", "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reservationRequest))
                )
                    .andExpect(status().isCreated)
                    .andReturn()
                
                val responseContent = reservationResult.response.contentAsString
                val responseJson = objectMapper.readTree(responseContent)
                val reservationId = responseJson.get("data").get("reservationId").asLong()

                // when & then - 예약 취소
                mockMvc.perform(
                    delete("/api/v1/reservations/{reservationId}", reservationId)
                        .header("X-User-Id", "4")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCELED"))
            }
        }
    }

    describe("사용자 예약 목록 조회 API") {
        context("사용자의 예약 목록을 조회할 때") {
            it("해당 사용자의 모든 예약을 반환해야 한다") {
                // given
                val user = User.createWithId(5L, "테스트 사용자")
                userJpaRepository.save(user)

                // when & then
                mockMvc.perform(
                    get("/api/v1/reservations")
                        .header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
            }
        }
    }
})