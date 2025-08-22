package kr.hhplus.be.server.api.payment.integration

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import kr.hhplus.be.server.api.payment.dto.request.PaymentRequest
import kr.hhplus.be.server.config.IntegrationTest
import kr.hhplus.be.server.domain.auth.factory.TokenFactory
import kr.hhplus.be.server.domain.auth.repositories.TokenStore
import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistoryType
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.concert.models.*
import kr.hhplus.be.server.domain.concert.repositories.*
import kr.hhplus.be.server.domain.payment.models.PaymentStatusType
import kr.hhplus.be.server.domain.payment.repositories.PaymentRepository
import kr.hhplus.be.server.domain.payment.repositories.PaymentStatusTypePojoRepository
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.user.models.User
import kr.hhplus.be.server.domain.user.repositories.UserRepository
import kr.hhplus.be.server.global.lock.DistributedLock
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
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
    private val userRepository: UserRepository,
    private val pointRepository: PointRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
    private val concertRepository: ConcertRepository,
    private val concertScheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository,
    private val seatStatusTypeRepository: SeatStatusTypePojoRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationStatusTypeRepository: ReservationStatusTypePojoRepository,
    private val paymentStatusTypeRepository: PaymentStatusTypePojoRepository,
    private val tokenStore: TokenStore,
    private val tokenFactory: TokenFactory,
    private val distributedLock: DistributedLock,
    private val redisTemplate: RedisTemplate<String, Any>
) : DescribeSpec({
    extension(SpringExtension)

    lateinit var mockMvc: MockMvc
    lateinit var testUser: User
    lateinit var testConcert: Concert
    lateinit var testSchedule: ConcertSchedule
    lateinit var testSeat: Seat
    lateinit var testReservation: Reservation

    beforeEach {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()

        // 분산락 상태 정리 먼저
        try {
            distributedLock.clearAllLocks()
        } catch (e: Exception) {
            // 무시
        }

        // Redis 정리
        try {
            redisTemplate.connectionFactory?.connection?.use { connection ->
                connection.serverCommands()?.flushDb()
            }
        } catch (e: Exception) {
            // 테스트 환경에서 Redis 초기화 실패는 무시
        }

        // DB 데이터 정리
        val jdbcTemplate = webApplicationContext.getBean(JdbcTemplate::class.java)
        try {
            // 외래키 제약 순서를 고려한 삭제
            jdbcTemplate.execute("DELETE FROM payment")
            jdbcTemplate.execute("DELETE FROM reservation")
            jdbcTemplate.execute("DELETE FROM seat")
            jdbcTemplate.execute("DELETE FROM concert_schedule")
            jdbcTemplate.execute("DELETE FROM concert")
            jdbcTemplate.execute("DELETE FROM point_history")
            jdbcTemplate.execute("DELETE FROM point")
            jdbcTemplate.execute("DELETE FROM users")
        } catch (e: Exception) {
            // 테스트 환경에서 DB 정리 실패는 무시
        }
        
        // 충분한 초기화 대기
        Thread.sleep(500)

        // 테스트 사용자 생성 (userId를 명시적으로 설정하지 않음)
        testUser = userRepository.save(User(
            userId = 1L,
            
            
        ))
        userRepository.flush()
        
        // 포인트 생성 (충분한 금액)
        val point = pointRepository.save(Point.create(testUser.userId, BigDecimal("1000000")))
        pointRepository.flush()

        // 포인트 이력 타입
        pointHistoryTypeRepository.save(
            PointHistoryType(
                code = "DEDUCT",
                name = "사용",
                description = "포인트 사용"
            )
        )

        // 콘서트 생성
        testConcert = concertRepository.save(
            Concert.create(
                title = "결제 테스트 콘서트",
                artist = "테스트 아티스트"
            )
        )

        // 스케줄 생성
        testSchedule = concertScheduleRepository.save(
            ConcertSchedule.create(
                concertId = testConcert.concertId,
                concertDate = LocalDate.now().plusDays(30),
                venue = "테스트 공연장",
                totalSeats = 50
            )
        )

        // 좌석 상태 타입
        val availableStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "AVAILABLE",
                name = "예약가능",
                description = "예약 가능한 좌석"
            )
        )
        
        val reservedStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "RESERVED",
                name = "예약완료",
                description = "예약된 좌석"
            )
        )
        
        val occupiedStatus = seatStatusTypeRepository.save(
            SeatStatusType(
                code = "OCCUPIED",
                name = "점유완료",
                description = "결제 완료된 좌석"
            )
        )
        seatStatusTypeRepository.flush()

        // 좌석 생성 (사용 가능 상태로)
        testSeat = seatRepository.save(
            Seat.create(
                scheduleId = testSchedule.scheduleId,
                seatNumber = "A1",
                price = BigDecimal("100000"),
                availableStatus = availableStatus
            )
        )
        seatRepository.flush()

        // 예약 상태 타입
        val temporaryStatus = reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "TEMPORARY",
                name = "임시예약",
                description = "임시 예약 상태"
            )
        )

        reservationStatusTypeRepository.save(
            ReservationStatusType(
                code = "CONFIRMED",
                name = "확정예약",
                description = "결제 완료된 확정 예약"
            )
        )

        // 결제 상태 타입
        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = PaymentStatusType.PENDING,
                name = "결제대기",
                description = "결제 처리 대기중"
            )
        )
        
        paymentStatusTypeRepository.save(
            PaymentStatusType(
                code = PaymentStatusType.COMPLETED,
                name = "결제완료",
                description = "결제가 성공적으로 완료됨"
            )
        )

        pointHistoryTypeRepository.save(
            PointHistoryType.createDefault(
                PointHistoryType.USE,
                "사용",
                PointHistoryType.CATEGORY_USE,
                "포인트 사용"
            )
        )
        // 좌석을 예약 상태로 변경
        testSeat.reserve(reservedStatus)
        seatRepository.save(testSeat)
        seatRepository.flush()

        // 예약 생성
        testReservation = reservationRepository.save(
            Reservation.createTemporary(
                userId = testUser.userId,
                concertId = testConcert.concertId,
                seatId = testSeat.seatId,
                seatNumber = testSeat.seatNumber,
                price = testSeat.price,
                temporaryStatus = temporaryStatus,
                tempMinutes = 10
            )
        )
        reservationRepository.flush()

        // 토큰 생성 및 활성화
        val token = tokenFactory.createWaitingToken(testUser.userId)
        tokenStore.save(token)
        Thread.sleep(50) // 저장 대기
        tokenStore.activateToken(token.token)
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
                .andExpect(jsonPath("$.data.amount").value(100000))
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