package kr.hhplus.be.server.domain.reservation.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.just
import io.mockk.Runs
import kr.hhplus.be.server.domain.concert.exception.SeatAlreadyReservedException
import kr.hhplus.be.server.domain.reservation.exception.ReservationNotFoundException
import kr.hhplus.be.server.domain.reservation.exception.ReservationAccessDeniedException
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.repositories.ReservationRepository
import kr.hhplus.be.server.domain.reservation.repositories.ReservationStatusTypePojoRepository
import kr.hhplus.be.server.domain.reservation.kafka.ReservationEventProducer
import kr.hhplus.be.server.domain.reservation.event.ReservationCreatedEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationCancelledEvent
import kr.hhplus.be.server.domain.auth.service.ActiveTokenService
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.config.TestDataConstants
import kr.hhplus.be.server.global.client.SeatApiClient
import kr.hhplus.be.server.api.concert.dto.SeatDto
import java.math.BigDecimal
import java.time.LocalDateTime

class ReservationServiceTest : DescribeSpec({
    
    val reservationRepository = mockk<ReservationRepository>()
    val statusRepository = mockk<ReservationStatusTypePojoRepository>()
    val reservationEventProducer = mockk<ReservationEventProducer>()
    val seatApiClient = mockk<SeatApiClient>()
    val activeTokenService = mockk<ActiveTokenService>()
    
    val reservationService = ReservationService(
        reservationRepository,
        statusRepository,
        reservationEventProducer,
        seatApiClient,
        activeTokenService
    )
    
    beforeEach {
        clearAllMocks()
    }
    
    describe("createReservation") {
        context("예약 가능한 좌석을 예약할 때") {
            it("예약이 성공적으로 생성되어야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val price = BigDecimal("50000")

                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val reservation = Reservation.createTemporary(userId, concertId, seatId, "A1", price, temporaryStatus)

                every { reservationRepository.findBySeatIdAndStatusCodeIn(seatId, any()) } returns null
                every { statusRepository.getTemporaryStatus() } returns temporaryStatus
                every { statusRepository.getConfirmedStatus() } returns TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CONFIRMED.code,
                    name = TestDataConstants.ReservationStatusType.CONFIRMED.name,
                    description = TestDataConstants.ReservationStatusType.CONFIRMED.description
                )
                val seatInfo = SeatDto(seatId = seatId, scheduleId = 1L, seatNumber = "A1", price = price, statusCode = "AVAILABLE")
                
                every { reservationRepository.save(any()) } returns reservation
                every { seatApiClient.validateSeatAvailability(seatId) } returns mockk()
                every { seatApiClient.getSeatInfo(seatId) } returns seatInfo
                every { seatApiClient.reserveSeat(seatId) } returns mockk()
                every { reservationEventProducer.sendReservationCreatedEvent(any()) } returns Unit

                // when
                val result = reservationService.createReservation(userId, concertId, seatId)

                // then
                result shouldNotBe null
                result.userId shouldBe userId
                result.concertId shouldBe concertId
                result.seatId shouldBe seatId
                verify { reservationRepository.save(any()) }
                verify { reservationEventProducer.sendReservationCreatedEvent(any()) }
            }
        }
        
        context("이미 예약된 좌석을 예약할 때") {
            it("예외가 발생해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val price = BigDecimal("50000")
                
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val existingReservation = Reservation.createTemporary(userId, concertId, seatId, "A1", price, temporaryStatus)
                val seatInfo = SeatDto(seatId = seatId, scheduleId = 1L, seatNumber = "A1", price = price, statusCode = "AVAILABLE")
                
                every { statusRepository.getTemporaryStatus() } returns temporaryStatus
                every { statusRepository.getConfirmedStatus() } returns TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CONFIRMED.code,
                    name = TestDataConstants.ReservationStatusType.CONFIRMED.name,
                    description = TestDataConstants.ReservationStatusType.CONFIRMED.description
                )
                every { reservationRepository.findBySeatIdAndStatusCodeIn(seatId, any()) } returns existingReservation
                every { seatApiClient.validateSeatAvailability(seatId) } throws SeatAlreadyReservedException(seatId)
                every { seatApiClient.getSeatInfo(seatId) } returns seatInfo
                
                // when & then
                shouldThrow<SeatAlreadyReservedException> {
                    reservationService.createReservation(userId, concertId, seatId)
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
            }
        }
    }
    
    describe("cancelReservationByUser") {
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
                every { reservationEventProducer.sendReservationCancelledEvent(any()) } returns Unit
                
                // when
                val result = reservationService.cancelReservationByUser(reservationId, userId, cancelReason)
                
                // then
                result shouldNotBe null
                verify { reservationRepository.save(any()) }
                verify { reservationEventProducer.sendReservationCancelledEvent(any()) }
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
    
    describe("createReservation with token") {
        context("유효한 토큰으로 예약할 때") {
            it("토큰 검증 후 예약이 생성되어야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token"
                val price = BigDecimal("50000")
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val reservation = Reservation.createTemporary(userId, concertId, seatId, "A1", price, temporaryStatus)
                val seatInfo = SeatDto(seatId = seatId, scheduleId = 1L, seatNumber = "A1", price = price, statusCode = "AVAILABLE")
                
                every { activeTokenService.isTokenActive(token) } returns true
                every { statusRepository.getTemporaryStatus() } returns temporaryStatus
                every { reservationRepository.save(any()) } returns reservation
                every { seatApiClient.validateSeatAvailability(seatId) } returns mockk()
                every { seatApiClient.getSeatInfo(seatId) } returns seatInfo
                every { seatApiClient.reserveSeat(seatId) } returns mockk()
                every { reservationEventProducer.sendReservationCreatedEvent(any()) } returns Unit
                
                // when
                val result = reservationService.createReservation(userId, concertId, seatId, token)
                
                // then
                result shouldNotBe null
                result.userId shouldBe userId
                verify { activeTokenService.isTokenActive(token) }
                verify { reservationRepository.save(any()) }
            }
        }
        
        context("유효하지 않은 토큰으로 예약할 때") {
            it("IllegalArgumentException이 발생해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val invalidToken = "invalid-token"
                
                every { activeTokenService.isTokenActive(invalidToken) } returns false
                
                // when & then
                shouldThrow<IllegalArgumentException> {
                    reservationService.createReservation(userId, concertId, seatId, invalidToken)
                }
            }
        }
    }
    
    describe("cancelReservationByUser with token") {
        context("유효한 토큰으로 예약을 취소할 때") {
            it("토큰 검증 후 예약이 취소되어야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val token = "valid-token"
                val cancelReason = "개인 사정"
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val cancelledStatus = TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CANCELLED.code,
                    name = TestDataConstants.ReservationStatusType.CANCELLED.name,
                    description = TestDataConstants.ReservationStatusType.CANCELLED.description
                )
                val reservation = Reservation.createTemporary(userId, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                reservation.reservationId = reservationId
                
                every { activeTokenService.isTokenActive(token) } returns true
                every { reservationRepository.findById(reservationId) } returns reservation
                every { statusRepository.getCancelledStatus() } returns cancelledStatus
                every { reservationRepository.save(any()) } returns reservation
                every { reservationEventProducer.sendReservationCancelledEvent(any()) } returns Unit
                
                // when
                val result = reservationService.cancelReservationByUser(reservationId, userId, cancelReason, token)
                
                // then
                result shouldNotBe null
                verify { activeTokenService.isTokenActive(token) }
                verify { reservationRepository.save(any()) }
            }
        }
    }
    
    describe("cancelReservationBySystem") {
        context("시스템에서 예약을 취소할 때") {
            it("예약이 성공적으로 취소되어야 한다") {
                // given
                val reservationId = 1L
                val cancelReason = "만료"
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val cancelledStatus = TestDataFixture.createReservationStatusType(
                    code = TestDataConstants.ReservationStatusType.CANCELLED.code,
                    name = TestDataConstants.ReservationStatusType.CANCELLED.name,
                    description = TestDataConstants.ReservationStatusType.CANCELLED.description
                )
                val reservation = Reservation.createTemporary(1L, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                reservation.reservationId = reservationId
                
                every { reservationRepository.findById(reservationId) } returns reservation
                every { statusRepository.getCancelledStatus() } returns cancelledStatus
                every { reservationRepository.save(any()) } returns reservation
                every { reservationEventProducer.sendReservationCancelledEvent(any()) } returns Unit
                
                // when
                val result = reservationService.cancelReservationBySystem(reservationId, cancelReason)
                
                // then
                result shouldNotBe null
                verify { reservationRepository.save(any()) }
                verify { reservationEventProducer.sendReservationCancelledEvent(any()) }
            }
        }
        
        context("존재하지 않는 예약을 시스템에서 취소할 때") {
            it("ReservationNotFoundException이 발생해야 한다") {
                // given
                val reservationId = 999L
                val cancelReason = "만료"
                
                every { reservationRepository.findById(reservationId) } returns null
                
                // when & then
                shouldThrow<ReservationNotFoundException> {
                    reservationService.cancelReservationBySystem(reservationId, cancelReason)
                }
            }
        }
    }
    
    describe("getExpiredReservations") {
        context("만료된 예약을 조회할 때") {
            it("만료된 예약 목록을 반환해야 한다") {
                // given
                val limit = 10
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val expiredReservation1 = Reservation.createTemporary(1L, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                val expiredReservation2 = Reservation.createTemporary(2L, 1L, 2L, "A2", BigDecimal("50000"), temporaryStatus)
                val expiredReservations = listOf(expiredReservation1, expiredReservation2)
                
                every { statusRepository.getTemporaryStatus() } returns temporaryStatus
                every { 
                    reservationRepository.findByExpiresAtBeforeAndStatusCode(any<LocalDateTime>(), temporaryStatus.code) 
                } returns expiredReservations
                
                // when
                val result = reservationService.getExpiredReservations(limit)
                
                // then
                result shouldNotBe null
                result.size shouldBe 2
                verify { reservationRepository.findByExpiresAtBeforeAndStatusCode(any<LocalDateTime>(), temporaryStatus.code) }
            }
        }
        
        context("만료된 예약이 없을 때") {
            it("빈 목록을 반환해야 한다") {
                // given
                val limit = 10
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                
                every { statusRepository.getTemporaryStatus() } returns temporaryStatus
                every { 
                    reservationRepository.findByExpiresAtBeforeAndStatusCode(any<LocalDateTime>(), temporaryStatus.code) 
                } returns emptyList()
                
                // when
                val result = reservationService.getExpiredReservations(limit)
                
                // then
                result shouldNotBe null
                result.size shouldBe 0
            }
        }
    }
    
    describe("Access Control") {
        context("다른 사용자의 예약을 취소하려 할 때") {
            it("ReservationAccessDeniedException이 발생해야 한다") {
                // given
                val reservationId = 1L
                val ownerId = 1L
                val wrongUserId = 2L
                val cancelReason = "개인 사정"
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val reservation = Reservation.createTemporary(ownerId, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                reservation.reservationId = reservationId
                
                every { reservationRepository.findById(reservationId) } returns reservation
                
                // when & then
                shouldThrow<ReservationAccessDeniedException> {
                    reservationService.cancelReservationByUser(reservationId, wrongUserId, cancelReason)
                }
            }
        }
    }
})