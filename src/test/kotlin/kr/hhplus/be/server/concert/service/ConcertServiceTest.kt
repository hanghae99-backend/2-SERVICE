package kr.hhplus.be.server.concert.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.concert.entity.Concert
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.entity.Seat
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import java.time.LocalDate
import java.util.*

class ConcertServiceTest : DescribeSpec({
    
    val concertJpaRepository = mockk<ConcertJpaRepository>()
    val concertScheduleJpaRepository = mockk<ConcertScheduleJpaRepository>()
    val seatJpaRepository = mockk<SeatJpaRepository>()
    
    val concertService = ConcertService(
        concertJpaRepository, 
        concertScheduleJpaRepository, 
        seatJpaRepository
    )
    
    describe("getAvailableConcerts") {
        context("예약 가능한 콘서트 목록을 조회할 때") {
            it("예약 가능한 콘서트 스케줄을 반환해야 한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                val schedules = listOf(
                    mockk<ConcertSchedule>(relaxed = true) {
                        every { concertId } returns 1L
                    },
                    mockk<ConcertSchedule>(relaxed = true) {
                        every { concertId } returns 2L
                    }
                )
                val concerts = listOf(
                    mockk<Concert>(relaxed = true) {
                        every { concertId } returns 1L
                    },
                    mockk<Concert>(relaxed = true) {
                        every { concertId } returns 2L
                    }
                )
                
                every { 
                    concertScheduleJpaRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
                        startDate, endDate, 0
                    ) 
                } returns schedules
                every { concertJpaRepository.findAllById(listOf(1L, 2L)) } returns concerts
                
                // when
                val result = concertService.getAvailableConcerts(startDate, endDate)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
            }
        }
        
        context("예약 가능한 콘서트가 없을 때") {
            it("빈 목록을 반환해야 한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                
                every { 
                    concertScheduleJpaRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
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
    
    describe("getConcertsByDate") {
        context("특정 날짜의 콘서트를 조회할 때") {
            it("해당 날짜의 콘서트 목록을 반환해야 한다") {
                // given
                val date = LocalDate.now()
                val schedules = listOf(
                    mockk<ConcertSchedule>(relaxed = true) {
                        every { concertId } returns 1L
                    }
                )
                val concerts = listOf(
                    mockk<Concert>(relaxed = true) {
                        every { concertId } returns 1L
                    }
                )
                
                every { concertScheduleJpaRepository.findByConcertDate(date) } returns schedules
                every { concertJpaRepository.findAllById(listOf(1L)) } returns concerts
                
                // when
                val result = concertService.getConcertsByDate(date)
                
                // then
                result shouldNotBe null
                result.size shouldBe 1
            }
        }
        
        context("해당 날짜에 콘서트가 없을 때") {
            it("빈 목록을 반환해야 한다") {
                // given
                val date = LocalDate.now()
                
                every { concertScheduleJpaRepository.findByConcertDate(date) } returns emptyList()
                
                // when
                val result = concertService.getConcertsByDate(date)
                
                // then
                result shouldNotBe null
                result.size shouldBe 0
            }
        }
    }
    
    describe("getConcertById") {
        context("존재하는 콘서트 ID로 조회할 때") {
            it("해당 콘서트 정보를 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = mockk<Concert>(relaxed = true)
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                
                // when
                val result = concertService.getConcertById(concertId)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("존재하지 않는 콘서트 ID로 조회할 때") {
            it("ConcertNotFoundException을 던져야 한다") {
                // given
                val concertId = 999L
                
                every { concertJpaRepository.findById(concertId) } returns Optional.empty()
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertById(concertId)
                }
            }
        }
    }
    
    describe("getConcertScheduleById") {
        context("존재하는 스케줄 ID로 조회할 때") {
            it("해당 스케줄 정보를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val concertId = 1L
                val schedule = mockk<ConcertSchedule>(relaxed = true) {
                    every { this@mockk.concertId } returns concertId
                }
                val concert = mockk<Concert>(relaxed = true)
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.of(schedule)
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                
                // when
                val result = concertService.getConcertScheduleById(scheduleId)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("존재하지 않는 스케줄 ID로 조회할 때") {
            it("ConcertNotFoundException을 던져야 한다") {
                // given
                val scheduleId = 999L
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.empty()
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertScheduleById(scheduleId)
                }
            }
        }
        
        context("스케줄은 존재하지만 콘서트가 존재하지 않을 때") {
            it("ConcertNotFoundException을 던져야 한다") {
                // given
                val scheduleId = 1L
                val concertId = 999L
                val schedule = mockk<ConcertSchedule>(relaxed = true) {
                    every { this@mockk.concertId } returns concertId
                }
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.of(schedule)
                every { concertJpaRepository.findById(concertId) } returns Optional.empty()
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertScheduleById(scheduleId)
                }
            }
        }
    }
    
    describe("getConcertDetailByScheduleId") {
        context("유효한 스케줄 ID로 상세 정보를 조회할 때") {
            it("콘서트, 스케줄, 좌석 정보를 포함한 상세 정보를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val concertId = 1L
                val schedule = mockk<ConcertSchedule>(relaxed = true) {
                    every { this@mockk.concertId } returns concertId
                }
                val concert = mockk<Concert>(relaxed = true)
                val seats = listOf(mockk<Seat>(relaxed = true), mockk<Seat>(relaxed = true))
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.of(schedule)
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                every { seatJpaRepository.findByScheduleId(scheduleId) } returns seats
                
                // when
                val result = concertService.getConcertDetailByScheduleId(scheduleId)
                
                // then
                result shouldNotBe null
            }
        }
    }
    
    describe("getSchedulesByConcertId") {
        context("존재하는 콘서트의 모든 스케줄을 조회할 때") {
            it("해당 콘서트의 모든 스케줄을 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = mockk<Concert>(relaxed = true)
                val schedules = listOf(mockk<ConcertSchedule>(relaxed = true), mockk<ConcertSchedule>(relaxed = true))
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                every { concertScheduleJpaRepository.findByConcertId(concertId) } returns schedules
                
                // when
                val result = concertService.getSchedulesByConcertId(concertId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
            }
        }
        
        context("존재하지 않는 콘서트의 스케줄을 조회할 때") {
            it("ConcertNotFoundException을 던져야 한다") {
                // given
                val concertId = 999L
                
                every { concertJpaRepository.findById(concertId) } returns Optional.empty()
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    concertService.getSchedulesByConcertId(concertId)
                }
            }
        }
    }
    
    describe("getAvailableSchedulesByConcertId") {
        context("예약 가능한 스케줄을 조회할 때") {
            it("예약 가능한 스케줄만 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = mockk<Concert>(relaxed = true)
                val availableSchedules = listOf(mockk<ConcertSchedule>(relaxed = true))
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(concert)
                every { 
                    concertScheduleJpaRepository.findByConcertIdAndAvailableSeatsGreaterThanAndConcertDateGreaterThanEqualOrderByConcertDateAsc(
                        concertId, 0, any()
                    ) 
                } returns availableSchedules
                
                // when
                val result = concertService.getAvailableSchedulesByConcertId(concertId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 1
            }
        }
    }
})
