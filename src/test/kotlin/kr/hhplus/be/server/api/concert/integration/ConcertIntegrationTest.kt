package kr.hhplus.be.server.api.concert.integration

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.concert.dto.request.SearchConcertRequest
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatStatusTypeJpaRepository
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
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

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class ConcertIntegrationTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var concertJpaRepository: ConcertJpaRepository

    @Autowired
    private lateinit var concertScheduleJpaRepository: ConcertScheduleJpaRepository

    @Autowired
    private lateinit var seatJpaRepository: SeatJpaRepository

    @Autowired
    private lateinit var seatStatusTypeJpaRepository: SeatStatusTypeJpaRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var mockMvc: MockMvc
    private lateinit var testConcert: Concert
    private lateinit var testSchedule: ConcertSchedule

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()

        // 테스트 데이터 생성
        setupTestData()
    }

    private fun setupTestData() {
        // 콘서트 생성
        testConcert = concertJpaRepository.save(
            Concert.create("통합테스트 콘서트", "테스트 아티스트")
        )
        
        // 콘서트 스케줄 생성
        testSchedule = concertScheduleJpaRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(10),
                venue = "테스트 홀",
                totalSeats = 100
            )
        )
    }

    @Test
    @DisplayName("예약 가능한 콘서트 목록 조회 성공 테스트")
    fun searchConcerts_Success() {
        // when & then
        mockMvc.perform(
            get("/api/v1/concerts")
                .param("startDate", LocalDate.now().toString())
                .param("endDate", LocalDate.now().plusDays(30).toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.data").isArray)
    }

    @Test
    @DisplayName("콘서트 상세 조회 성공 테스트")
    fun getConcert_Success() {
        // when & then
        mockMvc.perform(
            get("/api/v1/concerts/{concertId}", testConcert.concertId)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.concertId").value(testConcert.concertId))
        .andExpect(jsonPath("$.data.title").value("통합테스트 콘서트"))
        .andExpect(jsonPath("$.data.artist").value("테스트 아티스트"))
    }

    @Test
    @DisplayName("콘서트 상세 조회 실패 테스트 - 존재하지 않는 콘서트")
    fun getConcert_Fail_ConcertNotFound() {
        // given
        val nonExistentConcertId = 99999L

        // when & then
        mockMvc.perform(
            get("/api/v1/concerts/{concertId}", nonExistentConcertId)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isNotFound)
        .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("좌석 조회 성공 테스트")
    fun getSeats_Success() {
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
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray)
        .andExpect(jsonPath("$.data.length()").value(2)) // 예약 가능한 좌석만 2개
    }

    @Test
    @DisplayName("좌석 조회 실패 테스트 - 존재하지 않는 스케줄")
    fun getSeats_Fail_ScheduleNotFound() {
        // given
        val nonExistentScheduleId = 99999L

        // when & then
        mockMvc.perform(
            get("/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats", 
                testConcert.concertId, nonExistentScheduleId)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isNotFound)
        .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("콘서트 검색 성공 테스트")
    fun searchConcerts_WithFilter_Success() {
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
        .andDo(print())
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray)
    }

    @Test
    @DisplayName("콘서트 검색 실패 테스트 - 유효하지 않은 날짜 범위")
    fun searchConcerts_Fail_InvalidDateRange() {
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
        .andDo(print())
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("콘서트 검색 검증 실패 테스트 - 필수 파라미터 누락")
    fun searchConcerts_Fail_ValidationError() {
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
        .andDo(print())
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.success").value(false))
    }
}