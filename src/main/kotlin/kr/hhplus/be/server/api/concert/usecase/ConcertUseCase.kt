package kr.hhplus.be.server.api.concert.usecase

import kr.hhplus.be.server.api.concert.dto.*
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate


@Service
@Transactional(readOnly = true)
class ConcertUseCase(
    private val concertService: ConcertService,
    private val seatService: SeatService
) {
    

    fun getAvailableConcerts(
        startDate: LocalDate = LocalDate.now(), 
        endDate: LocalDate = LocalDate.now().plusMonths(3)
    ): List<ConcertScheduleWithInfoDto> {
        return concertService.getAvailableConcerts(startDate, endDate)
    }
    

    fun getConcertsByDate(date: LocalDate): List<ConcertScheduleWithInfoDto> {
        return concertService.getConcertsByDate(date)
    }
    

    fun getConcertById(concertId: Long): ConcertDto {
        return concertService.getConcertById(concertId)
    }
    

    fun getConcertScheduleById(scheduleId: Long): ConcertScheduleWithInfoDto {
        return concertService.getConcertScheduleById(scheduleId)
    }
    

    fun getConcertDetailByScheduleId(scheduleId: Long): ConcertDetailDto {
        return concertService.getConcertDetailByScheduleId(scheduleId)
    }
    

    fun getSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        return concertService.getSchedulesByConcertId(concertId)
    }
    

    fun getAvailableSchedulesByConcertId(concertId: Long): List<ConcertWithScheduleDto> {
        return concertService.getAvailableSchedulesByConcertId(concertId)
    }
    

    fun getAvailableSeats(scheduleId: Long): List<SeatDto> {
        return seatService.getAvailableSeats(scheduleId)
    }
    

    fun getAllSeats(scheduleId: Long): List<SeatDto> {
        return seatService.getAllSeats(scheduleId)
    }
}
