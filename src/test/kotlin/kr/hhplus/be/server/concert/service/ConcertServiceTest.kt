package kr.hhplus.be.server.concert.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.repository.*
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.service.ConcertService
import java.time.LocalDate

class ConcertServiceTest : DescribeSpec({
    
    val concertRepository = mockk<ConcertRepository>()
    val concertScheduleRepository = mockk<ConcertScheduleRepository>()
    val seatRepository = mockk<SeatRepository>()
    
    val concertService = ConcertService(
        concertRepository,
        concertScheduleRepository,
        seatRepository
    )
    
    describe("getConcertById") {
        context("존재하는 콘서트 ID로 조회할 때") {
            it("해당 콘서트 정보를 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = mockk<Concert>(relaxed = true)
                
                every { concertRepository.findById(concertId) } returns concert
                
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
                    concertScheduleRepository.findByConcertDateBetweenAndAvailableSeatsGreaterThanOrderByConcertDateAsc(
                        startDate, endDate, 0
                    ) 
                } returns schedules
                every { concertRepository.findById(1L) } returns concerts[0]
                every { concertRepository.findById(2L) } returns concerts[1]
                
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
})
