package kr.hhplus.be.server.domain.concert.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.domain.concert.repositories.SeatRepository
import kr.hhplus.be.server.domain.concert.repositories.SeatStatusTypePojoRepository
import java.math.BigDecimal
import java.time.LocalDate

class SeatGenerationServiceTest : BehaviorSpec({
    
    val seatRepository = mockk<SeatRepository>(relaxed = true)
    val seatStatusTypeRepository = mockk<SeatStatusTypePojoRepository>(relaxed = true)
    
    val seatGenerationService = SeatGenerationService(
        seatRepository,
        seatStatusTypeRepository
    )
    
    Given("좌석 생성 서비스에서") {
        
        When("콘서트 스케줄에 대한 좌석을 생성할 때") {
            val schedule = ConcertSchedule.create(
                concertId = 1L,
                concertDate = LocalDate.now().plusDays(7),
                venue = "올림픽공원",
                totalSeats = 50
            ).apply {
                scheduleId = 1L // ID 설정
            }
            
            val availableStatus = SeatStatusType.createDefault("AVAILABLE", "사용 가능", "NORMAL")
            val generatedSeats = (1..50).map { i ->
                Seat.create(
                    scheduleId = 1L,
                    seatNumber = String.format("%02d", i),
                    price = BigDecimal("100000"),
                    availableStatus = availableStatus
                )
            }
            
            every { seatStatusTypeRepository.getAvailableStatus() } returns availableStatus
            every { seatRepository.saveAll(any<List<Seat>>()) } returns generatedSeats
            
            val result = seatGenerationService.generateSeatsForSchedule(schedule)
            
            Then("50개의 좌석이 생성되어야 한다") {
                result.size shouldBe 50
                verify { seatStatusTypeRepository.getAvailableStatus() }
                verify { seatRepository.saveAll(any<List<Seat>>()) }
            }
        }
        
        When("기존 좌석이 있는지 확인할 때") {
            val scheduleId = 1L
            
            every { seatRepository.countByScheduleId(scheduleId) } returns 10
            
            val result = seatGenerationService.hasExistingSeats(scheduleId)
            
            Then("기존 좌석이 있다고 반환되어야 한다") {
                result shouldBe true
                verify { seatRepository.countByScheduleId(scheduleId) }
            }
        }
    }
})
