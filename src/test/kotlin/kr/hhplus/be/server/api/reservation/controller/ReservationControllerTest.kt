package kr.hhplus.be.server.api.reservation.controller

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCancelRequest
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.auth.service.TokenValidator
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.config.TestDataConstants
import java.math.BigDecimal
import java.time.LocalDateTime

class ReservationControllerTest : DescribeSpec({
    
    lateinit var reservationService: ReservationService
    lateinit var tokenValidator: TokenValidator
    lateinit var controller: ReservationController
    
    beforeEach {
        reservationService = mockk<ReservationService>()
        tokenValidator = mockk<TokenValidator>()
        controller = ReservationController(reservationService, tokenValidator)
    }
    
    describe("예약 생성 API - createReservation") {
        context("유효한 예약 생성 요청이 들어올 때") {
            it("예약을 생성하고 201 Created를 반환한다") {
                // given
                val request = ReservationCreateRequest(
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    token = "test-token"
                )
                
                val mockStatus = mockk<ReservationStatusType>()
                every { mockStatus.code } returns TestDataConstants.ReservationStatusType.TEMPORARY.code
                every { mockStatus.name } returns "임시예약"
                every { mockStatus.description } returns "임시 예약 상태"
                
                val mockReservation = mockk<Reservation>(relaxed = true)
                every { mockReservation.reservationId } returns 1L
                every { mockReservation.userId } returns request.userId
                every { mockReservation.concertId } returns request.concertId
                every { mockReservation.seatId } returns request.seatId
                every { mockReservation.seatNumber } returns "A1"
                every { mockReservation.price } returns BigDecimal("50000")
                every { mockReservation.status } returns mockStatus
                every { mockReservation.reservedAt } returns LocalDateTime.now()
                every { mockReservation.expiresAt } returns LocalDateTime.now().plusMinutes(5)
                every { mockReservation.confirmedAt } returns null
                every { mockReservation.paymentId } returns null
                
                val mockToken = mockk<WaitingToken>()
                every { tokenValidator.validateActiveToken(request.token) } returns mockToken
                every { 
                    reservationService.createReservation(
                        request.userId, 
                        request.concertId, 
                        request.seatId
                    ) 
                } returns mockReservation
                
                // when
                val response = controller.createReservation(request)
                
                // then
                response shouldNotBe null
                response.statusCode.value() shouldBe 201
                response.body?.success shouldBe true
                response.body?.message shouldBe "좌석 예약 완료"
                response.body?.data?.reservationId shouldBe 1L
                
                verify(exactly = 1) {
                    tokenValidator.validateActiveToken(request.token)
                    reservationService.createReservation(
                        request.userId,
                        request.concertId,
                        request.seatId
                    )
                }
            }
        }

        context("잘못된 토큰으로 예약 생성을 시도할 때") {
            it("예외를 발생시킨다") {
                // given
                val request = ReservationCreateRequest(
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    token = "invalid-token"
                )
                
                every { 
                    tokenValidator.validateActiveToken(request.token) 
                } throws IllegalArgumentException("유효하지 않은 토큰")
                
                // when & then
                shouldThrow<IllegalArgumentException> {
                    controller.createReservation(request)
                }.message shouldBe "유효하지 않은 토큰"
            }
        }

        context("이미 예약된 좌석을 예약하려고 할 때") {
            it("예외를 발생시킨다") {
                // given
                val request = ReservationCreateRequest(
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    token = "test-token"
                )
                
                val mockToken = mockk<WaitingToken>()
                every { tokenValidator.validateActiveToken(request.token) } returns mockToken
                every { 
                    reservationService.createReservation(
                        request.userId, 
                        request.concertId, 
                        request.seatId
                    ) 
                } throws IllegalStateException("이미 예약된 좌석입니다")
                
                // when & then
                shouldThrow<IllegalStateException> {
                    controller.createReservation(request)
                }.message shouldBe "이미 예약된 좌석입니다"
            }
        }
    }

    describe("예약 취소 API - cancelReservation") {
        context("존재하는 예약을 취소할 때") {
            it("예약을 취소하고 200 OK를 반환한다") {
                // given
                val reservationId = 1L
                val request = ReservationCancelRequest(
                    userId = 1L,
                    cancelReason = "개인 사정으로 인한 취소",
                    token = "test-token"
                )
                
                val mockStatus = mockk<ReservationStatusType>()
                every { mockStatus.code } returns TestDataConstants.ReservationStatusType.CANCELLED.code
                every { mockStatus.name } returns "취소"
                every { mockStatus.description } returns "취소된 예약"
                
                val mockReservation = mockk<Reservation>(relaxed = true)
                every { mockReservation.reservationId } returns reservationId
                every { mockReservation.userId } returns request.userId
                every { mockReservation.concertId } returns 1L
                every { mockReservation.seatId } returns 1L
                every { mockReservation.seatNumber } returns "A1"
                every { mockReservation.price } returns BigDecimal("50000")
                every { mockReservation.status } returns mockStatus
                every { mockReservation.reservedAt } returns LocalDateTime.now().minusHours(1)
                every { mockReservation.expiresAt } returns null
                every { mockReservation.confirmedAt } returns null
                every { mockReservation.paymentId } returns null
                
                val mockToken = mockk<WaitingToken>()
                every { tokenValidator.validateActiveToken(request.token) } returns mockToken
                every { 
                    reservationService.cancelReservationByUser(
                        reservationId,
                        request.userId,
                        request.cancelReason
                    ) 
                } returns mockReservation
                
                // when
                val response = controller.cancelReservation(reservationId, request)
                
                // then
                response shouldNotBe null
                response.statusCode.value() shouldBe 200
                response.body?.success shouldBe true
                response.body?.message shouldBe "예약 취소 완료"
                response.body?.data?.statusCode shouldBe TestDataConstants.ReservationStatusType.CANCELLED.code
                
                verify(exactly = 1) {
                    tokenValidator.validateActiveToken(request.token)
                    reservationService.cancelReservationByUser(
                        reservationId,
                        request.userId,
                        request.cancelReason
                    )
                }
            }
        }

        context("다른 사용자의 예약을 취소하려고 할 때") {
            it("예외를 발생시킨다") {
                // given
                val reservationId = 1L
                val request = ReservationCancelRequest(
                    userId = 2L,  // 다른 사용자
                    cancelReason = null,
                    token = "test-token"
                )
                
                val mockToken = mockk<WaitingToken>()
                every { tokenValidator.validateActiveToken(request.token) } returns mockToken
                every { 
                    reservationService.cancelReservationByUser(
                        reservationId,
                        request.userId,
                        request.cancelReason
                    ) 
                } throws IllegalArgumentException("해당 예약을 취소할 권한이 없습니다")
                
                // when & then
                shouldThrow<IllegalArgumentException> {
                    controller.cancelReservation(reservationId, request)
                }.message shouldBe "해당 예약을 취소할 권한이 없습니다"
            }
        }
    }

    describe("예약 조회 API - getReservation") {
        context("존재하는 예약 ID로 조회할 때") {
            it("예약 정보를 반환한다") {
                // given
                val reservationId = 1L
                
                val mockStatus = mockk<ReservationStatusType>()
                every { mockStatus.code } returns TestDataConstants.ReservationStatusType.CONFIRMED.code
                every { mockStatus.name } returns "확정"
                every { mockStatus.description } returns "확정된 예약"
                
                val mockReservation = mockk<Reservation>(relaxed = true)
                every { mockReservation.reservationId } returns reservationId
                every { mockReservation.userId } returns 1L
                every { mockReservation.concertId } returns 1L
                every { mockReservation.seatId } returns 1L
                every { mockReservation.seatNumber } returns "A1"
                every { mockReservation.price } returns BigDecimal("50000")
                every { mockReservation.status } returns mockStatus
                every { mockReservation.reservedAt } returns LocalDateTime.now().minusHours(2)
                every { mockReservation.expiresAt } returns null
                every { mockReservation.confirmedAt } returns LocalDateTime.now().minusHours(1)
                every { mockReservation.paymentId } returns 1L
                
                every { reservationService.getReservationById(reservationId) } returns mockReservation
                
                // when
                val response = controller.getReservation(reservationId)
                
                // then
                response shouldNotBe null
                response.statusCode.value() shouldBe 200
                response.body?.success shouldBe true
                response.body?.message shouldBe "예약 정보 조회 완료"
                response.body?.data?.reservationId shouldBe reservationId
                response.body?.data?.statusCode shouldBe TestDataConstants.ReservationStatusType.CONFIRMED.code
            }
        }

        context("존재하지 않는 예약 ID로 조회할 때") {
            it("NoSuchElementException을 발생시킨다") {
                // given
                val invalidReservationId = 999L
                
                every { 
                    reservationService.getReservationById(invalidReservationId) 
                } throws NoSuchElementException("예약을 찾을 수 없습니다")
                
                // when & then
                shouldThrow<NoSuchElementException> {
                    controller.getReservation(invalidReservationId)
                }.message shouldBe "예약을 찾을 수 없습니다"
            }
        }
    }
})
