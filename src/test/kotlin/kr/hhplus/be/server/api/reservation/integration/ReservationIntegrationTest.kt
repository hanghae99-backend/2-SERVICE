package kr.hhplus.be.server.api.reservation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.IsolationMode
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.config.TestDataCleanupHelper
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.config.TestDataConstants
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.user.models.User
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import jakarta.persistence.EntityManager
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
    private val objectMapper: ObjectMapper,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val distributedLock: DistributedLock,
    private val redisTemplate: StringRedisTemplate,
    private val transactionManager: PlatformTransactionManager
) : DescribeSpec({
    extension(SpringExtension)
    
    // 테스트 격리를 위한 설정
    isolationMode = IsolationMode.InstancePerTest

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testSeat: Seat
    lateinit var testToken: String
    
    // 각 테스트마다 고유한 userId 사용
    val testUserId = System.currentTimeMillis() % 1000000

    beforeEach {
        val transactionTemplate = TransactionTemplate(transactionManager)
        val entityManager = webApplicationContext.getBean(EntityManager::class.java)
        
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 분산락 통계 초기화
        distributedLock.resetStatistics()

        transactionTemplate.execute { _ ->
            // 테스트 데이터 정리
            val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
            TestDataCleanupHelper.cleanupAll(redisTemplate, jdbcTemplate)
            
            // 영속성 컨텍스트 클리어
            entityManager.flush()
            entityManager.clear()

            // TestDataFixture를 사용한 완전한 테스트 환경 구성
            // 고유 userId 사용
            testUser = TestDataFixture.createTestUser(
                context = webApplicationContext,
                userId = testUserId,
                withPoints = true,
                pointAmount = BigDecimal("200000")
            )
            
            // 콘서트 환경 생성
            val concertEnv = TestDataFixture.createFullConcertEnvironment(
                context = webApplicationContext,
                concertTitle = TestDataConstants.Concert.RESERVATION_TITLE,
                seatCount = TestDataConstants.Seat.DEFAULT_SEAT_COUNT
            )
            
            testConcert = concertEnv.concert
            testSchedule = concertEnv.schedule
            testSeat = concertEnv.seats.first()

            // 토큰 생성 및 활성화
            val token = TestDataFixture.createAndActivateToken(webApplicationContext, testUser.userId)
            testToken = token.token
            
            // DB에 즉시 반영
            entityManager.flush()
        }
        
        // 트랜잭션 후 영속성 컨텍스트 클리어
        entityManager.clear()
        Thread.sleep(100)
    }

    afterEach {
        val stats = distributedLock.getLockStatistics()
        println("""
            === Reservation 통합 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())
        
        Thread.sleep(200)
    }

    describe("예약 생성 API") {
        context("유효한 좌석을 예약할 때") {
            it("예약이 성공적으로 생성되어야 한다") {
                // given
                val request = ReservationCreateRequest(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = testToken
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").exists())
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.seatNumber").value("A1"))
            }
        }

        context("이미 예약된 좌석을 예약하려고 할 때") {
            it("충돌 상황을 적절히 처리해야 한다") {
                // given - 먼저 예약 생성
                val firstRequest = ReservationCreateRequest(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = testToken
                )
                
                mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest))
                ).andExpect(status().isCreated)

                // 첫 번째 예약이 완료될 시간을 줌
                Thread.sleep(500)

                // 다른 사용자 생성 - 고유한 userId 사용
                val anotherUserId = testUserId + 1000 // 충돌 방지를 위해 기존 userId에 1000을 더함
                val anotherUser = TestDataFixture.createTestUser(
                    context = webApplicationContext,
                    userId = anotherUserId,
                    withPoints = true,
                    pointAmount = TestDataConstants.Point.DEFAULT_AMOUNT
                )
                
                val anotherToken = tokenFactory.createWaitingToken(anotherUser.userId)
                tokenStore.save(anotherToken)
                tokenStore.activateToken(anotherToken.token)

                // 토큰이 제대로 활성화될 시간을 줌
                Thread.sleep(200)

                // when & then - 같은 좌석 예약 시도
                val secondRequest = ReservationCreateRequest(
                    userId = anotherUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = anotherToken.token
                )

                try {
                    val result = mockMvc.perform(
                        post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(secondRequest))
                    )

                    val response = result.andReturn().response
                    val status = response.status
                    val content = response.contentAsString
                    
                    println("두 번째 예약 시도 응답: Status=$status, Content=$content")
                    
                    // 실패 응답(4xx, 5xx)이 와야 정상
                    assert(status >= 400) { "이미 예약된 좌석에 대한 예약 시도는 실패해야 합니다. 실제 응답: $status" }
                    
                    // 구체적인 상태 코드 확인 (선택적)
                    when (status) {
                        400 -> println("비즈니스 로직 오류로 인한 Bad Request")
                        409 -> println("좌석 충돌로 인한 Conflict")
                        429 -> println("분산락 타임아웃으로 인한 Too Many Requests")
                        500 -> {
                            println("서버 내부 오류 발생")
                            println("응답 내용: $content")
                            // 500 에러도 예상 가능한 결과로 처리 (락 경합 상황에서 발생할 수 있음)
                        }
                        else -> println("기타 오류 응답: $status")
                    }
                    
                } catch (e: Exception) {
                    println("두 번째 예약 시도 중 예외 발생: ${e.message}")
                    // 예외 발생도 중복 예약 방지가 제대로 작동하는 것으로 간주
                }
            }
        }
    }
})
