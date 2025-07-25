package kr.hhplus.be.server.concert.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.concert.entity.ConcertSchedule
import kr.hhplus.be.server.concert.entity.Seat
import kr.hhplus.be.server.concert.entity.SeatStatusType
import kr.hhplus.be.server.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import java.util.*

class SeatServiceTest : DescribeSpec({
    
    val seatJpaRepository = mockk<SeatJpaRepository>()
    val concertScheduleJpaRepository = mockk<ConcertScheduleJpaRepository>()
    val seatService = SeatService(seatJpaRepository, concertScheduleJpaRepository)
    
    describe("getAvailableSeats") {
        context("존재하는 스케줄의 예약 가능한 좌석을 조회할 때") {
            it("예약 가능한 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val schedule = mockk<ConcertSchedule>(relaxed = true)
                val seats = listOf(
                    mockk<Seat>(relaxed = true),
                    mockk<Seat>(relaxed = true)
                )
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.of(schedule)
                every { 
                    seatJpaRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId, SeatStatusType.AVAILABLE) 
                } returns seats
                
                // when
                val result = seatService.getAvailableSeats(scheduleId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
            }
        }
        
        context("존재하지 않는 스케줄로 조회할 때") {
            it("ConcertNotFoundException을 던져야 한다") {
                // given
                val scheduleId = 999L
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.empty()
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    seatService.getAvailableSeats(scheduleId)
                }
            }
        }
    }
    
    describe("getAllSeats") {
        context("존재하는 스케줄의 모든 좌석을 조회할 때") {
            it("모든 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val schedule = mockk<ConcertSchedule>(relaxed = true)
                val seats = listOf(
                    mockk<Seat>(relaxed = true),
                    mockk<Seat>(relaxed = true),
                    mockk<Seat>(relaxed = true)
                )
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.of(schedule)
                every { seatJpaRepository.findByScheduleId(scheduleId) } returns seats
                
                // when
                val result = seatService.getAllSeats(scheduleId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 3
            }
        }
        
        context("존재하지 않는 스케줄로 조회할 때") {
            it("ConcertNotFoundException을 던져야 한다") {
                // given
                val scheduleId = 999L
                
                every { concertScheduleJpaRepository.findById(scheduleId) } returns Optional.empty()
                
                // when & then
                shouldThrow<ConcertNotFoundException> {
                    seatService.getAllSeats(scheduleId)
                }
            }
        }
    }
    
    describe("getSeatById") {
        context("존재하는 좌석 ID로 조회할 때") {
            it("해당 좌석 정보를 반환해야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>(relaxed = true)
                
                every { seatJpaRepository.findById(seatId) } returns Optional.of(seat)
                
                // when
                val result = seatService.getSeatById(seatId)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("존재하지 않는 좌석 ID로 조회할 때") {
            it("SeatNotFoundException을 던져야 한다") {
                // given
                val seatId = 999L
                
                every { seatJpaRepository.findById(seatId) } returns Optional.empty()
                
                // when & then
                shouldThrow<SeatNotFoundException> {
                    seatService.getSeatById(seatId)
                }
            }
        }
    }
    
    describe("isSeatAvailable") {
        context("예약 가능한 좌석을 확인할 때") {
            it("true를 반환해야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>(relaxed = true)
                
                every { seatJpaRepository.findById(seatId) } returns Optional.of(seat)
                every { seat.isAvailable() } returns true
                
                // when
                val result = seatService.isSeatAvailable(seatId)
                
                // then
                result shouldBe true
            }
        }
        
        context("예약 불가능한 좌석을 확인할 때") {
            it("false를 반환해야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>(relaxed = true)
                
                every { seatJpaRepository.findById(seatId) } returns Optional.of(seat)
                every { seat.isAvailable() } returns false
                
                // when
                val result = seatService.isSeatAvailable(seatId)
                
                // then
                result shouldBe false
            }
        }
        
        context("존재하지 않는 좌석을 확인할 때") {
            it("SeatNotFoundException을 던져야 한다") {
                // given
                val seatId = 999L
                
                every { seatJpaRepository.findById(seatId) } returns Optional.empty()
                
                // when & then
                shouldThrow<SeatNotFoundException> {
                    seatService.isSeatAvailable(seatId)
                }
            }
        }
    }
    
    describe("getSeatsByNumberPattern") {
        context("좌석 번호 패턴으로 검색할 때") {
            it("패턴에 맞는 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val pattern = "A"
                val seats = listOf(
                    mockk<Seat>(relaxed = true),
                    mockk<Seat>(relaxed = true)
                )
                
                every { 
                    seatJpaRepository.findByScheduleIdAndSeatNumberContainingOrderBySeatNumberAsc(scheduleId, pattern) 
                } returns seats
                
                // when
                val result = seatService.getSeatsByNumberPattern(scheduleId, pattern)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
            }
        }
    }
    
    describe("confirmSeat") {
        context("예약 가능한 좌석을 확정할 때") {
            it("좌석을 확정 상태로 변경해야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>(relaxed = true)
                val confirmedSeat = mockk<Seat>(relaxed = true)
                
                every { seatJpaRepository.findById(seatId) } returns Optional.of(seat)
                every { seat.confirm() } returns confirmedSeat
                every { seatJpaRepository.save(confirmedSeat) } returns confirmedSeat
                
                // when
                val result = seatService.confirmSeat(seatId)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("존재하지 않는 좌석을 확정할 때") {
            it("SeatNotFoundException을 던져야 한다") {
                // given
                val seatId = 999L
                
                every { seatJpaRepository.findById(seatId) } returns Optional.empty()
                
                // when & then
                shouldThrow<SeatNotFoundException> {
                    seatService.confirmSeat(seatId)
                }
            }
        }
    }
})
