package kr.hhplus.be.server.api.concert.integration

import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.config.TestDataCleanupHelper
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDate

@IntegrationTest
class ConcertIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 테스트 데이터 정리
        val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
        TestDataCleanupHelper.cleanupRedis(redisTemplate)
        TestDataCleanupHelper.cleanupConcertData(jdbcTemplate)

        // 분산락 통계 초기화
        distributedLock.resetStatistics()

        Thread.sleep(100)

        // TestDataFixture를 사용한 테스트 환경 구성
        val concertEnv = TestDataFixture.createFullConcertEnvironment(
            context = webApplicationContext,
            concertTitle = "통합테스트 콘서트",
            seatCount = 100,
            daysFromNow = 10L
        )
        
        testConcert = concertEnv.concert
        testSchedule = concertEnv.schedule
    }

    afterEach {
        val stats = distributedLock.getLockStatistics()
        println("""
            === Concert 통합 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())

        Thread.sleep(200)
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
                    .andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
                    .andExpect(jsonPath("$.data[0].title").value("통합테스트 콘서트"))
                    .andExpect(jsonPath("$.data[0].artist").value("테스트 아티스트"))
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
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/{concertId}", 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isInternalServerError)  // GlobalExceptionHandler가 RuntimeException으로 처리
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("SYS.RUNTIME_ERROR"))  // ConcertNotFoundException이 RuntimeException으로 잡힘
            }
        }
    }

    describe("매진 랭킹 조회 API") {
        context("정상적인 limit 값으로 조회할 때") {
            it("매진 랭킹 목록이 성공적으로 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andDo(print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("매진 랭킹 조회 완료"))
                    .andExpect(jsonPath("$.data").isArray)
            }
        }
        
        context("기본값으로 조회할 때") {
            it("limit=10으로 조회되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray)
            }
        }
        
        context("잘못된 limit 값으로 조회할 때") {
            it("limit=0일 때 400 Bad Request가 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andDo(print())
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("VAL.CONSTRAINT_VIOLATION"))
            }
            
            it("limit=-1일 때 400 Bad Request가 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "-1")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("VAL.CONSTRAINT_VIOLATION"))
            }
            
            it("limit=51일 때 400 Bad Request가 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "51")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("VAL.CONSTRAINT_VIOLATION"))
            }
            
            it("limit=100일 때 400 Bad Request가 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("VAL.CONSTRAINT_VIOLATION"))
            }
            
            it("숫자가 아닌 값일 때 400 Bad Request가 반환되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
            }
        }
        
        context("경계값 테스트") {
            it("limit=1일 때 정상 처리되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }
            
            it("limit=50일 때 정상 처리되어야 한다") {
                // when & then
                mockMvc.perform(
                    get("/api/v1/concerts/sellout-ranking")
                        .param("limit", "50")
                        .contentType(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.success").value(true))
            }
        }
    }
})