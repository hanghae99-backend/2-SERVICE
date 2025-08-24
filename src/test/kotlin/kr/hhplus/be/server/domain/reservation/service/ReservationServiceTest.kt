package kr.hhplus.be.server.domain.reservation.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.domain.concert.exception.SeatAlreadyReservedException
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.config.TestDataConstants
import kr.hhplus.be.server.global.event.DomainEventPublisher
import io.mockk.just
import io.mockk.Runs
import java.math.BigDecimal

class ReservationServiceTest : DescribeSpec({
    
    val reservationRepository = mockk<ReservationRepository>()
    val statusRepository = mockk<ReservationStatusTypePojoRepository>()
    val eventPublisher = mockk<DomainEventPublisher>()
    val seatService = mockk<SeatService>()
    val selloutRankingService = mockk<SelloutRankingService>()
    
    val reservationService = ReservationService(
        reservationRepository,
        statusRepository,
        eventPublisher,
        seatService,
        selloutRankingService
    )
    
    beforeEach {
        clearAllMocks()
        every { eventPublisher.publish(any()) } returns Unit
        every { selloutRankingService.incrementReservationCount(any()) } just Runs
        every { selloutRankingService.decrementReservationCount(any()) } just Runs
    }
    
    describe("reserveSeat") {
        context("예약 가능한 좌석을 예약할 때") {
            it("예약이 성공적으로 생성되어야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val price = BigDecimal("50000")

                val seatDto = mockk<SeatDto> {
                    every { this@mockk.seatId } returns seatId
                    every { this@mockk.price } returns price
                    every { this@mockk.seatNumber } returns "A1"
                }
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val confirmStatus = TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CONFIRMED.code,
                    name = TestDataConstants.ReservationStatusType.CONFIRMED.name,
                    description = TestDataConstants.ReservationStatusType.CONFIRMED.description
                )

                val reservation = Reservation.createTemporary(userId, concertId, seatId, "A1", price, temporaryStatus)

                every { seatService.getSeatById(seatId) } returns seatDto
                every { reservationRepository.findBySeatIdAndStatusCodeIn(seatId, any()) } returns null
                every { statusRepository.getTemporaryStatus() } returns temporaryStatus
                every { reservationRepository.save(any()) } returns reservation
                every { seatService.reserveSeat(seatId) } returns seatDto
                every { statusRepository.getConfirmedStatus() } returns confirmStatus

                // when
                val result = reservationService.reserveSeat(userId, concertId, seatId)

                // then
                result shouldNotBe null
                result.userId shouldBe userId
                result.concertId shouldBe concertId
                result.seatId shouldBe seatId
                verify { reservationRepository.save(any()) }
                verify { eventPublisher.publish(any()) }
            }
        }
        
        context("이미 예약된 좌석을 예약할 때") {
            it("예외가 발생해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val price = BigDecimal("50000")
                
                val seatDto = mockk<SeatDto> {
                    every { this@mockk.seatId } returns seatId
                    every { this@mockk.price } returns price
                    every { this@mockk.seatNumber } returns "A1"
                }
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val existingReservation = Reservation.createTemporary(userId, concertId, seatId, "A1", price, temporaryStatus)
                
                every { seatService.getSeatById(seatId) } returns seatDto
                every { statusRepository.getTemporaryStatus() } returns temporaryStatus
                every { statusRepository.getConfirmedStatus() } returns TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CONFIRMED.code,
                    name = TestDataConstants.ReservationStatusType.CONFIRMED.name,
                    description = TestDataConstants.ReservationStatusType.CONFIRMED.description
                )
                every { reservationRepository.findBySeatIdAndStatusCodeIn(seatId, any()) } returns existingReservation
                
                // when & then
                shouldThrow<SeatAlreadyReservedException> {
                    reservationService.reserveSeat(userId, concertId, seatId)
                }
            }
        }
    }
    
    describe("confirmReservation") {
        context("유효한 임시 예약을 확정할 때") {
            it("예약이 성공적으로 확정되어야 한다") {
                // given
                val reservationId = 1L
                val paymentId = 1L
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val confirmedStatus = TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CONFIRMED.code,
                    name = TestDataConstants.ReservationStatusType.CONFIRMED.name,
                    description = TestDataConstants.ReservationStatusType.CONFIRMED.description
                )
                val reservation = Reservation.createTemporary(1L, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                reservation.reservationId = reservationId
                
                every { reservationRepository.findById(reservationId) } returns reservation
                every { statusRepository.getConfirmedStatus() } returns confirmedStatus
                every { reservationRepository.save(any()) } returns reservation
                
                // when
                val result = reservationService.confirmReservation(reservationId, paymentId)
                
                // then
                result shouldNotBe null
                result.paymentId shouldBe paymentId
                verify { reservationRepository.save(any()) }
                verify { eventPublisher.publish(any()) }
            }
        }
    }
    
    describe("cancelReservation") {
        context("유효한 예약을 취소할 때") {
            it("예약이 성공적으로 취소되어야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val cancelReason = "개인 사정"
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val cancelledStatus = TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CANCELLED.code,
                    name = TestDataConstants.ReservationStatusType.CANCELLED.name,
                    description = TestDataConstants.ReservationStatusType.CANCELLED.description
                )
                val reservation = Reservation.createTemporary(userId, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                reservation.reservationId = reservationId
                
                every { reservationRepository.findById(reservationId) } returns reservation
                every { statusRepository.getCancelledStatus() } returns cancelledStatus
                every { reservationRepository.save(any()) } returns reservation
                
                // when
                val result = reservationService.cancelReservation(reservationId, userId, cancelReason)
                
                // then
                result shouldNotBe null
                verify { reservationRepository.save(any()) }
                verify { eventPublisher.publish(any()) }
            }
        }
    }
    
    describe("getReservationById") {
        context("존재하는 예약 ID로 조회할 때") {
            it("예약 정보를 반환해야 한다") {
                // given
                val reservationId = 1L
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val reservation = Reservation.createTemporary(1L, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                reservation.reservationId = reservationId
                
                every { reservationRepository.findById(reservationId) } returns reservation
                
                // when
                val result = reservationService.getReservationById(reservationId)
                
                // then
                result shouldNotBe null
                result.reservationId shouldBe reservationId
            }
        }
        
        context("존재하지 않는 예약 ID로 조회할 때") {
            it("예외가 발생해야 한다") {
                // given
                val invalidReservationId = 999L
                
                every { reservationRepository.findById(invalidReservationId) } returns null
                
                // when & then
                shouldThrow<ReservationNotFoundException> {
                    reservationService.getReservationById(invalidReservationId)
                }
            }
        }
    }
})