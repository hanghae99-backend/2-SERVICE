package kr.hhplus.be.server.concert.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.*
import kr.hhplus.be.server.concert.entity.Seat
import kr.hhplus.be.server.concert.entity.SeatInfo
import kr.hhplus.be.server.concert.entity.SeatStatus
import kr.hhplus.be.server.concert.entity.ConcertNotFoundException
import kr.hhplus.be.server.concert.entity.SeatNotFoundException
import kr.hhplus.be.server.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository
import java.math.BigDecimal
import java.util.*

class SeatServiceUnitTest : BehaviorSpec({
    
    lateinit var seatJpaRepository: SeatJpaRepository
    lateinit var concertJpaRepository: ConcertJpaRepository
    lateinit var seatService: SeatService
    
    beforeTest {
        seatJpaRepository = mockk()
        concertJpaRepository = mockk()
        seatService = SeatService(seatJpaRepository, concertJpaRepository)
        clearMocks(seatJpaRepository, concertJpaRepository, answers = false, recordedCalls = true)
    }
    
    given("SeatService는 좌석 비즈니스 로직을 처리한다") {
        `when`("존재하는 콘서트의 예약 가능한 좌석을 조회하면") {
            then("AVAILABLE 상태의 좌석 목록을 반환한다") {
                // given
                val concertId = 1L
                val availableSeats = listOf(
                    Seat(
                        seatId = 1L,
                        concertId = concertId,
                        seatNumber = 1,
                        price = BigDecimal("150000"),
                        status = SeatStatus.AVAILABLE
                    ),
                    Seat(
                        seatId = 2L,
                        concertId = concertId,
                        seatNumber = 2,
                        price = BigDecimal("150000"),
                        status = SeatStatus.AVAILABLE
                    ),
                    Seat(
                        seatId = 5L,
                        concertId = concertId,
                        seatNumber = 5,
                        price = BigDecimal("200000"),
                        status = SeatStatus.AVAILABLE
                    )
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(mockk())
                every { seatJpaRepository.findAvailableSeatsByConcertId(concertId) } returns availableSeats
                
                // when
                val result = seatService.getAvailableSeats(concertId)
                
                // then
                result shouldHaveSize 3
                result.all { it.status == SeatStatus.AVAILABLE } shouldBe true
                
                result[0].seatId shouldBe 1L
                result[0].seatNumber shouldBe 1
                result[0].price shouldBe BigDecimal("150000")
                
                result[1].seatId shouldBe 2L
                result[1].seatNumber shouldBe 2
                result[1].price shouldBe BigDecimal("150000")
                
                result[2].seatId shouldBe 5L
                result[2].seatNumber shouldBe 5
                result[2].price shouldBe BigDecimal("200000")
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
                verify(exactly = 1) { seatJpaRepository.findAvailableSeatsByConcertId(concertId) }
            }
        }
        
        `when`("존재하지 않는 콘서트의 좌석을 조회하면") {
            then("ConcertNotFoundException이 발생한다") {
                // given
                val nonExistentConcertId = 999L
                
                every { concertJpaRepository.findById(nonExistentConcertId) } returns Optional.empty()
                
                // when & then
                val exception = shouldThrow<ConcertNotFoundException> {
                    seatService.getAvailableSeats(nonExistentConcertId)
                }
                
                exception.message shouldBe "콘서트를 찾을 수 없습니다. ID: $nonExistentConcertId"
                
                verify(exactly = 1) { concertJpaRepository.findById(nonExistentConcertId) }
                verify(exactly = 0) { seatJpaRepository.findAvailableSeatsByConcertId(any()) }
            }
        }
        
        `when`("콘서트에 예약 가능한 좌석이 없으면") {
            then("빈 목록을 반환한다") {
                // given
                val concertId = 1L
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(mockk())
                every { seatJpaRepository.findAvailableSeatsByConcertId(concertId) } returns emptyList()
                
                // when
                val result = seatService.getAvailableSeats(concertId)
                
                // then
                result.shouldBeEmpty()
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
                verify(exactly = 1) { seatJpaRepository.findAvailableSeatsByConcertId(concertId) }
            }
        }
        
        `when`("콘서트의 모든 좌석 정보를 조회하면") {
            then("모든 상태의 좌석을 좌석 번호순으로 정렬하여 반환한다") {
                // given
                val concertId = 1L
                val allSeats = listOf(
                    Seat(
                        seatId = 3L,
                        concertId = concertId,
                        seatNumber = 3,
                        price = BigDecimal("150000"),
                        status = SeatStatus.CONFIRMED
                    ),
                    Seat(
                        seatId = 1L,
                        concertId = concertId,
                        seatNumber = 1,
                        price = BigDecimal("150000"),
                        status = SeatStatus.AVAILABLE
                    ),
                    Seat(
                        seatId = 4L,
                        concertId = concertId,
                        seatNumber = 4,
                        price = BigDecimal("200000"),
                        status = SeatStatus.UNAVAILABLE
                    ),
                    Seat(
                        seatId = 2L,
                        concertId = concertId,
                        seatNumber = 2,
                        price = BigDecimal("150000"),
                        status = SeatStatus.RESERVED
                    )
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(mockk())
                every { seatJpaRepository.findByConcertId(concertId) } returns allSeats
                
                // when
                val result = seatService.getAllSeats(concertId)
                
                // then
                result shouldHaveSize 4
                result shouldBeSortedBy { it.seatNumber }
                
                result[0].seatNumber shouldBe 1
                result[0].status shouldBe SeatStatus.AVAILABLE
                
                result[1].seatNumber shouldBe 2
                result[1].status shouldBe SeatStatus.RESERVED
                
                result[2].seatNumber shouldBe 3
                result[2].status shouldBe SeatStatus.CONFIRMED
                
                result[3].seatNumber shouldBe 4
                result[3].status shouldBe SeatStatus.UNAVAILABLE
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
                verify(exactly = 1) { seatJpaRepository.findByConcertId(concertId) }
            }
        }
        
        `when`("콘서트에 좌석이 없으면") {
            then("빈 목록을 반환한다") {
                // given
                val concertId = 1L
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(mockk())
                every { seatJpaRepository.findByConcertId(concertId) } returns emptyList()
                
                // when
                val result = seatService.getAllSeats(concertId)
                
                // then
                result.shouldBeEmpty()
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
                verify(exactly = 1) { seatJpaRepository.findByConcertId(concertId) }
            }
        }
        
        `when`("존재하는 좌석의 정보를 조회하면") {
            then("좌석 정보를 SeatInfo로 반환한다") {
                // given
                val seatId = 1L
                val seat = Seat(
                    seatId = seatId,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                every { seatJpaRepository.findById(seatId) } returns Optional.of(seat)
                
                // when
                val result = seatService.getSeatById(seatId)
                
                // then
                result.seatId shouldBe seatId
                result.seatNumber shouldBe 15
                result.price shouldBe BigDecimal("180000")
                result.status shouldBe SeatStatus.AVAILABLE
                
                verify(exactly = 1) { seatJpaRepository.findById(seatId) }
            }
        }
        
        `when`("존재하지 않는 좌석의 정보를 조회하면") {
            then("SeatNotFoundException이 발생한다") {
                // given
                val nonExistentSeatId = 999L
                
                every { seatJpaRepository.findById(nonExistentSeatId) } returns Optional.empty()
                
                // when & then
                val exception = shouldThrow<SeatNotFoundException> {
                    seatService.getSeatById(nonExistentSeatId)
                }
                
                exception.message shouldBe "좌석을 찾을 수 없습니다. ID: $nonExistentSeatId"
                
                verify(exactly = 1) { seatJpaRepository.findById(nonExistentSeatId) }
            }
        }
        
        `when`("AVAILABLE 상태인 좌석의 예약 가능 여부를 확인하면") {
            then("true를 반환한다") {
                // given
                val seatId = 1L
                val seat = Seat(
                    seatId = seatId,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                every { seatJpaRepository.findById(seatId) } returns Optional.of(seat)
                
                // when
                val result = seatService.isSeatAvailable(seatId)
                
                // then
                result shouldBe true
                
                verify(exactly = 1) { seatJpaRepository.findById(seatId) }
            }
        }
        
        `when`("AVAILABLE이 아닌 상태인 좌석의 예약 가능 여부를 확인하면") {
            then("false를 반환한다") {
                // given
                val reservedSeatId = 1L
                val confirmedSeatId = 2L
                val unavailableSeatId = 3L
                
                val reservedSeat = Seat(
                    seatId = reservedSeatId,
                    concertId = 100L,
                    seatNumber = 1,
                    price = BigDecimal("180000"),
                    status = SeatStatus.RESERVED
                )
                
                val confirmedSeat = Seat(
                    seatId = confirmedSeatId,
                    concertId = 100L,
                    seatNumber = 2,
                    price = BigDecimal("180000"),
                    status = SeatStatus.CONFIRMED
                )
                
                val unavailableSeat = Seat(
                    seatId = unavailableSeatId,
                    concertId = 100L,
                    seatNumber = 3,
                    price = BigDecimal("180000"),
                    status = SeatStatus.UNAVAILABLE
                )
                
                every { seatJpaRepository.findById(reservedSeatId) } returns Optional.of(reservedSeat)
                every { seatJpaRepository.findById(confirmedSeatId) } returns Optional.of(confirmedSeat)
                every { seatJpaRepository.findById(unavailableSeatId) } returns Optional.of(unavailableSeat)
                
                // when & then
                seatService.isSeatAvailable(reservedSeatId) shouldBe false
                seatService.isSeatAvailable(confirmedSeatId) shouldBe false
                seatService.isSeatAvailable(unavailableSeatId) shouldBe false
                
                verify(exactly = 1) { seatJpaRepository.findById(reservedSeatId) }
                verify(exactly = 1) { seatJpaRepository.findById(confirmedSeatId) }
                verify(exactly = 1) { seatJpaRepository.findById(unavailableSeatId) }
            }
        }
        
        `when`("존재하지 않는 좌석의 예약 가능 여부를 확인하면") {
            then("SeatNotFoundException이 발생한다") {
                // given
                val nonExistentSeatId = 999L
                
                every { seatJpaRepository.findById(nonExistentSeatId) } returns Optional.empty()
                
                // when & then
                val exception = shouldThrow<SeatNotFoundException> {
                    seatService.isSeatAvailable(nonExistentSeatId)
                }
                
                exception.message shouldBe "좌석을 찾을 수 없습니다. ID: $nonExistentSeatId"
                
                verify(exactly = 1) { seatJpaRepository.findById(nonExistentSeatId) }
            }
        }
        
        `when`("좌석 상태를 성공적으로 변경하면") {
            then("true를 반환한다") {
                // given
                val seatId = 1L
                val newStatus = SeatStatus.RESERVED
                
                every { seatJpaRepository.updateSeatStatus(seatId, newStatus) } returns 1
                
                // when
                val result = seatService.updateSeatStatus(seatId, newStatus)
                
                // then
                result shouldBe true
                
                verify(exactly = 1) { seatJpaRepository.updateSeatStatus(seatId, newStatus) }
            }
        }
        
        `when`("좌석 상태 변경에 실패하면") {
            then("false를 반환한다") {
                // given
                val seatId = 999L
                val newStatus = SeatStatus.RESERVED
                
                every { seatJpaRepository.updateSeatStatus(seatId, newStatus) } returns 0
                
                // when
                val result = seatService.updateSeatStatus(seatId, newStatus)
                
                // then
                result shouldBe false
                
                verify(exactly = 1) { seatJpaRepository.updateSeatStatus(seatId, newStatus) }
            }
        }
        
        `when`("다양한 상태로 좌석을 변경하면") {
            then("각각 올바르게 처리된다") {
                // given
                val seatId = 1L
                
                every { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.RESERVED) } returns 1
                every { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.CONFIRMED) } returns 1
                every { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.AVAILABLE) } returns 1
                every { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.UNAVAILABLE) } returns 1
                
                // when & then
                seatService.updateSeatStatus(seatId, SeatStatus.RESERVED) shouldBe true
                seatService.updateSeatStatus(seatId, SeatStatus.CONFIRMED) shouldBe true
                seatService.updateSeatStatus(seatId, SeatStatus.AVAILABLE) shouldBe true
                seatService.updateSeatStatus(seatId, SeatStatus.UNAVAILABLE) shouldBe true
                
                verify(exactly = 1) { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.RESERVED) }
                verify(exactly = 1) { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.CONFIRMED) }
                verify(exactly = 1) { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.AVAILABLE) }
                verify(exactly = 1) { seatJpaRepository.updateSeatStatus(seatId, SeatStatus.UNAVAILABLE) }
            }
        }
    }
    
    given("SeatService는 데이터 변환을 정확히 처리한다") {
        `when`("Seat 엔티티를 SeatInfo DTO로 변환하면") {
            then("모든 필드가 올바르게 매핑된다") {
                // given
                val concertId = 1L
                val seats = listOf(
                    Seat(
                        seatId = 1L,
                        concertId = concertId,
                        seatNumber = 10,
                        price = BigDecimal("120000"),
                        status = SeatStatus.AVAILABLE
                    ),
                    Seat(
                        seatId = 2L,
                        concertId = concertId,
                        seatNumber = 20,
                        price = BigDecimal("180000"),
                        status = SeatStatus.RESERVED
                    )
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(mockk())
                every { seatJpaRepository.findAvailableSeatsByConcertId(concertId) } returns listOf(seats[0])
                
                // when
                val result = seatService.getAvailableSeats(concertId)
                
                // then
                result shouldHaveSize 1
                val seatInfo = result[0]
                
                seatInfo.seatId shouldBe seats[0].seatId
                seatInfo.seatNumber shouldBe seats[0].seatNumber
                seatInfo.price shouldBe seats[0].price
                seatInfo.status shouldBe seats[0].status
            }
        }
        
        `when`("여러 Seat 엔티티를 SeatInfo 리스트로 변환하면") {
            then("모든 엔티티가 올바르게 변환된다") {
                // given
                val concertId = 1L
                val seats = listOf(
                    Seat(
                        seatId = 1L,
                        concertId = concertId,
                        seatNumber = 1,
                        price = BigDecimal("100000"),
                        status = SeatStatus.AVAILABLE
                    ),
                    Seat(
                        seatId = 2L,
                        concertId = concertId,
                        seatNumber = 2,
                        price = BigDecimal("150000"),
                        status = SeatStatus.RESERVED
                    ),
                    Seat(
                        seatId = 3L,
                        concertId = concertId,
                        seatNumber = 3,
                        price = BigDecimal("200000"),
                        status = SeatStatus.CONFIRMED
                    )
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(mockk())
                every { seatJpaRepository.findByConcertId(concertId) } returns seats
                
                // when
                val result = seatService.getAllSeats(concertId)
                
                // then
                result shouldHaveSize 3
                
                result.forEachIndexed { index, seatInfo ->
                    val originalSeat = seats[index]
                    seatInfo.seatId shouldBe originalSeat.seatId
                    seatInfo.seatNumber shouldBe originalSeat.seatNumber
                    seatInfo.price shouldBe originalSeat.price
                    seatInfo.status shouldBe originalSeat.status
                }
            }
        }
        
        `when`("다양한 가격대의 좌석을 조회하면") {
            then("가격 정보가 정확히 매핑된다") {
                // given
                val concertId = 1L
                val seats = listOf(
                    Seat(
                        seatId = 1L,
                        concertId = concertId,
                        seatNumber = 1,
                        price = BigDecimal("50000.00"),
                        status = SeatStatus.AVAILABLE
                    ),
                    Seat(
                        seatId = 2L,
                        concertId = concertId,
                        seatNumber = 2,
                        price = BigDecimal("1000000.50"),
                        status = SeatStatus.AVAILABLE
                    )
                )
                
                every { concertJpaRepository.findById(concertId) } returns Optional.of(mockk())
                every { seatJpaRepository.findAvailableSeatsByConcertId(concertId) } returns seats
                
                // when
                val result = seatService.getAvailableSeats(concertId)
                
                // then
                result shouldHaveSize 2
                result[0].price shouldBe BigDecimal("50000.00")
                result[1].price shouldBe BigDecimal("1000000.50")
            }
        }
    }
    
    given("SeatService는 예외 상황을 적절히 처리한다") {
        `when`("Repository에서 예외가 발생하면") {
            then("예외를 그대로 전파한다") {
                // given
                val concertId = 1L
                val repositoryException = RuntimeException("데이터베이스 연결 오류")
                
                every { concertJpaRepository.findById(concertId) } throws repositoryException
                
                // when & then
                val exception = shouldThrow<RuntimeException> {
                    seatService.getAvailableSeats(concertId)
                }
                
                exception shouldBe repositoryException
                
                verify(exactly = 1) { concertJpaRepository.findById(concertId) }
                verify(exactly = 0) { seatJpaRepository.findAvailableSeatsByConcertId(any()) }
            }
        }
        
        `when`("좌석 조회 시 Repository에서 예외가 발생하면") {
            then("예외를 그대로 전파한다") {
                // given
                val seatId = 1L
                val repositoryException = RuntimeException("좌석 조회 실패")
                
                every { seatJpaRepository.findById(seatId) } throws repositoryException
                
                // when & then
                val exception = shouldThrow<RuntimeException> {
                    seatService.getSeatById(seatId)
                }
                
                exception shouldBe repositoryException
                
                verify(exactly = 1) { seatJpaRepository.findById(seatId) }
            }
        }
        
        `when`("좌석 상태 업데이트 시 Repository에서 예외가 발생하면") {
            then("예외를 그대로 전파한다") {
                // given
                val seatId = 1L
                val newStatus = SeatStatus.RESERVED
                val repositoryException = RuntimeException("상태 업데이트 실패")
                
                every { seatJpaRepository.updateSeatStatus(seatId, newStatus) } throws repositoryException
                
                // when & then
                val exception = shouldThrow<RuntimeException> {
                    seatService.updateSeatStatus(seatId, newStatus)
                }
                
                exception shouldBe repositoryException
                
                verify(exactly = 1) { seatJpaRepository.updateSeatStatus(seatId, newStatus) }
            }
        }
    }
})
