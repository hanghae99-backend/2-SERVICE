package kr.hhplus.be.server.api.concert.usecase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.api.concert.dto.SeatDto
import java.math.BigDecimal

class SeatUseCaseTest : DescribeSpec({
    
    val seatService = mockk<SeatService>()
    val distributedLock = mockk<DistributedLock>()
    val eventPublisher = mockk<DomainEventPublisher>()
    
    val seatUseCase = SeatUseCase(
        seatService,
        distributedLock,
        eventPublisher
    )
    
    // DistributedLock executeWithLock 메서드의 기본 동작 설정
    fun setupDistributedLockMock() {
        every { 
            distributedLock.executeWithLock<Any>(
                lockKey = any(),
                lockTimeoutMs = any(),
                waitTimeoutMs = any(),
                action = any()
            )
        } answers {
            val action = args[3] as () -> Any
            action.invoke()
        }
    }
    
    // EventPublisher mock 설정
    fun setupEventPublisherMock() {
        justRun { eventPublisher.publish(any()) }
    }
    
    describe("getAvailableSeats") {
        context("특정 스케줄의 예약 가능한 좌석을 조회할 때") {
            it("예약 가능한 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val availableSeats = listOf(
                    SeatDto(1L, scheduleId, "A1", BigDecimal("100000"), "AVAILABLE"),
                    SeatDto(2L, scheduleId, "A2", BigDecimal("100000"), "AVAILABLE")
                )
                
                every { seatService.getAvailableSeats(scheduleId) } returns availableSeats
                
                // when
                val result = seatUseCase.getAvailableSeats(scheduleId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                verify { seatService.getAvailableSeats(scheduleId) }
            }
        }
    }
    
    describe("getAllSeats") {
        context("특정 스케줄의 모든 좌석을 조회할 때") {
            it("모든 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val allSeats = listOf(
                    SeatDto(1L, scheduleId, "A1", BigDecimal("100000"), "AVAILABLE"),
                    SeatDto(2L, scheduleId, "A2", BigDecimal("100000"), "OCCUPIED"),
                    SeatDto(3L, scheduleId, "A3", BigDecimal("100000"), "AVAILABLE")
                )
                
                every { seatService.getAllSeats(scheduleId) } returns allSeats
                
                // when
                val result = seatUseCase.getAllSeats(scheduleId)
                
                // then
                result shouldNotBe null
                result.size shouldBe 3
                verify { seatService.getAllSeats(scheduleId) }
            }
        }
    }
    
    describe("getSeatById") {
        context("존재하는 좌석 ID로 조회할 때") {
            it("해당 좌석 정보를 반환해야 한다") {
                // given
                val seatId = 1L
                val seat = SeatDto(seatId, 1L, "A1", BigDecimal("100000"), "AVAILABLE")
                
                every { seatService.getSeatById(seatId) } returns seat
                
                // when
                val result = seatUseCase.getSeatById(seatId)
                
                // then
                result shouldNotBe null
                result.seatId shouldBe seatId
                verify { seatService.getSeatById(seatId) }
            }
        }
    }
    
    describe("isSeatAvailable") {
        context("예약 가능한 좌석을 확인할 때") {
            it("true를 반환해야 한다") {
                // given
                val seatId = 1L
                
                every { seatService.isSeatAvailable(seatId) } returns true
                
                // when
                val result = seatUseCase.isSeatAvailable(seatId)
                
                // then
                result shouldBe true
                verify { seatService.isSeatAvailable(seatId) }
            }
        }
        
        context("예약 불가능한 좌석을 확인할 때") {
            it("false를 반환해야 한다") {
                // given
                val seatId = 1L
                
                every { seatService.isSeatAvailable(seatId) } returns false
                
                // when
                val result = seatUseCase.isSeatAvailable(seatId)
                
                // then
                result shouldBe false
                verify { seatService.isSeatAvailable(seatId) }
            }
        }
    }
    
    describe("getSeatsByNumberPattern") {
        context("좌석 번호 패턴으로 검색할 때") {
            it("패턴에 맞는 좌석 목록을 반환해야 한다") {
                // given
                val scheduleId = 1L
                val pattern = "A"
                val matchingSeats = listOf(
                    SeatDto(1L, scheduleId, "A1", BigDecimal("100000"), "AVAILABLE"),
                    SeatDto(2L, scheduleId, "A2", BigDecimal("100000"), "AVAILABLE")
                )
                
                every { seatService.getSeatsByNumberPattern(scheduleId, pattern) } returns matchingSeats
                
                // when
                val result = seatUseCase.getSeatsByNumberPattern(scheduleId, pattern)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                verify { seatService.getSeatsByNumberPattern(scheduleId, pattern) }
            }
        }
    }
    
    describe("confirmSeat") {
        context("예약 가능한 좌석을 확정할 때") {
            it("좌석을 확정 상태로 변경하고 이벤트를 발행해야 한다") {
                // given
                val seatId = 1L
                val previousSeat = SeatDto(seatId, 1L, "A1", BigDecimal("100000"), "AVAILABLE")
                val confirmedSeat = SeatDto(seatId, 1L, "A1", BigDecimal("100000"), "OCCUPIED")
                
                setupDistributedLockMock()
                setupEventPublisherMock()
                every { seatService.getSeatById(seatId) } returns previousSeat
                every { seatService.confirmSeat(seatId) } returns confirmedSeat
                
                // when
                val result = seatUseCase.confirmSeat(seatId)
                
                // then
                result shouldNotBe null
                result.statusCode shouldBe "OCCUPIED"
                verify { seatService.getSeatById(seatId) }
                verify { seatService.confirmSeat(seatId) }
                verify { eventPublisher.publish(any()) }
            }
        }
    }
})