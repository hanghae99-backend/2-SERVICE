package kr.hhplus.be.server.domain.concert.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.service.ConcertService
import java.math.BigDecimal
import java.time.LocalDate

class ConcertServiceTest : DescribeSpec({
    
    val concertRepository = mockk<ConcertRepository>()
    val concertScheduleRepository = mockk<ConcertScheduleRepository>()
    val seatRepository = mockk<SeatRepository>()
    val concertStatsService = mockk<ConcertStatsService>()
    
    val concertService = ConcertService(
        concertRepository,
        concertScheduleRepository,
        seatRepository,
        concertStatsService
    )
    
    describe("getConcertById") {
        context("존재하는 콘서트 ID로 조회할 때") {
            it("해당 콘서트 정보를 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = Concert(
                    concertId = concertId,
                    title = "Test Concert",
                    artist = "Test Artist",
                    isActive = true
                )
                
                every { concertRepository.findById(concertId) } returns concert
                
                // when
                val result = concertService.getConcertById(concertId)
                
                // then
                result shouldNotBe null
                result.concertId shouldBe concertId
                result.title shouldBe "Test Concert"
            }
        }
        
        context("존재하지 않는 콘서트 ID로 조회할 때") {
            it("ConcertNotFoundException을 던져야 한다") {
                // given
                val concertId = 999L
                
                every { concertRepository.findById(concertId) } returns null
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertById(concertId)
                }
            }
        }
    }
    
    describe("getAvailableConcerts") {
        context("예약 가능한 콘서트 목록을 조회할 때") {
            it("예약 가능한 콘서트 스케줄을 반환해야 한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                val schedule1 = ConcertSchedule(
                    scheduleId = 1L,
                    concertId = 1L,
                    concertDate = startDate,
                    venue = "Test Venue 1",
                    totalSeats = 100,
                    availableSeats = 50,
                )
                val schedule2 = ConcertSchedule(
                    scheduleId = 2L,
                    concertId = 2L,
                    concertDate = startDate.plusDays(1),
                    venue = "Test Venue 2",
                    totalSeats = 100,
                    availableSeats = 30,
                )
                val schedules = listOf(schedule1, schedule2)
                
                val concert1 = Concert(1L, "Concert 1", "Artist 1")
                val concert2 = Concert(2L, "Concert 2", "Artist 2")
                
                every { 
                    concertScheduleRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
                        startDate, endDate, 0
                    ) 
                } returns schedules
                every { concertRepository.findById(1L) } returns concert1
                every { concertRepository.findById(2L) } returns concert2
                
                // when
                val result = concertService.getAvailableConcerts(startDate, endDate)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                result[0].title shouldBe "Concert 1"
                result[1].title shouldBe "Concert 2"
            }
        }
        
        context("예약 가능한 콘서트가 없을 때") {
            it("빈 목록을 반환해야 한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                
                every { 
                    concertScheduleRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
                        startDate, endDate, 0
                    ) 
                } returns emptyList()
                
                // when
                val result = concertService.getAvailableConcerts(startDate, endDate)
                
                // then
                result shouldNotBe null
                result.size shouldBe 0
            }
        }
    }

    describe("getConcertDetailByScheduleId") {
        context("존재하는 스케줄 ID로 상세 정보를 조회할 때") {
            it("콘서트, 스케줄, 좌석 정보를 모두 포함한 상세 정보를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val concertId = 1L
                
                val concert = Concert(concertId, "Test Concert", "Test Artist")
                val schedule = ConcertSchedule(
                    scheduleId = scheduleId,
                    concertId = concertId,
                    concertDate = LocalDate.now(),
                    venue = "Test Venue",
                    totalSeats = 100,
                    availableSeats = 50
                )
                
                every { concertScheduleRepository.findById(scheduleId) } returns schedule
                every { concertRepository.findById(concertId) } returns concert
                every { seatRepository.findByScheduleId(scheduleId) } returns emptyList()
                
                // when
                val result = concertService.getConcertDetailByScheduleId(scheduleId)
                
                // then
                result shouldNotBe null
                result.concert.concertId shouldBe concertId
                result.schedule.scheduleId shouldBe scheduleId
                result.seats shouldNotBe null
            }
        }
        
        context("존재하지 않는 스케줄 ID로 조회할 때") {
            it("ConcertNotFoundException이 발생해야 한다") {
                // given
                val scheduleId = 999L
                
                every { concertScheduleRepository.findById(scheduleId) } returns null
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertDetailByScheduleId(scheduleId)
                }
            }
        }
        
        context("스케줄은 있지만 콘서트가 없을 때") {
            it("ConcertNotFoundException이 발생해야 한다") {
                // given
                val scheduleId = 1L
                val concertId = 999L
                
                val schedule = ConcertSchedule(
                    scheduleId = scheduleId,
                    concertId = concertId,
                    concertDate = LocalDate.now(),
                    venue = "Test Venue",
                    totalSeats = 100,
                    availableSeats = 50
                )
                
                every { concertScheduleRepository.findById(scheduleId) } returns schedule
                every { concertRepository.findById(concertId) } returns null
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertDetailByScheduleId(scheduleId)
                }
            }
        }
    }
    
    describe("getSchedulesByConcertId") {
        context("존재하는 콘서트의 스케줄을 조회할 때") {
            it("해당 콘서트의 모든 스케줄을 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = Concert(concertId, "Test Concert", "Test Artist")
                val schedule1 = ConcertSchedule(1L, concertId, LocalDate.now(), "Venue 1", 100, 50)
                val schedule2 = ConcertSchedule(2L, concertId, LocalDate.now().plusDays(1), "Venue 2", 100, 30)
                val schedules = listOf(schedule1, schedule2)
                
                every { concertRepository.findById(concertId) } returns concert
                every { concertScheduleRepository.findByConcertId(concertId) } returns schedules
                
                // when
                val result = concertService.getSchedulesByConcertId(concertId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                result[0].concert.concertId shouldBe concertId
                result[1].concert.concertId shouldBe concertId
            }
        }
        
        context("존재하지 않는 콘서트의 스케줄을 조회할 때") {
            it("ConcertNotFoundException이 발생해야 한다") {
                // given
                val concertId = 999L
                
                every { concertRepository.findById(concertId) } returns null
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getSchedulesByConcertId(concertId)
                }
            }
        }
        
        context("콘서트는 있지만 스케줄이 없을 때") {
            it("빈 목록을 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = Concert(concertId, "Test Concert", "Test Artist")
                
                every { concertRepository.findById(concertId) } returns concert
                every { concertScheduleRepository.findByConcertId(concertId) } returns emptyList()
                
                // when
                val result = concertService.getSchedulesByConcertId(concertId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 0
            }
        }
    }
    
    describe("incrementPopularity") {
        context("콘서트 인기도를 증가시킬 때") {
            it("ConcertStatsService의 incrementViewCount가 호출되어야 한다") {
                // given
                val concertId = 1L
                
                every { concertStatsService.incrementViewCount(concertId) } returns Unit
                
                // when
                concertService.incrementPopularity(concertId)
                
                // then
                verify { concertStatsService.incrementViewCount(concertId) }
            }
        }
    }
    
    describe("decrementPopularity") {
        context("콘서트 인기도를 감소시킬 때") {
            it("ConcertStatsService의 decrementViewCount가 호출되어야 한다") {
                // given
                val concertId = 1L
                
                every { concertStatsService.decrementViewCount(concertId) } returns Unit
                
                // when
                concertService.decrementPopularity(concertId)
                
                // then
                verify { concertStatsService.decrementViewCount(concertId) }
            }
        }
    }
})