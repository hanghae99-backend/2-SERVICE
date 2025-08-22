package kr.hhplus.be.server.api.payment.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.IsolationMode
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
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
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.user.models.User
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate

@IntegrationTest
class PaymentIntegrationTest(
    private val webApplicationContext: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>,
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
    lateinit var testReservation: Reservation
    
    // 각 테스트마다 고유한 userId 사용
    val testUserId = System.currentTimeMillis() % 1000000

    beforeEach {
        val transactionTemplate = TransactionTemplate(transactionManager)
        val entityManager = webApplicationContext.getBean(jakarta.persistence.EntityManager::class.java)
        
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 분산락 상태 정리 먼저
        try {
            distributedLock.clearAllLocks()
        } catch (e: Exception) {
            // 무시
        }

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
                concertTitle = TestDataConstants.Concert.PAYMENT_TITLE,
                seatCount = TestDataConstants.Seat.DEFAULT_SEAT_COUNT
            )
            
            testConcert = concertEnv.concert
            testSchedule = concertEnv.schedule
            testSeat = concertEnv.seats.first()

            // 결제 상태 타입은 createCompleteTestEnvironment에서 이미 생성됨

            // 좌석 예약 생성 (TestDataFixture 사용)
            testReservation = TestDataFixture.createReservationForSeat(
                context = webApplicationContext,
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seat = testSeat,
                tempMinutes = 10
            )

            // 토큰 생성 및 활성화
            val token = TestDataFixture.createAndActivateToken(webApplicationContext, testUser.userId)
            
            // DB에 즉시 반영
            entityManager.flush()
        }
        
        // 트랜잭션 후 영속성 컨텍스트 클리어
        entityManager.clear()
        Thread.sleep(200) // 활성화 대기
    }

    afterEach {
        try {
            val stats = distributedLock.getLockStatistics()
            println("""
                === Payment 통합 테스트 분산락 통계 ===
                성공: ${stats.acquisitionCount}
                실패: ${stats.failureCount}
                평균 대기시간: ${stats.averageWaitTimeMs}ms
                성공률: ${stats.successRate}%
            """.trimIndent())
        } catch (e: Exception) {
            // 통계 오류 무시
        }
        
        // 덕트 테스트 후 정리 대기
        Thread.sleep(300)
    }

    describe("결제 API") {
        context("유효한 예약에 대해 결제를 요청할 때") {
            it("결제가 성공적으로 처리되어야 한다") {
                // given
                val activeToken = tokenStore.findActiveTokenByUserId(testUser.userId)
                    ?: throw IllegalStateException("활성화된 토큰이 없습니다")
                val request = PaymentRequest(
                    userId = testUser.userId,
                    reservationId = testReservation.reservationId,
                    seatId = testSeat.seatId,
                    token = activeToken.token
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").exists())
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.amount").value(50000))  // 좌석 기본 가격
            }
        }

        context("존재하지 않는 예약에 대해 결제를 요청할 때") {
            it("404 Not Found를 반환해야 한다") {
                // given
                val activeToken = tokenStore.findActiveTokenByUserId(testUser.userId)
                    ?: throw IllegalStateException("활성화된 토큰이 없습니다")
                val request = PaymentRequest(
                    userId = testUser.userId,
                    reservationId = 99999L,
                    seatId = testSeat.seatId,
                    token = activeToken.token
                )

                // when & then
                mockMvc.perform(
                    post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
            }
        }
    }
})