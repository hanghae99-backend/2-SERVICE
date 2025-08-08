package kr.hhplus.be.server.api.concert.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.concert.dto.request.SearchConcertRequest
import kr.hhplus.be.server.config.TestDataCleanupService
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatStatusTypeJpaRepository
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
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
class ConcertIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val testDataCleanupService: TestDataCleanupService,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val seatStatusTypeJpaRepository: SeatStatusTypeJpaRepository,
    private val objectMapper: ObjectMapper
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 안전한 데이터 정리
        testDataCleanupService.cleanupAllTestData()

        // 테스트 데이터 생성
        testConcert = concertJpaRepository.save(
            Concert.create("통합테스트 콘서트", "테스트 아티스트")
        )
        
        testSchedule = concertScheduleJpaRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(10),
                venue = "테스트 홀",
                totalSeats = 100
            )
        )
    }

    describe("콘서트 목록 조회 API") {
        context("예약 가능한 콘서트 목록을 조회할 때") {
            it("콘서트 목록이 성공적으로 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts")
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().plusDays(30).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isArray)
            }
        }
    }

    describe("콘서트 상세 조회 API") {
        context("존재하는 콘서트를 조회할 때") {
            it("콘서트 상세 정보가 성공적으로 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/{concertId}", testConcert.concertId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.concertId").value(testConcert.concertId))
                .andExpect(jsonPath("$.data.title").value("통합테스트 콘서트"))
                .andExpect(jsonPath("$.data.artist").value("테스트 아티스트"))
            }
        }

        context("존재하지 않는 콘서트를 조회할 때") {
            it("404 Not Found 응답을 반환해야 한다") {
                // given
                val nonExistentConcertId = 99999L

                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/{concertId}", nonExistentConcertId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("좌석 조회 API") {
        context("좌석 정보가 있는 스케줄의 좌석을 조회할 때") {
            it("좌석 정보가 성공적으로 반환되어야 한다") {
                // given - 좌석 데이터 생성
                val availableStatus = seatStatusTypeJpaRepository.findByCode("AVAILABLE")!!
                val reservedStatus = seatStatusTypeJpaRepository.findByCode("RESERVED")!!

                seatJpaRepository.save(
                    Seat.create(
                        scheduleId = testSchedule.scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("50000"),
                        availableStatus = availableStatus
                    )
                )
                
                seatJpaRepository.save(
                    Seat.create(
                        scheduleId = testSchedule.scheduleId,
                        seatNumber = "A2",
                        price = BigDecimal("50000"),
                        availableStatus = availableStatus
                    )
                )
                
                seatJpaRepository.save(
                    Seat.create(
                        scheduleId = testSchedule.scheduleId,
                        seatNumber = "A3",
                        price = BigDecimal("50000"),
                        availableStatus = reservedStatus
                    )
                )

                // when & then - 예약 가능한 좌석만 조회
                mockMvc.perform(
                    get("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats", 
                        testConcert.concertId, testSchedule.scheduleId)
                        .param("availableOnly", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray)
                .andExpect(jsonPath("$.data.length()").value(2)) // 예약 가능한 좌석만 2개
            }
        }

        context("존재하지 않는 스케줄의 좌석을 조회할 때") {
            it("404 Not Found 응답을 반환해야 한다") {
                // given
                val nonExistentScheduleId = 99999L

                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats", 
                        testConcert.concertId, nonExistentScheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }

    describe("콘서트 검색 API") {
        context("유효한 검색 조건으로 콘서트를 검색할 때") {
            it("검색 결과가 성공적으로 반환되어야 한다") {
                // given
                val request = SearchConcertRequest(
                    keyword = "테스트",
                    startDate = LocalDate.now(),
                    endDate = LocalDate.now().plusDays(30),
                    availableOnly = true
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/concerts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray)
            }
        }

        context("유효하지 않은 날짜 범위로 검색할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given - 시작일이 종료일보다 늦음
                val request = SearchConcertRequest(
                    keyword = "테스트",
                    startDate = LocalDate.now().plusDays(30),
                    endDate = LocalDate.now(),
                    availableOnly = false
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/concerts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }

        context("필수 파라미터가 누락된 검색 요청을 할 때") {
            it("400 Bad Request 응답을 반환해야 한다") {
                // given - startDate가 null인 잘못된 요청
                val invalidRequestJson = """
                {
                    "keyword": "테스트",
                    "endDate": "${LocalDate.now().plusDays(30)}",
                    "availableOnly": true
                }
                """.trimIndent()

                // when & then
                mockMvc.perform(
                    post("/api/v1/concerts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson)
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }
})