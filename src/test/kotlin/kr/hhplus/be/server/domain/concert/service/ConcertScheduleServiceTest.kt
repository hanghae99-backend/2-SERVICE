package kr.hhplus.be.server.domain.concert.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.concert.dto.request.ConcertScheduleCreateRequest
import kr.hhplus.be.server.domain.concert.exception.ConcertNotFoundException
import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.concert.repositories.ConcertRepository
import kr.hhplus.be.server.domain.concert.repositories.ConcertScheduleRepository
import kr.hhplus.be.server.domain.concert.rules.ConcertBusinessRules
import kr.hhplus.be.server.global.exception.ParameterValidationException
import kr.hhplus.be.server.global.properties.ConcertProperties
import java.math.BigDecimal
import java.time.LocalDate

class ConcertScheduleServiceTest : BehaviorSpec({
    
    val concertRepository = mockk<ConcertRepository>(relaxed = true)
    val concertScheduleRepository = mockk<ConcertScheduleRepository>(relaxed = true)
    val seatGenerationService = mockk<SeatGenerationService>(relaxed = true)
    val concertProperties = mockk<ConcertProperties>(relaxed = true)
    val concertBusinessRules = mockk<ConcertBusinessRules>(relaxed = true)
    
    val concertScheduleService = ConcertScheduleService(
        concertRepository,
        concertScheduleRepository,
        seatGenerationService,
        concertProperties,
        concertBusinessRules
    )
    
    Given("콘서트 스케줄 서비스에서") {
        
        When("콘서트 스케줄을 생성할 때") {
            val concertId = 1L
            val concertDate = LocalDate.now().plusDays(7) // 현재 날짜에서 7일 후
            val venue = "올림픽공원"
            val expectedSeats = 50
            
            val request = ConcertScheduleCreateRequest(
                concertId = concertId,
                concertDate = concertDate,
                venue = venue
            )
            
            val concert = Concert.create("Test Concert", "Test Artist")
            val savedSchedule = ConcertSchedule.create(concertId, concertDate, venue, expectedSeats).apply {
                scheduleId = 1L // 스케줄 ID 설정
            }
            val mockAvailableStatus = mockk<SeatStatusType>(relaxed = true)
            val generatedSeats = (1..expectedSeats).map { seatNum -> 
                Seat.create(1L, "A$seatNum", BigDecimal("50000"), mockAvailableStatus) 
            }
            
            every { concertProperties.defaultSeatsPerSchedule } returns expectedSeats
            every { concertRepository.findById(concertId) } returns concert
            every { concertBusinessRules.validateSeatCount(expectedSeats) } returns Unit
            every { concertBusinessRules.validateScheduleTime(any()) } returns Unit
            every { concertScheduleRepository.save(any<ConcertSchedule>()) } returns savedSchedule
            every { seatGenerationService.hasExistingSeats(savedSchedule.scheduleId) } returns false
            every { seatGenerationService.generateSeatsForSchedule(savedSchedule) } returns generatedSeats
            
            val result = concertScheduleService.createConcertSchedule(request)
            
            Then("콘서트 스케줄이 생성되고 좌석이 자동 생성되어야 한다") {
                result shouldNotBe null
                result.concertId shouldBe concertId
                result.venue shouldBe venue
                result.totalSeats shouldBe expectedSeats
                
                verify { concertRepository.findById(concertId) }
                verify { concertBusinessRules.validateSeatCount(expectedSeats) }
                verify { concertBusinessRules.validateScheduleTime(any()) }
                verify { concertScheduleRepository.save(any<ConcertSchedule>()) }
                verify { seatGenerationService.generateSeatsForSchedule(savedSchedule) }
            }
        }
        
        When("존재하지 않는 콘서트로 스케줄을 생성하려 할 때") {
            val request = ConcertScheduleCreateRequest(
                concertId = 999L,
                concertDate = LocalDate.now().plusDays(7),
                venue = "올림픽공원"
            )
            
            every { concertProperties.defaultSeatsPerSchedule } returns 50
            every { concertRepository.findById(999L) } returns null
            
            Then("ConcertNotFoundException이 발생해야 한다") {
                shouldThrow<ConcertNotFoundException> {
                    concertScheduleService.createConcertSchedule(request)
                }
            }
        }
        
        When("좌석 생성 개수가 예상과 다를 때") {
            val concertId = 1L
            val expectedSeats = 50
            val actualSeats = 30
            
            val request = ConcertScheduleCreateRequest(
                concertId = concertId,
                concertDate = LocalDate.now().plusDays(7),
                venue = "올림픽공원"
            )
            
            val concert = Concert.create("Test Concert", "Test Artist")
            val savedSchedule = ConcertSchedule.create(concertId, LocalDate.now().plusDays(7), "올림픽공원", expectedSeats).apply {
                scheduleId = 1L
            }
            val mockAvailableStatus = mockk<SeatStatusType>(relaxed = true)
            val generatedSeats = (1..actualSeats).map { seatNum -> 
                Seat.create(1L, "A$seatNum", BigDecimal("50000"), mockAvailableStatus) 
            }
            
            every { concertProperties.defaultSeatsPerSchedule } returns expectedSeats
            every { concertRepository.findById(concertId) } returns concert
            every { concertBusinessRules.validateSeatCount(expectedSeats) } returns Unit
            every { concertBusinessRules.validateScheduleTime(any()) } returns Unit
            every { concertScheduleRepository.save(any<ConcertSchedule>()) } returns savedSchedule
            every { seatGenerationService.hasExistingSeats(savedSchedule.scheduleId) } returns false
            every { seatGenerationService.generateSeatsForSchedule(savedSchedule) } returns generatedSeats
            
            Then("ParameterValidationException이 발생해야 한다") {
                shouldThrow<ParameterValidationException> {
                    concertScheduleService.createConcertSchedule(request)
                }
            }
        }
        
        When("기존 스케줄에 좌석을 생성할 때") {
            val scheduleId = 1L
            val schedule = ConcertSchedule.create(1L, LocalDate.now().plusDays(7), "올림픽공원", 50).apply {
                scheduleId
            }
            val mockAvailableStatus = mockk<SeatStatusType>(relaxed = true)
            val generatedSeats = (1..50).map { seatNum -> 
                Seat.create(1L, "A$seatNum", BigDecimal("50000"), mockAvailableStatus) 
            }
            
            every { concertScheduleRepository.findById(scheduleId) } returns schedule
            every { seatGenerationService.hasExistingSeats(scheduleId) } returns false
            every { seatGenerationService.generateSeatsForSchedule(schedule) } returns generatedSeats
            
            val result = concertScheduleService.generateSeatsForExistingSchedule(scheduleId)
            
            Then("좌석이 생성되어야 한다") {
                result.size shouldBe 50
                verify { seatGenerationService.generateSeatsForSchedule(schedule) }
            }
        }
        
        When("이미 좌석이 있는 스케줄에 좌석을 생성하려 할 때") {
            val scheduleId = 1L
            val schedule = ConcertSchedule.create(1L, LocalDate.now().plusDays(7), "올림픽공원", 50)
            
            every { concertScheduleRepository.findById(scheduleId) } returns schedule
            every { seatGenerationService.hasExistingSeats(scheduleId) } returns true
            
            Then("IllegalStateException이 발생해야 한다") {
                shouldThrow<IllegalStateException> {
                    concertScheduleService.generateSeatsForExistingSchedule(scheduleId)
                }
            }
        }
        
        When("스케줄을 삭제할 때") {
            val scheduleId = 1L
            val schedule = ConcertSchedule.create(1L, LocalDate.now().plusDays(7), "올림픽공원", 50)
            
            every { concertScheduleRepository.findById(scheduleId) } returns schedule
            every { seatGenerationService.getSeatCount(scheduleId) } returns 0
            every { concertScheduleRepository.delete(schedule) } returns Unit
            
            concertScheduleService.deleteSchedule(scheduleId)
            
            Then("스케줄이 삭제되어야 한다") {
                verify { concertScheduleRepository.delete(schedule) }
            }
        }
        
        When("좌석이 있는 스케줄을 삭제하려 할 때") {
            val scheduleId = 1L
            val schedule = ConcertSchedule.create(1L, LocalDate.now().plusDays(7), "올림픽공원", 50)
            
            every { concertScheduleRepository.findById(scheduleId) } returns schedule
            every { seatGenerationService.getSeatCount(scheduleId) } returns 10
            
            Then("IllegalStateException이 발생해야 한다") {
                shouldThrow<IllegalStateException> {
                    concertScheduleService.deleteSchedule(scheduleId)
                }
            }
        }
    }
})
