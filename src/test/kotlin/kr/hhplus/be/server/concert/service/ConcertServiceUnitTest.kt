package kr.hhplus.be.server.concert.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kr.hhplus.be.server.concert.entity.Concert
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.entity.ConcertNotFoundException
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

class ConcertServiceUnitTest : BehaviorSpec({
    
    lateinit var concertJpaRepository: ConcertJpaRepository
    lateinit var seatJpaRepository: SeatJpaRepository
    lateinit var concertService: ConcertService
    
    beforeTest {
        concertJpaRepository = mockk()
        seatJpaRepository = mockk()
        concertService = ConcertService(concertJpaRepository, seatJpaRepository)
        clearMocks(concertJpaRepository, seatJpaRepository, answers = false, recordedCalls = true)
    }
    
    given("ConcertService는 콘서트 비즈니스 로직을 처리한다") {
        `when`("예약 가능한 콘서트 목록을 조회하면") {
            then("기본 기간(현재부터 3개월)의 콘서트와 좌석 정보를 반환한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                val concerts = listOf(
                    Concert(
                        concertId = 1L,
                        title = "IU 콘서트",
                        artist = "아이유",
                        venue = "올림픽공원",
                        concertDate = LocalDate.now().plusDays(30),
                        startTime = LocalTime.of(19, 0),
                        endTime = LocalTime.of(21, 0),
                        basePrice = BigDecimal("150000")
                    ),
                    Concert(
                        concertId = 2L,
                        title = "BTS 콘서트",
                        artist = "방탄소년단",
                        venue = "잠실실내체육관",
                        concertDate = LocalDate.now().plusDays(60),
                        startTime = LocalTime.of(18, 0),
                        endTime = LocalTime.of(21, 0),
                        basePrice = BigDecimal("200000")
                    )
                )
                
                every { concertJpaRepository.findAvailableConcertsByDateRange(startDate, endDate) } returns concerts
                every { concertJpaRepository.countAvailableSeatsByConcertId(1L) } returns 50
                every { concertJpaRepository.countAvailableSeatsByConcertId(2L) } returns 100
                
                // when
                val result = concertService.getAvailableConcerts()
                
                // then
                result shouldHaveSize 2
                result[0].concertId shouldBe 1L
                result[0].title shouldBe "IU 콘서트"
                result[0].availableSeats shouldBe 50
                result[1].concertId shouldBe 2L
                result[1].title shouldBe "BTS 콘서트"
                result[1].availableSeats shouldBe 100
                
                verify(exactly = 1) { concertJpaRepository.findAvailableConcertsByDateRange(startDate, endDate) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(1L) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(2L) }
            }
        }
        
        `when`("사용자 정의 기간으로 예약 가능한 콘서트를 조회하면") {
            then("해당 기간의 콘서트 목록을 반환한다") {
                // given
                val customStartDate = LocalDate.now().plusDays(10)
                val customEndDate = LocalDate.now().plusDays(20)
                val concerts = listOf(
                    Concert(
                        concertId = 3L,
                        title = "NewJeans 콘서트",
                        artist = "뉴진스",
                        venue = "올림픽홀",
                        concertDate = LocalDate.now().plusDays(15),
                        startTime = LocalTime.of(20, 0),
                        endTime = LocalTime.of(22, 0),
                        basePrice = BigDecimal("120000")
                    )
                )
                
                every { concertJpaRepository.findAvailableConcertsByDateRange(customStartDate, customEndDate) } returns concerts
                every { concertJpaRepository.countAvailableSeatsByConcertId(3L) } returns 75
                
                // when
                val result = concertService.getAvailableConcerts(customStartDate, customEndDate)
                
                // then
                result shouldHaveSize 1
                result[0].concertId shouldBe 3L
                result[0].title shouldBe "NewJeans 콘서트"
                result[0].availableSeats shouldBe 75
                
                verify(exactly = 1) { concertJpaRepository.findAvailableConcertsByDateRange(customStartDate, customEndDate) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(3L) }
            }
        }
        
        `when`("예약 가능한 콘서트가 없으면") {
            then("빈 목록을 반환한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                
                every { concertJpaRepository.findAvailableConcertsByDateRange(startDate, endDate) } returns emptyList()
                
                // when
                val result = concertService.getAvailableConcerts()
                
                // then
                result.shouldBeEmpty()
                
                verify(exactly = 1) { concertJpaRepository.findAvailableConcertsByDateRange(startDate, endDate) }
                verify(exactly = 0) { concertJpaRepository.countAvailableSeatsByConcertId(any()) }
            }
        }
        
        `when`("특정 날짜의 콘서트 목록을 조회하면") {
            then("해당 날짜의 모든 콘서트를 반환한다") {
                // given
                val targetDate = LocalDate.now().plusDays(30)
                val concerts = listOf(
                    Concert(
                        concertId = 4L,
                        title = "오후 콘서트",
                        artist = "아티스트A",
                        venue = "콘서트홀A",
                        concertDate = targetDate,
                        startTime = LocalTime.of(14, 0),
                        endTime = LocalTime.of(16, 0),
                        basePrice = BigDecimal("100000")
                    ),
                    Concert(
                        concertId = 5L,
                        title = "저녁 콘서트",
                        artist = "아티스트B",
                        venue = "콘서트홀B",
                        concertDate = targetDate,
                        startTime = LocalTime.of(19, 0),
                        endTime = LocalTime.of(21, 0),
                        basePrice = BigDecimal("150000")
                    )
                )
                
                every { concertJpaRepository.findByConcertDate(targetDate) } returns concerts
                every { concertJpaRepository.countAvailableSeatsByConcertId(4L) } returns 30
                every { concertJpaRepository.countAvailableSeatsByConcertId(5L) } returns 50
                
                // when
                val result = concertService.getConcertsByDate(targetDate)
                
                // then
                result shouldHaveSize 2
                result[0].concertId shouldBe 4L
                result[0].title shouldBe "오후 콘서트"
                result[0].availableSeats shouldBe 30
                result[1].concertId shouldBe 5L
                result[1].title shouldBe "저녁 콘서트"
                result[1].availableSeats shouldBe 50
                
                verify(exactly = 1) { concertJpaRepository.findByConcertDate(targetDate) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(4L) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(5L) }
            }
        }
        
        `when`("특정 날짜에 콘서트가 없으면") {
            then("빈 목록을 반환한다") {
                // given
                val targetDate = LocalDate.now().plusDays(100)
                
                every { concertJpaRepository.findByConcertDate(targetDate) } returns emptyList()
                
                // when
                val result = concertService.getConcertsByDate(targetDate)
                
                // then
                result.shouldBeEmpty()
                
                verify(exactly = 1) { concertJpaRepository.findByConcertDate(targetDate) }
                verify(exactly = 0) { concertJpaRepository.countAvailableSeatsByConcertId(any()) }
            }
        }
        
        `when`("존재하는 콘서트의 상세 정보를 조회하면") {
            then("콘서트 정보와 가용 좌석 수를 포함하여 반환한다") {
                // given
                val concertId = 1L
                val concert = Concert(
                    concertId = concertId,
                    title = "IU 콘서트",
                    artist = "아이유",
                    venue = "올림픽공원",
                    concertDate = LocalDate.now().plusDays(30),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("150000")
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                every { concertJpaRepository.countAvailableSeatsByConcertId(concertId) } returns 85
                
                // when
                val result = concertService.getConcertById(concertId)
                
                // then
                result.concertId shouldBe concertId
                result.title shouldBe "IU 콘서트"
                result.artist shouldBe "아이유"
                result.venue shouldBe "올림픽공원"
                result.availableSeats shouldBe 85
                result.basePrice shouldBe BigDecimal("150000")
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(concertId) }
            }
        }
        
        `when`("존재하지 않는 콘서트의 상세 정보를 조회하면") {
            then("ConcertNotFoundException이 발생한다") {
                // given
                val nonExistentConcertId = 999L
                
                every { concertJpaRepository.findById(nonExistentConcertId) } returns Optional.empty()
                
                // when & then
                val exception = shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertById(nonExistentConcertId)
                }
                
                exception.message shouldBe "콘서트를 찾을 수 없습니다. ID: $nonExistentConcertId"
                
                verify(exactly = 1) { concertJpaRepository.findById(nonExistentConcertId) }
                verify(exactly = 0) { concertJpaRepository.countAvailableSeatsByConcertId(any()) }
            }
        }
        
        `when`("콘서트 엔티티를 직접 조회하면") {
            then("엔티티 객체를 반환한다") {
                // given
                val concertId = 1L
                val concert = Concert(
                    concertId = concertId,
                    title = "IU 콘서트",
                    artist = "아이유",
                    venue = "올림픽공원",
                    concertDate = LocalDate.now().plusDays(30),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("150000")
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                
                // when
                val result = concertService.getConcertEntityById(concertId)
                
                // then
                result shouldBe concert
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
            }
        }
        
        `when`("존재하지 않는 콘서트 엔티티를 조회하면") {
            then("null을 반환한다") {
                // given
                val nonExistentConcertId = 999L
                
                every { concertJpaRepository.findById(nonExistentConcertId) } returns Optional.empty()
                
                // when
                val result = concertService.getConcertEntityById(nonExistentConcertId)
                
                // then
                result shouldBe null
                
                verify(exactly = 1) { concertJpaRepository.findById(nonExistentConcertId) }
            }
        }
        
        `when`("콘서트에 가용 좌석이 없으면") {
            then("availableSeats가 0으로 표시된다") {
                // given
                val concertId = 1L
                val concert = Concert(
                    concertId = concertId,
                    title = "매진 콘서트",
                    artist = "인기 아티스트",
                    venue = "소극장",
                    concertDate = LocalDate.now().plusDays(5),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("200000")
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                every { concertJpaRepository.countAvailableSeatsByConcertId(concertId) } returns 0
                
                // when
                val result = concertService.getConcertById(concertId)
                
                // then
                result.availableSeats shouldBe 0
                result.title shouldBe "매진 콘서트"
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(concertId) }
            }
        }
        
        `when`("여러 콘서트의 가용 좌석 수가 다르면") {
            then("각각 올바른 가용 좌석 수를 반환한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(1)
                val concerts = listOf(
                    Concert(
                        concertId = 1L,
                        title = "대형 콘서트",
                        artist = "월드스타",
                        venue = "대형 아레나",
                        concertDate = LocalDate.now().plusDays(10),
                        startTime = LocalTime.of(19, 0),
                        endTime = LocalTime.of(22, 0),
                        basePrice = BigDecimal("300000")
                    ),
                    Concert(
                        concertId = 2L,
                        title = "소형 콘서트",
                        artist = "신인가수",
                        venue = "소극장",
                        concertDate = LocalDate.now().plusDays(20),
                        startTime = LocalTime.of(20, 0),
                        endTime = LocalTime.of(22, 0),
                        basePrice = BigDecimal("80000")
                    )
                )
                
                every { concertJpaRepository.findAvailableConcertsByDateRange(startDate, endDate) } returns concerts
                every { concertJpaRepository.countAvailableSeatsByConcertId(1L) } returns 5000
                every { concertJpaRepository.countAvailableSeatsByConcertId(2L) } returns 100
                
                // when
                val result = concertService.getAvailableConcerts(startDate, endDate)
                
                // then
                result shouldHaveSize 2
                result.find { it.concertId == 1L }?.availableSeats shouldBe 5000
                result.find { it.concertId == 2L }?.availableSeats shouldBe 100
                
                verify(exactly = 1) { concertJpaRepository.findAvailableConcertsByDateRange(startDate, endDate) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(1L) }
                verify(exactly = 1) { concertJpaRepository.countAvailableSeatsByConcertId(2L) }
            }
        }
    }
    
    given("ConcertService는 데이터 변환을 정확히 처리한다") {
        `when`("Concert 엔티티를 ConcertSchedule DTO로 변환하면") {
            then("모든 필드가 올바르게 매핑된다") {
                // given
                val concert = Concert(
                    concertId = 1L,
                    title = "변환 테스트 콘서트",
                    artist = "테스트 아티스트",
                    venue = "테스트 장소",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(19, 30),
                    endTime = LocalTime.of(22, 0),
                    basePrice = BigDecimal("175000")
                )
                
                every { concertJpaRepository.findById(1L) } returns Optional.of(concert)
                every { concertJpaRepository.countAvailableSeatsByConcertId(1L) } returns 250
                
                // when
                val result = concertService.getConcertById(1L)
                
                // then
                result.concertId shouldBe concert.concertId
                result.title shouldBe concert.title
                result.artist shouldBe concert.artist
                result.venue shouldBe concert.venue
                result.concertDate shouldBe concert.concertDate
                result.startTime shouldBe concert.startTime
                result.endTime shouldBe concert.endTime
                result.basePrice shouldBe concert.basePrice
                result.availableSeats shouldBe 250
            }
        }
        
        `when`("여러 Concert 엔티티를 ConcertSchedule 리스트로 변환하면") {
            then("모든 엔티티가 올바르게 변환된다") {
                // given
                val concerts = listOf(
                    Concert(
                        concertId = 1L,
                        title = "첫 번째 콘서트",
                        artist = "아티스트1",
                        venue = "장소1",
                        concertDate = LocalDate.now().plusDays(10),
                        startTime = LocalTime.of(18, 0),
                        endTime = LocalTime.of(20, 0),
                        basePrice = BigDecimal("120000")
                    ),
                    Concert(
                        concertId = 2L,
                        title = "두 번째 콘서트",
                        artist = "아티스트2",
                        venue = "장소2",
                        concertDate = LocalDate.now().plusDays(20),
                        startTime = LocalTime.of(19, 0),
                        endTime = LocalTime.of(21, 30),
                        basePrice = BigDecimal("180000")
                    )
                )
                
                val targetDate = LocalDate.now().plusDays(15)
                every { concertJpaRepository.findByConcertDate(targetDate) } returns concerts
                every { concertJpaRepository.countAvailableSeatsByConcertId(1L) } returns 150
                every { concertJpaRepository.countAvailableSeatsByConcertId(2L) } returns 200
                
                // when
                val result = concertService.getConcertsByDate(targetDate)
                
                // then
                result shouldHaveSize 2
                
                val firstConcert = result.find { it.concertId == 1L }
                firstConcert?.title shouldBe "첫 번째 콘서트"
                firstConcert?.artist shouldBe "아티스트1"
                firstConcert?.availableSeats shouldBe 150
                
                val secondConcert = result.find { it.concertId == 2L }
                secondConcert?.title shouldBe "두 번째 콘서트"
                secondConcert?.artist shouldBe "아티스트2"
                secondConcert?.availableSeats shouldBe 200
            }
        }
    }
})
