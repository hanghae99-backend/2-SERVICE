package kr.hhplus.be.server.api.concert.dto

import kr.hhplus.be.server.domain.concert.models.Concert
import kr.hhplus.be.server.domain.concert.models.ConcertSchedule
import kr.hhplus.be.server.domain.concert.models.Seat
import java.time.LocalDate




data class ConcertDto(
    val concertId: Long,
    val title: String,
    val artist: String
) {
    companion object {
        fun from(concert: Concert): ConcertDto {
            return ConcertDto(
                concertId = concert.concertId,
                title = concert.title,
                artist = concert.artist
            )
        }
    }
}

/**
 * 콘서트 스케줄 기본 정보 DTO
 */
data class ConcertScheduleDto(
    val scheduleId: Long,
    val concertId: Long,
    val venue: String,
    val concertDate: LocalDate,
    val totalSeats: Int,
    val availableSeats: Int
) {
    companion object {
        fun from(schedule: ConcertSchedule): ConcertScheduleDto {
            return ConcertScheduleDto(
                scheduleId = schedule.scheduleId,
                concertId = schedule.concertId,
                venue = schedule.venue,
                concertDate = schedule.concertDate,
                totalSeats = schedule.totalSeats,
                availableSeats = schedule.availableSeats
            )
        }
    }
}

/**
 * 콘서트 전체 정보 조합 DTO
 */
data class ConcertWithScheduleDto(
    val concert: Concert,
    val schedule: ConcertSchedule
) {
    companion object {
        fun from(concert: Concert, schedule: ConcertSchedule): ConcertWithScheduleDto {
            return ConcertWithScheduleDto(
                concert = concert,
                schedule = schedule
            )
        }
    }
}

/**
 * 콘서트 스케줄과 콘서트 정보가 합쳐진 DTO (API 응답용)
 */
data class ConcertScheduleWithInfoDto(
    val scheduleId: Long,
    val concertId: Long,
    val title: String,
    val artist: String,
    val venue: String,
    val concertDate: LocalDate,
    val totalSeats: Int,
    val availableSeats: Int
) {
    companion object {
        fun from(concert: Concert, schedule: ConcertSchedule): ConcertScheduleWithInfoDto {
            return ConcertScheduleWithInfoDto(
                scheduleId = schedule.scheduleId,
                concertId = schedule.concertId,
                title = concert.title,
                artist = concert.artist,
                venue = schedule.venue,
                concertDate = schedule.concertDate,
                totalSeats = schedule.totalSeats,
                availableSeats = schedule.availableSeats
            )
        }
    }
}

/**
 * 콘서트 상세 정보 (좌석 포함) DTO
 */
data class ConcertDetailDto(
    val concert: ConcertDto,
    val schedule: ConcertScheduleDto,
    val seats: List<SeatDto>
) {
    companion object {
        fun from(
            concert: Concert, 
            schedule: ConcertSchedule, 
            seats: List<Seat>
        ): ConcertDetailDto {
            return ConcertDetailDto(
                concert = ConcertDto.from(concert),
                schedule = ConcertScheduleDto.from(schedule),
                seats = seats.map { SeatDto.from(it) }
            )
        }
    }
}

/**
 * 콘서트 상세 정보 (좌석 상태 포함) DTO
 */
data class ConcertDetailWithStatusDto(
    val concert: ConcertDto,
    val schedule: ConcertScheduleDto,
    val seats: List<SeatWithStatusDto>
) {
    companion object {
        fun from(
            concert: Concert,
            schedule: ConcertSchedule,
            seatsWithStatus: List<SeatWithStatusDto>
        ): ConcertDetailWithStatusDto {
            return ConcertDetailWithStatusDto(
                concert = ConcertDto.from(concert),
                schedule = ConcertScheduleDto.from(schedule),
                seats = seatsWithStatus
            )
        }
    }
}

/**
 * 콘서트 기본 정보 응답용 DTO
 */
data class ConcertDetail(
    val concertId: Long,
    val title: String,
    val artist: String
) {
    companion object {
        fun from(data: ConcertDto): ConcertDetail {
            return ConcertDetail(
                concertId = data.concertId,
                title = data.title,
                artist = data.artist
            )
        }
    }
}

/**
 * 콘서트 스케줄 응답용 DTO
 */
data class ConcertScheduleDetail(
    val scheduleId: Long,
    val concertId: Long,
    val title: String,
    val artist: String,
    val venue: String,
    val concertDate: String,
    val totalSeats: Int,
    val availableSeats: Int
) {
    companion object {
        fun from(data: ConcertScheduleWithInfoDto): ConcertScheduleDetail {
            return ConcertScheduleDetail(
                scheduleId = data.scheduleId,
                concertId = data.concertId,
                title = data.title,
                artist = data.artist,
                venue = data.venue,
                concertDate = data.concertDate.toString(),
                totalSeats = data.totalSeats,
                availableSeats = data.availableSeats
            )
        }
        
        fun from(data: ConcertWithScheduleDto): ConcertScheduleDetail {
            return ConcertScheduleDetail(
                scheduleId = data.schedule.scheduleId,
                concertId = data.schedule.concertId,
                title = data.concert.title,
                artist = data.concert.artist,
                venue = data.schedule.venue,
                concertDate = data.schedule.concertDate.toString(),
                totalSeats = data.schedule.totalSeats,
                availableSeats = data.schedule.availableSeats
            )
        }
    }
}

/**
 * 콘서트 전체 상세 정보 응답용 DTO
 */
data class ConcertFullDetail(
    val concert: ConcertDetail,
    val schedule: ConcertScheduleDto,
    val seats: List<SeatDetail>
) {
    companion object {
        fun from(data: ConcertDetailDto): ConcertFullDetail {
            return ConcertFullDetail(
                concert = ConcertDetail.from(data.concert),
                schedule = data.schedule,
                seats = data.seats.map { SeatDetail.from(it) }
            )
        }
    }
}
