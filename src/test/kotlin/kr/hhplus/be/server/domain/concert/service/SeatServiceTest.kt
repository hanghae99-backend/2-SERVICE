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
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.config.TestDataConstants
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
                val availableStatus = TestDataFixture.createSeatStatusType()
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
                val availableStatus = TestDataFixture.createSeatStatusType()
                val occupiedStatus = TestDataFixture.createSeatStatusType(
                    code = TestDataConstants.SeatStatusType.OCCUPIED.code,
                    name = TestDataConstants.SeatStatusType.OCCUPIED.name,
                    description = TestDataConstants.SeatStatusType.OCCUPIED.description
                )
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
                val availableStatus = TestDataFixture.createSeatStatusType()
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
                val reservedStatus = TestDataFixture.createSeatStatusType(
                    code = TestDataConstants.SeatStatusType.RESERVED.code,
                    name = TestDataConstants.SeatStatusType.RESERVED.name,
                    description = TestDataConstants.SeatStatusType.RESERVED.description
                )
                val occupiedStatus = TestDataFixture.createSeatStatusType(
                    code = TestDataConstants.SeatStatusType.OCCUPIED.code,
                    name = TestDataConstants.SeatStatusType.OCCUPIED.name,
                    description = TestDataConstants.SeatStatusType.OCCUPIED.description
                )
                val seat = mockk<Seat>(relaxed = true)

                every { seatRepository.findById(seatId) } returns seat
                every { seatStatusTypePojoRepository.getOccupiedStatus() } returns occupiedStatus
                every { seat.confirm(occupiedStatus) } returns Unit
                every { seatRepository.save(seat) } returns seat

                // when
                val result = seatService.confirmSeat(seatId)

                // then
                result shouldNotBe null
                verify { seat.confirm(occupiedStatus) }
                verify { seatRepository.save(seat) }
            }
        }
        
        context("존재하지 않는 좌석을 확정할 때") {
            it("SeatNotFoundException을 던져야 한다") {
                // given
                val seatId = 999L
                
                every { seatRepository.findById(seatId) } returns null
                
                // when & then
                shouldThrow<SeatNotFoundException> {
                    seatService.confirmSeat(seatId)
                }
            }
        }
    }
    
    describe("getSeatLayout") {
        context("스케줄의 좌석 배치를 조회할 때") {
            it("모든 좌석을 LAYOUT 상태로 반환해야 한다") {
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
                val availableStatus = TestDataFixture.createSeatStatusType()
                val seats = listOf(
                    Seat(1L, scheduleId, "A1", "NORMAL", BigDecimal("100000"), availableStatus),
                    Seat(2L, scheduleId, "A2", "NORMAL", BigDecimal("100000"), availableStatus)
                )
                
                every { concertScheduleRepository.findById(scheduleId) } returns schedule
                every { seatRepository.findByScheduleId(scheduleId) } returns seats
                
                // when
                val result = seatService.getSeatLayout(scheduleId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                result.forEach { seat ->
                    seat.statusCode shouldBe "LAYOUT"
                }
            }
        }
    }
    
    describe("validateSeatAvailability") {
        context("예약 가능한 좌석을 검증할 때") {
            it("예외가 발생하지 않아야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>()
                
                every { seatRepository.findById(seatId) } returns seat
                every { seat.isAvailable() } returns true
                
                // when & then
                seatService.validateSeatAvailability(seatId)
                
                verify { seat.isAvailable() }
            }
        }
        
        context("예약 불가능한 좌석을 검증할 때") {
            it("IllegalStateException이 발생해야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>()
                
                every { seatRepository.findById(seatId) } returns seat
                every { seat.isAvailable() } returns false
                
                // when & then
                shouldThrow<IllegalStateException> {
                    seatService.validateSeatAvailability(seatId)
                }
            }
        }
    }
    
    describe("reserveSeat") {
        context("예약 가능한 좌석을 예약할 때") {
            it("좌석을 예약 상태로 변경해야 한다") {
                // given
                val seatId = 1L
                val availableStatus = TestDataFixture.createSeatStatusType()
                val reservedStatus = TestDataFixture.createSeatStatusType(
                    code = TestDataConstants.SeatStatusType.RESERVED.code,
                    name = TestDataConstants.SeatStatusType.RESERVED.name,
                    description = TestDataConstants.SeatStatusType.RESERVED.description
                )
                val seat = mockk<Seat>(relaxed = true)
                
                every { seatRepository.findById(seatId) } returns seat
                every { seat.isAvailable() } returns true
                every { seatStatusTypePojoRepository.getReservedStatus() } returns reservedStatus
                every { seat.reserve(reservedStatus) } returns Unit
                every { seatRepository.save(seat) } returns seat
                
                // when
                val result = seatService.reserveSeat(seatId)
                
                // then
                result shouldNotBe null
                verify { seat.reserve(reservedStatus) }
                verify { seatRepository.save(seat) }
            }
        }
        
        context("이미 예약된 좌석을 예약할 때") {
            it("IllegalStateException이 발생해야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>()
                
                every { seatRepository.findById(seatId) } returns seat
                every { seat.isAvailable() } returns false
                
                // when & then
                shouldThrow<IllegalStateException> {
                    seatService.reserveSeat(seatId)
                }
            }
        }
    }
    
    describe("releaseSeat") {
        context("예약된 좌석을 해제할 때") {
            it("좌석을 사용 가능 상태로 변경해야 한다") {
                // given
                val seatId = 1L
                val availableStatus = TestDataFixture.createSeatStatusType()
                val seat = mockk<Seat>(relaxed = true)
                
                every { seatRepository.findById(seatId) } returns seat
                every { seat.isReserved() } returns true
                every { seatStatusTypePojoRepository.getAvailableStatus() } returns availableStatus
                every { seat.release(availableStatus) } returns Unit
                every { seatRepository.save(seat) } returns seat
                
                // when
                val result = seatService.releaseSeat(seatId)
                
                // then
                result shouldNotBe null
                verify { seat.release(availableStatus) }
                verify { seatRepository.save(seat) }
            }
        }
        
        context("예약 상태가 아닌 좌석을 해제할 때") {
            it("IllegalStateException이 발생해야 한다") {
                // given
                val seatId = 1L
                val seat = mockk<Seat>()
                
                every { seatRepository.findById(seatId) } returns seat
                every { seat.isReserved() } returns false
                
                // when & then
                shouldThrow<IllegalStateException> {
                    seatService.releaseSeat(seatId)
                }
            }
        }
        
        context("존재하지 않는 좌석을 해제할 때") {
            it("SeatNotFoundException이 발생해야 한다") {
                // given
                val seatId = 999L
                
                every { seatRepository.findById(seatId) } returns null
                
                // when & then
                shouldThrow<SeatNotFoundException> {
                    seatService.releaseSeat(seatId)
                }
            }
        }
    }
})
