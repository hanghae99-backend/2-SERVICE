package kr.hhplus.be.server.api.concert.usecase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.api.concert.usecase.SeatUseCase
import kr.hhplus.be.server.domain.concert.service.SeatService
import java.math.BigDecimal
import java.time.LocalDate

class ConcertUseCaseTest : DescribeSpec({
    
    val concertService = mockk<ConcertService>()
    val seatService = mockk<SeatService>()
    
    val concertUseCase = ConcertUseCase(
        concertService,
        seatService
    )
    
    describe("getAvailableConcerts") {
        context("예약 가능한 콘서트를 조회할 때") {
            it("예약 가능한 콘서트 목록을 반환해야 한다") {
                // given
                val startDate = LocalDate.now()
                val endDate = LocalDate.now().plusMonths(3)
                val concerts = listOf(
                    ConcertScheduleWithInfoDto(
                        scheduleId = 1L,
                        concertId = 1L,
                        title = "Test Concert",
                        artist = "Test Artist", // concertTitle -> title, artist 추가
                        venue = "Test Venue",
                        concertDate = startDate, // performanceDate -> concertDate
                        totalSeats = 100,
                        availableSeats = 100
                    )
                )
                
                every { concertService.getAvailableConcerts(startDate, endDate) } returns concerts
                
                // when
                val result = concertUseCase.getAvailableConcerts(startDate, endDate)
                
                // then
                result shouldNotBe null
                result.size shouldBe 1
                verify { concertService.getAvailableConcerts(startDate, endDate) }
            }
        }
    }
    
    describe("getConcertsByDate") {
        context("특정 날짜의 콘서트를 조회할 때") {
            it("해당 날짜의 콘서트 목록을 반환해야 한다") {
                // given
                val date = LocalDate.now()
                val concerts = listOf(
                    ConcertScheduleWithInfoDto(
                        scheduleId = 1L,
                        concertId = 1L,
                        title = "Test Concert",
                        artist = "Test Artist",
                        venue = "Test Venue",
                        concertDate = date, // performanceDate -> concertDate
                        totalSeats = 100,
                        availableSeats = 100
                    )
                )
                
                every { concertService.getConcertsByDate(date) } returns concerts
                
                // when
                val result = concertUseCase.getConcertsByDate(date)
                
                // then
                result shouldNotBe null
                result.size shouldBe 1
                verify { concertService.getConcertsByDate(date) }
            }
        }
    }
    
    describe("getConcertById") {
        context("존재하는 콘서트 ID로 조회할 때") {
            it("해당 콘서트 정보를 반환해야 한다") {
                // given
                val concertId = 1L
                val concert = ConcertDto(
                    concertId = concertId,
                    title = "Test Concert",
                    artist = "Test Artist" // description -> artist
                )
                
                every { concertService.getConcertById(concertId) } returns concert
                
                // when
                val result = concertUseCase.getConcertById(concertId)
                
                // then
                result shouldNotBe null
                verify { concertService.getConcertById(concertId) }
            }
        }
    }
    
    describe("getConcertScheduleById") {
        context("존재하는 스케줄 ID로 조회할 때") {
            it("해당 스케줄 정보를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val schedule = ConcertScheduleWithInfoDto(
                    scheduleId = scheduleId,
                    concertId = 1L,
                    title = "Test Concert",
                    artist = "Test Artist",
                    venue = "Test Venue",
                    concertDate = LocalDate.now(),
                    totalSeats = 100,
                    availableSeats = 50
                )
                
                every { concertService.getConcertScheduleById(scheduleId) } returns schedule
                
                // when
                val result = concertUseCase.getConcertScheduleById(scheduleId)
                
                // then
                result shouldNotBe null
                verify { concertService.getConcertScheduleById(scheduleId) }
            }
        }
    }
    
    describe("getConcertDetailByScheduleId") {
        context("스케줄 ID로 콘서트 상세 정보를 조회할 때") {
            it("해당 콘서트 상세 정보를 반환해야 한다") {
                // given
                val scheduleId = 1L
                val concertDto = ConcertDto(1L, "Test Concert", "Test Artist")
                val scheduleDto = ConcertScheduleDto(
                    scheduleId = scheduleId,
                    concertId = 1L,
                    venue = "Test Venue",
                    concertDate = LocalDate.now(),
                    totalSeats = 100,
                    availableSeats = 50
                )
                val seats = listOf(
                    SeatDto(1L, scheduleId, "A1", BigDecimal("100000"), "AVAILABLE")
                )
                val detail = ConcertDetailDto(
                    concert = concertDto,
                    schedule = scheduleDto,
                    seats = seats
                )
                
                every { concertService.getConcertDetailByScheduleId(scheduleId) } returns detail
                
                // when
                val result = concertUseCase.getConcertDetailByScheduleId(scheduleId)
                
                // then
                result shouldNotBe null
                verify { concertService.getConcertDetailByScheduleId(scheduleId) }
            }
        }
    }
    
    describe("getSchedulesByConcertId") {
        context("콘서트 ID로 스케줄 목록을 조회할 때") {
            it("해당 콘서트의 스케줄 목록을 반환해야 한다") {
                // given
                val concertId = 1L
                val schedules = listOf(
                    mockk<ConcertWithScheduleDto>(relaxed = true)
                )
                
                every { concertService.getSchedulesByConcertId(concertId) } returns schedules
                
                // when
                val result = concertUseCase.getSchedulesByConcertId(concertId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 1
                verify { concertService.getSchedulesByConcertId(concertId) }
            }
        }
    }
    
    describe("getAvailableSchedulesByConcertId") {
        context("콘서트 ID로 예약 가능한 스케줄 목록을 조회할 때") {
            it("해당 콘서트의 예약 가능한 스케줄 목록을 반환해야 한다") {
                // given
                val concertId = 1L
                val schedules = listOf(
                    mockk<ConcertWithScheduleDto>(relaxed = true)
                )
                
                every { concertService.getAvailableSchedulesByConcertId(concertId) } returns schedules
                
                // when
                val result = concertUseCase.getAvailableSchedulesByConcertId(concertId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 1
                verify { concertService.getAvailableSchedulesByConcertId(concertId) }
            }
        }
    }
    
    describe("getAvailableSeats") {
        context("스케줄의 예약 가능한 좌석을 조회할 때") {
            it("예약 가능한 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val seats = listOf(
                    SeatDto(1L, scheduleId, "A1", BigDecimal("100000"), "AVAILABLE")
                )
                
                every { seatService.getAvailableSeats(scheduleId) } returns seats
                
                // when
                val result = concertUseCase.getAvailableSeats(scheduleId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 1
                verify { seatService.getAvailableSeats(scheduleId) }
            }
        }
    }
    
    describe("getAllSeats") {
        context("스케줄의 모든 좌석을 조회할 때") {
            it("모든 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val seats = listOf(
                    SeatDto(1L, scheduleId, "A1", BigDecimal("100000"), "AVAILABLE"),
                    SeatDto(2L, scheduleId, "A2", BigDecimal("100000"), "OCCUPIED"),
                    SeatDto(3L, scheduleId, "A3", BigDecimal("100000"), "AVAILABLE")
                )
                
                every { seatService.getAllSeats(scheduleId) } returns seats
                
                // when
                val result = concertUseCase.getAllSeats(scheduleId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 3
                verify { seatService.getAllSeats(scheduleId) }
            }
        }
    }
})