package kr.hhplus.be.server.domain.concert.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.exception.SeatNotFoundException
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import java.math.BigDecimal
import java.time.LocalDateTime

class SeatServiceTest : DescribeSpec({
    
    val seatRepository = mockk<SeatRepository>()
    val concertScheduleRepository = mockk<ConcertScheduleRepository>()
    val seatStatusTypePojoRepository = mockk<SeatStatusTypePojoRepository>()
    
    val seatService = SeatService(
        seatRepository,
        concertScheduleRepository,
        seatStatusTypePojoRepository
    )
    
    describe("getAvailableSeats") {
        context("존재하는 스케줄의 예약 가능한 좌석을 조회할 때") {
            it("예약 가능한 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val schedule = ConcertSchedule(
                    scheduleId = scheduleId,
                    concertId = 1L,
                    concertDate = LocalDateTime.now().toLocalDate(),
                    venue = "Test Venue",
                    totalSeats = 100,
                    availableSeats = 50,
                )
                val availableStatus = SeatStatusType("AVAILABLE", "예약가능", "예약 가능한 좌석", true, LocalDateTime.now())
                val seat1 = Seat(1L, scheduleId, "A1", "NORMAL", BigDecimal("100000"), availableStatus)
                val seat2 = Seat(2L, scheduleId, "A2", "NORMAL", BigDecimal("100000"), availableStatus)
                val seats = listOf(seat1, seat2)
                
                every { concertScheduleRepository.findById(scheduleId) } returns schedule
                every { seatStatusTypePojoRepository.getAvailableStatus() } returns availableStatus
                every { 
                    seatRepository.findByScheduleIdAndStatusCodeOrderBySeatNumberAsc(scheduleId, availableStatus.code) 
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
                
                every { concertScheduleRepository.findById(scheduleId) } returns null
                
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
                val schedule = ConcertSchedule(
                    scheduleId = scheduleId,
                    concertId = 1L,
                    concertDate = LocalDateTime.now().toLocalDate(),
                    venue = "Test Venue",
                    totalSeats = 100,
                    availableSeats = 50
                )
                val availableStatus = SeatStatusType("AVAILABLE", "예약가능", "예약 가능한 좌석", true, LocalDateTime.now())
                val occupiedStatus = SeatStatusType("OCCUPIED", "점유", "점유된 좌석", true, LocalDateTime.now())
                val seats = listOf(

                    Seat(1L, scheduleId, "A1", "NORMAL", BigDecimal("100000"), availableStatus),
                    Seat(2L, scheduleId, "A2", "NORMAL", BigDecimal("100000"), occupiedStatus),
                    Seat(3L, scheduleId, "A3", "NORMAL", BigDecimal("100000"), availableStatus)
                )
                
                every { concertScheduleRepository.findById(scheduleId) } returns schedule
                every { seatRepository.findByScheduleId(scheduleId) } returns seats
                
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
                
                every { concertScheduleRepository.findById(scheduleId) } returns null
                
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
                val availableStatus = SeatStatusType("AVAILABLE", "예약가능", "예약 가능한 좌석", true, LocalDateTime.now())
                val seat = Seat(seatId, 1L, "A1", "NORMAL", BigDecimal("100000"), availableStatus)
                
                every { seatRepository.findById(seatId) } returns seat
                
                // when
                val result = seatService.getSeatById(seatId)
                
                // then
                result shouldNotBe null
                result.seatId shouldBe seatId
            }
        }
        
        context("존재하지 않는 좌석 ID로 조회할 때") {
            it("SeatNotFoundException을 던져야 한다") {
                // given
                val seatId = 999L
                
                every { seatRepository.findById(seatId) } returns null
                
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
                val availableStatus = SeatStatusType("AVAILABLE", "예약가능", "예약 가능한 좌석", true, LocalDateTime.now())
                val seat = mockk<Seat>()
                
                every { seatRepository.findById(seatId) } returns seat
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
                val seat = mockk<Seat>()
                
                every { seatRepository.findById(seatId) } returns seat
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
                
                every { seatRepository.findById(seatId) } returns null
                
                // when & then
                shouldThrow<SeatNotFoundException> {
                    seatService.isSeatAvailable(seatId)
                }
            }
        }
    }

    describe("confirmSeat") {
        context("예약 가능한 좌석을 확정할 때") {
            it("좌석을 확정 상태로 변경해야 한다") {
                // given
                val seatId = 1L
                val availableStatus = SeatStatusType("AVAILABLE", "예약가능", "예약 가능한 좌석", true, LocalDateTime.now())
                val reservedStatus = SeatStatusType("RESERVED", "임시예약", "임시 예약된 좌석", true, LocalDateTime.now())
                val occupiedStatus = SeatStatusType("OCCUPIED", "점유", "점유된 좌석", true, LocalDateTime.now())
                val seat = Seat(seatId, 1L, "A1", "NORMAL", BigDecimal("100000"), reservedStatus)
                val confirmedSeat = seat.confirm(occupiedStatus)
                
                every { seatRepository.findByIdWithPessimisticLock(seatId) } returns seat
                every { seatStatusTypePojoRepository.getOccupiedStatus() } returns occupiedStatus
                every { seatRepository.save(any()) } returns confirmedSeat
                
                // when
                val result = seatService.confirmSeat(seatId)
                
                // then
                result shouldNotBe null
                verify { seatRepository.save(any()) }
            }
        }
        
        context("존재하지 않는 좌석을 확정할 때") {
            it("SeatNotFoundException을 던져야 한다") {
                // given
                val seatId = 999L
                
                every { seatRepository.findByIdWithPessimisticLock(seatId) } returns null
                
                // when & then
                shouldThrow<SeatNotFoundException> {
                    seatService.confirmSeat(seatId)
                }
            }
        }
    }
})