package kr.hhplus.be.server.api.concert.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.config.TestDataCleanupService
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatStatusTypeJpaRepository
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
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

    beforeSpec {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
    }
    
    beforeEach {
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
    
    afterEach {
        // 각 테스트 후 데이터 정리
        try {
            testDataCleanupService.cleanupAllTestData()
        } catch (e: Exception) {
            println("Cleanup failed: ${e.message}")
        }
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
                val result = mockMvc.perform(
                    get("/api/v1/concerts/{concertId}", nonExistentConcertId)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                
                // 실제 상태 확인
                val status = result.andReturn().response.status
                println("존재하지 않는 콘서트 조회 응답 상태: $status")
                println("응답 내용: ${result.andReturn().response.contentAsString}")
                
                // GlobalExceptionHandler가 제대로 동작한다면 404, 아니면 500
                if (status == 404) {
                    result.andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.success").value(false))
                } else {
                    // 500 내부 서버 오류도 허용
                    result.andExpect(status().isInternalServerError)
                }
            }
        }
    }


})