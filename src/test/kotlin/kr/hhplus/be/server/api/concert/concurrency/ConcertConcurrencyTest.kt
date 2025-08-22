package kr.hhplus.be.server.api.concert.concurrency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.config.ConcurrencyTest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.ConcertScheduleJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatJpaRepository
import kr.hhplus.be.server.domain.concert.infrastructure.SeatStatusTypeJpaRepository
import kr.hhplus.be.server.domain.reservation.infrastructure.ReservationStatusTypeJpaRepository
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.user.infrastructure.UserJpaRepository
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.global.lock.DistributedLock
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ConcurrencyTest
class ConcertConcurrencyTest(
    private val webApplicationContext: WebApplicationContext,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val seatStatusTypeJpaRepository: SeatStatusTypeJpaRepository,
    private val reservationStatusTypeJpaRepository: ReservationStatusTypeJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val objectMapper: ObjectMapper,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testUsers: List<User>
    lateinit var testTokens: List<WaitingToken>

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 데이터 정리
        try {
            seatJpaRepository.deleteAll()
            concertScheduleJpaRepository.deleteAll()
            concertJpaRepository.deleteAll()
            seatStatusTypeJpaRepository.deleteAll()
            userJpaRepository.deleteAll()
        } catch (e: Exception) {
            // 무시
        }
        
        // Redis 정리
        try {
            redisTemplate.connectionFactory?.connection?.flushAll()
        } catch (e: Exception) {
            // 무시
        }
        
        // 분산락 통계 초기화
        distributedLock.resetStatistics()

        // 테스트 데이터 생성
        testConcert = concertJpaRepository.save(
            Concert.create("동시성 테스트 콘서트", "테스트 아티스트")
        )
        
        testSchedule = concertScheduleJpaRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(10),
                venue = "테스트 홀",
                totalSeats = 50
            )
        )

        // 테스트 사용자들 생성
        testUsers = mutableListOf()
        repeat(10) { index ->
            val user = userJpaRepository.save(User(
                userId = (index+1).toLong(),
            ))
            userJpaRepository.flush()
            testUsers = testUsers + user
        }

        // 테스트 토큰들 생성
        testTokens = testUsers.map { user ->
            val token = tokenFactory.createWaitingToken(user.userId)
            tokenStore.save(token)
            tokenStore.activateToken(token.token)
            token
        }

        Thread.sleep(100)
    }

    afterEach {
        val stats = distributedLock.getLockStatistics()
        println("""
            === Concert 동시성 테스트 분산락 통계 ===
            성공: ${stats.acquisitionCount}
            실패: ${stats.failureCount}
            평균 대기시간: ${stats.averageWaitTimeMs}ms
            성공률: ${stats.successRate}%
        """.trimIndent())
    }

    describe("콘서트 좌석 예약 동시성 테스트 - 분산락 적용") {
        context("여러 사용자가 동시에 같은 좌석을 예약하려고 할 때") {
            it("분산락으로 하나의 예약만 성공해야 한다") {
                // given
                // 좌석 상태 타입 생성 - AVAILABLE과 RESERVED 모두 필요
                val availableStatus = seatStatusTypeJpaRepository.save(
                    SeatStatusType(
                        code = SeatStatusType.AVAILABLE,
                        name = "예약 가능",
                        description = "예약 가능한 좌석"
                    )
                )

                // RESERVED 상태 추가 - 예약 처리 시 필요
                seatStatusTypeJpaRepository.save(
                    SeatStatusType(
                        code = SeatStatusType.RESERVED,
                        name = "예약됨",
                        description = "예약된 좌석"
                    )
                )

                // ReservationStatusType 데이터 생성 - TEMPORARY와 CONFIRMED 필요
                reservationStatusTypeJpaRepository.save(
                    ReservationStatusType(
                        code = ReservationStatusType.TEMPORARY,
                        name = "임시 예약",
                        description = "결제 대기 중",
                        category = "NORMAL",
                        autoExpireMinutes = 5,
                        isFinal = false
                    )
                )

                reservationStatusTypeJpaRepository.save(
                    ReservationStatusType(
                        code = ReservationStatusType.CONFIRMED,
                        name = "예약 확정",
                        description = "결제 완료",
                        category = "NORMAL",
                        autoExpireMinutes = null,
                        isFinal = false
                    )
                )
                
                val testSeat = seatJpaRepository.save(
                    Seat.create(
                        scheduleId = testSchedule.scheduleId,
                        seatNumber = "A1",
                        price = BigDecimal("80000"),
                        availableStatus = availableStatus
                    )
                )

                val executor = Executors.newFixedThreadPool(testUsers.size)
                val latch = CountDownLatch(testUsers.size)
                val successCount = AtomicInteger(0)
                val failureCount = AtomicInteger(0)

                // when
                val futures = testUsers.mapIndexed { index, user ->
                    CompletableFuture.supplyAsync({
                        try {
                            latch.countDown()
                            latch.await()
                            
                            val request = ReservationCreateRequest(
                                userId = user.userId,
                                concertId = testConcert.concertId,
                                seatId = testSeat.seatId,
                                token = testTokens[index].token
                            )

                            val result = mockMvc.perform(
                                post("/api/v1/reservations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request))
                            ).andReturn()

                            if (result.response.status == 201) {
                                successCount.incrementAndGet()
                                "SUCCESS"
                            } else {
                                failureCount.incrementAndGet()
                                "FAILURE:${result.response.status}"
                            }
                        } catch (e: Exception) {
                            failureCount.incrementAndGet()
                            "ERROR:${e.message}"
                        }
                    }, executor)
                }

                val results = futures.map { it.get(30, TimeUnit.SECONDS) }

                // then
                println("=== 좌석 예약 동시성 결과 ===")
                println("성공: ${successCount.get()}, 실패: ${failureCount.get()}")
                results.forEach { println(it) }
                
                // 하나만 성공해야 함
                successCount.get() shouldBe 1
                failureCount.get() shouldBe (testUsers.size - 1)

                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }
})