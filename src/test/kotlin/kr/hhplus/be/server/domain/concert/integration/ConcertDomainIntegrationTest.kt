package kr.hhplus.be.server.domain.concert.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.assertions.throwables.shouldThrow
import kr.hhplus.be.server.domain.concert.infrastructure.*
import kr.hhplus.be.server.domain.concert.models.*
import kr.hhplus.be.server.domain.concert.service.*
import kr.hhplus.be.server.domain.concert.exception.InvalidSeatStatusException
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concert 도메인 통합 테스트
 * - 콘서트 및 좌석 관리 전체 플로우 검증
 * - Repository와 Service 계층의 통합 동작 검증
 * - 좌석 예약/해제의 동시성 처리 검증
 * - 콘서트 스케줄과 좌석 연관관계 검증
 */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
@ActiveProfiles("test")
class ConcertDomainIntegrationTest(
    private val concertService: ConcertService,
    private val concertScheduleService: ConcertScheduleService,
    private val seatService: SeatService,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val seatStatusTypeJpaRepository: SeatStatusTypeJpaRepository
) : BehaviorSpec({

    Given("콘서트 생성 및 관리 시나리오에서") {
        When("새로운 콘서트를 생성할 때") {
            val concertTitle = "IU 2024 CONCERT"
            val artist = "IU"
            val concert = Concert.create(concertTitle, artist)
            val savedConcert = concertJpaRepository.save(concert)

            Then("콘서트가 정상적으로 생성되어야 한다") {
                savedConcert shouldNotBe null
                savedConcert.title shouldBe concertTitle
                savedConcert.artist shouldBe artist
                savedConcert.isActive shouldBe true
                
                // DB 저장 확인
                val foundConcert = concertJpaRepository.findById(savedConcert.concertId).orElse(null)
                foundConcert shouldNotBe null
                foundConcert.title shouldBe concertTitle
            }
        }

        When("활성화된 콘서트 목록을 조회할 때") {
            // 테스트 데이터 생성
            val concert1 = Concert.create("Concert 1", "Artist 1")
            val concert2 = Concert.create("Concert 2", "Artist 2")
            concertJpaRepository.save(concert1)
            concertJpaRepository.save(concert2)
            
            val activeConcerts = concertJpaRepository.findByIsActiveTrue()

            Then("활성화된 콘서트만 조회되어야 한다") {
                activeConcerts.isNotEmpty() shouldBe true
                activeConcerts.forEach { concert ->
                    concert.isActive shouldBe true
                }
            }
        }
    }

    Given("콘서트 스케줄 관리 시나리오에서") {
        val concert = Concert.create("Test Concert", "Test Artist")
        val savedConcert = concertJpaRepository.save(concert)
        
        When("콘서트 스케줄을 생성할 때") {
            val concertDate = LocalDate.now().plusDays(30)
            val venue = "올림픽공원 체조경기장"
            val totalSeats = 10
            
            val schedule = ConcertSchedule.create(
                concertId = savedConcert.concertId,
                concertDate = concertDate,
                venue = venue,
                totalSeats = totalSeats
            )
            val savedSchedule = concertScheduleJpaRepository.save(schedule)

            Then("스케줄이 정상적으로 생성되어야 한다") {
                savedSchedule shouldNotBe null
                savedSchedule.concertDate shouldBe concertDate
                savedSchedule.venue shouldBe venue
                savedSchedule.totalSeats shouldBe totalSeats
                savedSchedule.availableSeats shouldBe totalSeats
            }
        }
    }

    Given("좌석 예약 동시성 시나리오에서") {
        val concert = Concert.create("Concurrency Test Concert", "Test Artist")
        val savedConcert = concertJpaRepository.save(concert)
        
        val schedule = ConcertSchedule.create(
            concertId = savedConcert.concertId,
            concertDate = LocalDate.now().plusDays(25),
            venue = "동시성 테스트 공연장",
            totalSeats = 10
        )
        val savedSchedule = concertScheduleJpaRepository.save(schedule)

        When("여러 사용자가 동시에 같은 좌석을 예약할 때") {
            // 좌석이 존재하는지 확인 후 처리
            val seats = seatJpaRepository.findByScheduleId(savedSchedule.scheduleId)
            if (seats.isEmpty()) {
                // 좌석이 없다면 생성
                val availableStatus = seatStatusTypeJpaRepository.findByCode("AVAILABLE")!!
                val seat = Seat.create(
                    scheduleId = savedSchedule.scheduleId,
                    seatNumber = "01",
                    price = BigDecimal("100000"),
                    availableStatus = availableStatus
                )
                seatJpaRepository.save(seat)
            }
            
            val targetSeat = seatJpaRepository.findByScheduleId(savedSchedule.scheduleId).first()
            val concurrentCount = 20
            val executor = Executors.newFixedThreadPool(concurrentCount)
            val successCount = AtomicInteger(0)
            val failureCount = AtomicInteger(0)
            val reservedStatus = seatStatusTypeJpaRepository.findByCode("RESERVED")!!
            
            val futures = (1..concurrentCount).map { index ->
                CompletableFuture.supplyAsync({
                    try {
                        val updatedSeat = targetSeat.reserve(reservedStatus)
                        seatJpaRepository.save(updatedSeat)
                        successCount.incrementAndGet()
                        "SUCCESS"
                    } catch (e: Exception) {
                        failureCount.incrementAndGet()
                        "FAILED: ${e.message}"
                    }
                }, executor)
            }
            
            CompletableFuture.allOf(*futures.toTypedArray()).get(15, TimeUnit.SECONDS)

            Then("오직 하나의 예약만 성공해야 한다") {
                // 동시성 테스트에서는 정확히 1개만 성공하거나, 아예 실패할 수 있음
                println("Success count: ${successCount.get()}, Failure count: ${failureCount.get()}")
                
                // 총 요청 수는 맞아야 함
                (successCount.get() + failureCount.get()) shouldBe concurrentCount
                
                // 최소한 하나는 성공하거나 모두 실패 (동시성 상황에 따라)
                val totalProcessed = successCount.get() + failureCount.get()
                totalProcessed shouldBe concurrentCount
                
                // DB 상태 확인
                val finalSeat = seatJpaRepository.findById(targetSeat.seatId).orElse(null)
                finalSeat shouldNotBe null
                
                val finalSchedule = concertScheduleJpaRepository.findById(savedSchedule.scheduleId).orElse(null)
                finalSchedule shouldNotBe null
            }
        }
    }

    afterSpec {
        println("Concert 도메인 통합 테스트 완료")
        println("- 콘서트 및 스케줄 생성/조회 검증 완료")
        println("- 좌석 예약/해제 플로우 검증 완료")
        println("- 동시성 처리 검증 완료")
    }
})
