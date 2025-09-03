package kr.hhplus.be.server.api.reservation.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.IsolationMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.config.MockTestConfiguration
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
import kr.hhplus.be.server.api.concert.dto.SeatDto
import org.springframework.data.redis.core.RedisTemplate
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
import org.springframework.context.annotation.Import
import java.math.BigDecimal

@IntegrationTest
@Import(MockTestConfiguration::class)
class ReservationIntegrationTest(
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
            
            // MockSeatApiClient 초기화 - 빈이 존재하는 경우에만
            try {
                val mockSeatApiClient = webApplicationContext.getBean(kr.hhplus.be.server.config.mock.MockSeatApiClient::class.java)
                mockSeatApiClient.clear()
            } catch (e: Exception) {
                // MockSeatApiClient 빈이 없는 경우 무시
                println("모크 좌석 API 클라이언트를 찾을 수 없습니다: ${e.message}")
            }
            
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
            
            // MockSeatApiClient에 좌석 데이터 동기화 - 빈이 존재하는 경우에만
            try {
                val mockSeatApiClient = webApplicationContext.getBean(kr.hhplus.be.server.config.mock.MockSeatApiClient::class.java)
                concertEnv.seats.forEach { seat ->
                    mockSeatApiClient.setSeatData(
                        seat.seatId,
                        SeatDto(
                            seatId = seat.seatId,
                            scheduleId = seat.scheduleId,
                            seatNumber = seat.seatNumber,
                            price = seat.price,
                            statusCode = seat.status.code
                        )
                    )
                }
                println("좌석 데이터 동기화 완료: ${concertEnv.seats.size}개 좌석, seatId 범위: ${concertEnv.seats.first().seatId} ~ ${concertEnv.seats.last().seatId}")
            } catch (e: Exception) {
                println("모크 좌석 API 클라이언트에 데이터 동기화 실패: ${e.message}")
            }

            // 토큰 생성 및 활성화 - 직접 생성 with 디버깅
            val tokenFactory = webApplicationContext.getBean(kr.hhplus.be.server.domain.auth.factory.TokenFactory::class.java)
            val tokenStore = webApplicationContext.getBean(kr.hhplus.be.server.domain.auth.repositories.TokenStore::class.java)
            val waitingToken = tokenFactory.createWaitingToken(testUser.userId)
            println("생성된 토큰: ${waitingToken.token}, 사용자 ID: ${testUser.userId}")
            
            tokenStore.save(waitingToken)
            println("토큰 저장 완료")
            
            tokenStore.activateToken(waitingToken.token)
            println("토큰 활성화 완료")
            
            // 토큰 검증
            val savedToken = tokenStore.findByToken(waitingToken.token)
            val isValid = tokenStore.validate(waitingToken.token)
            val status = tokenStore.getTokenStatus(waitingToken.token)
            println("저장된 토큰 조회: $savedToken")
            println("토큰 유효성: $isValid")
            println("토큰 상태: $status")
        }
        
        // 트랜잭션 후 영속성 컨텍스트 클리어
        entityManager.clear()
        
        // 토큰 생성 및 활성화 - 트랜잭션 밖에서 수행
        val tokenFactory = webApplicationContext.getBean(kr.hhplus.be.server.domain.auth.factory.TokenFactory::class.java)
        val tokenStore = webApplicationContext.getBean(kr.hhplus.be.server.domain.auth.repositories.TokenStore::class.java)
        val waitingToken = tokenFactory.createWaitingToken(testUser.userId)
        println("생성된 토큰: ${waitingToken.token}, 사용자 ID: ${testUser.userId}")
        
        tokenStore.save(waitingToken)
        println("토큰 저장 완료")
        
        tokenStore.activateToken(waitingToken.token)
        println("토큰 활성화 완료")
        
        // 토큰 검증
        val savedToken = tokenStore.findByToken(waitingToken.token)
        val isValid = tokenStore.validate(waitingToken.token)
        val status = tokenStore.getTokenStatus(waitingToken.token)
        println("저장된 토큰 조회: $savedToken")
        println("토큰 유효성: $isValid")
        println("토큰 상태: $status")
        
        testToken = waitingToken.token
        
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

    describe("예약 생성 API - POST /api/v1/reservations") {
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
                .andExpect(jsonPath("$.message").value("좌석 예약 완료"))
                .andExpect(jsonPath("$.data.reservationId").exists())
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
                .andExpect(jsonPath("$.data.seatNumber").value("A1"))
                .andExpect(jsonPath("$.data.statusCode").value("TEMPORARY"))
            }
        }

        context("이미 예약된 좌석을 예약하려고 할 때") {
            it("중복 예약을 방지해야 한다") {
                // given - 먼저 예약 생성
                val firstRequest = ReservationCreateRequest(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = testToken
                )
                
                val firstResult = mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest))
                ).andReturn()
                
                firstResult.response.status shouldBe 201

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
                
                val anotherWaitingToken = tokenFactory.createWaitingToken(anotherUser.userId)
                tokenStore.save(anotherWaitingToken)
                tokenStore.activateToken(anotherWaitingToken.token)

                // 토큰이 제대로 활성화될 시간을 줌
                Thread.sleep(200)

                // when & then - 같은 좌석 예약 시도
                val secondRequest = ReservationCreateRequest(
                    userId = anotherUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = anotherWaitingToken.token
                )

                val secondResult = mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest))
                ).andReturn()

                val status = secondResult.response.status
                status shouldBeGreaterThan 399  // 4xx 또는 5xx 에러
                
                println("두 번째 예약 시도 결과: Status=$status")
                println("응답 내용: ${secondResult.response.contentAsString}")
            }
        }

        context("잘못된 토큰으로 예약을 시도할 때") {
            it("404 Not Found를 반환해야 한다") {
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
                .andExpect(status().isNotFound)
            }
        }
    }

    describe("예약 조회 API - GET /api/v1/reservations/{id}") {
        context("존재하는 예약을 조회할 때") {
            it("예약 정보를 반환해야 한다") {
                // given - 먼저 예약 생성
                val createRequest = ReservationCreateRequest(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = testToken
                )

                val createResult = mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                ).andReturn()
                
                val responseBody = objectMapper.readTree(createResult.response.contentAsString)
                val reservationId = responseBody.get("data").get("reservationId").asLong()

                // when & then
                mockMvc.perform(
                    get("/api/v1/reservations/$reservationId")
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("예약 정보 조회 완료"))
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.data.userId").value(testUser.userId))
            }
        }

        context("존재하지 않는 예약을 조회할 때") {
            it("404 Not Found를 반환해야 한다") {
                // given
                val invalidId = 99999L

                // when & then
                mockMvc.perform(
                    get("/api/v1/reservations/$invalidId")
                )
                .andExpect(status().isNotFound)
            }
        }
    }

    describe("예약 취소 API - DELETE /api/v1/reservations/{id}") {
        context("자신의 예약을 취소할 때") {
            it("예약이 취소되어야 한다") {
                // given - 먼저 예약 생성
                val createRequest = ReservationCreateRequest(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = testToken
                )

                val createResult = mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                ).andReturn()
                
                val responseBody = objectMapper.readTree(createResult.response.contentAsString)
                val reservationId = responseBody.get("data").get("reservationId").asLong()

                val cancelRequest = ReservationCancelRequest(
                    userId = testUser.userId,
                    cancelReason = "개인 사정으로 인한 취소",
                    token = testToken
                )

                // when & then
                mockMvc.perform(
                    delete("/api/v1/reservations/$reservationId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest))
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("예약 취소 완료"))
                .andExpect(jsonPath("$.data.statusCode").value("CANCELLED"))
            }
        }

        context("다른 사용자의 예약을 취소하려고 할 때") {
            it("403 Forbidden을 반환해야 한다") {
                // given - 먼저 예약 생성
                val createRequest = ReservationCreateRequest(
                    userId = testUser.userId,
                    concertId = testConcert.concertId,
                    seatId = testSeat.seatId,
                    token = testToken
                )

                val createResult = mockMvc.perform(
                    post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                ).andReturn()
                
                val responseBody = objectMapper.readTree(createResult.response.contentAsString)
                val reservationId = responseBody.get("data").get("reservationId").asLong()

                // 다른 사용자로 취소 시도
                val anotherUserId = testUserId + 2000
                val anotherUser = TestDataFixture.createTestUser(
                    context = webApplicationContext,
                    userId = anotherUserId,
                    withPoints = false
                )
                
                val anotherTokenForCancel = tokenFactory.createWaitingToken(anotherUser.userId)
                tokenStore.save(anotherTokenForCancel)
                tokenStore.activateToken(anotherTokenForCancel.token)

                val cancelRequest = ReservationCancelRequest(
                    userId = anotherUser.userId,
                    cancelReason = null,
                    token = anotherTokenForCancel.token
                )

                // when & then
                mockMvc.perform(
                    delete("/api/v1/reservations/$reservationId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest))
                )
                .andExpect(status().isForbidden)
            }
        }
    }
})
