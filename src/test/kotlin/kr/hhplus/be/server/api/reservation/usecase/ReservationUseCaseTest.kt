package kr.hhplus.be.server.api.reservation.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import io.mockk.verify
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.api.auth.usecase.TokenUseCase
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import kr.hhplus.be.server.api.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import java.math.BigDecimal
import java.time.LocalDateTime

class ReservationUseCaseTest : DescribeSpec({
    
    val reservationService = mockk<ReservationService>()
    val seatService = mockk<SeatService>()
    val userService = mockk<UserService>()
    val tokenUseCase = mockk<TokenUseCase>()

    val reservationUseCase = ReservationUseCase(
        reservationService,
        seatService,
        userService,
        tokenUseCase
    )
    
    describe("reserveSeat") {
        context("유효한 사용자가 좌석을 예약할 때") {
            it("예약을 성공적으로 생성해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val reservation = Reservation.createTemporary(userId, concertId, seatId, "A1", BigDecimal("100000"), temporaryStatus)
                
                val mockToken = WaitingToken.create(token, userId)
                
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { seatService.isSeatAvailable(seatId) } returns true
                every { reservationService.reserveSeat(userId, concertId, seatId) } returns reservation
                
                // when
                val result = reservationUseCase.reserveSeat(userId, concertId, seatId, token)
                
                // then
                result shouldNotBe null
                result.userId shouldBe userId
                result.concertId shouldBe concertId
                result.seatId shouldBe seatId
                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { seatService.isSeatAvailable(seatId) }
                verify { reservationService.reserveSeat(userId, concertId, seatId) }
            }
        }
        
        context("존재하지 않는 사용자가 좌석을 예약할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val userId = 999L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val mockToken = WaitingToken.create(token, userId)
                
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    reservationUseCase.reserveSeat(userId, concertId, seatId, token)
                }
                
                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
            }
        }
        
        context("예약 불가능한 좌석을 예약할 때") {
            it("IllegalStateException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val mockToken = WaitingToken.create(token, userId)
                
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { seatService.isSeatAvailable(seatId) } returns false
                
                // when & then
                shouldThrow<IllegalStateException> {
                    reservationUseCase.reserveSeat(userId, concertId, seatId, token)
                }
                
                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { seatService.isSeatAvailable(seatId) }
            }
        }

        context("토큰이 만료된 상태일 때") {
            it("TokenActivationException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "expired-token"

                every { tokenUseCase.validateActiveToken(token) } throws TokenActivationException("토큰이 만료되었습니다")

                // when & then
                shouldThrow<TokenActivationException> {
                    reservationUseCase.reserveSeat(userId, concertId, seatId, token)
                }

                verify { tokenUseCase.validateActiveToken(token) }
            }
        }

        context("존재하지 않는 토큰으로 예약할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "non-existent-token"

                every { tokenUseCase.validateActiveToken(token) } throws TokenNotFoundException("토큰을 찾을 수 없습니다")

                // when & then
                shouldThrow<TokenNotFoundException> {
                    reservationUseCase.reserveSeat(userId, concertId, seatId, token)
                }

                verify { tokenUseCase.validateActiveToken(token) }
            }
        }

        context("이미 예약된 좌석을 예약할 때") {
            it("IllegalStateException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token"

                val mockToken = WaitingToken.create(token, userId)

                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { seatService.isSeatAvailable(seatId) } returns false

                // when & then
                shouldThrow<IllegalStateException> {
                    reservationUseCase.reserveSeat(userId, concertId, seatId, token)
                }

                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { seatService.isSeatAvailable(seatId) }
            }
        }
    }
    
    describe("confirmReservation") {
        context("유효한 예약을 확정할 때") {
            it("예약을 확정해야 한다") {
                // given
                val reservationId = 1L
                val paymentId = 1L
                val reservation = mockk<Reservation>()
                
                every { reservationService.confirmReservation(reservationId, paymentId) } returns reservation
                
                // when
                val result = reservationUseCase.confirmReservation(reservationId, paymentId)
                
                // then
                result shouldNotBe null
                verify { reservationService.confirmReservation(reservationId, paymentId) }
            }
        }
    }
    
    describe("cancelReservation") {
        context("유효한 사용자가 본인 예약을 취소할 때") {
            it("예약을 취소해야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val cancelReason = "사용자 요청"
                val token = "valid-token"
                
                val reservation = mockk<Reservation>()
                
                val mockToken = WaitingToken.create(token, userId)
                
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationService.cancelReservation(reservationId, userId, cancelReason) } returns reservation
                
                // when
                val result = reservationUseCase.cancelReservation(reservationId, userId, cancelReason, token)
                
                // then
                result shouldNotBe null
                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { reservationService.cancelReservation(reservationId, userId, cancelReason) }
            }
        }
        
        context("존재하지 않는 사용자가 예약을 취소할 때") {
            it("UserNotFoundException을 던져야 한다") {
                // given
                val reservationId = 1L
                val userId = 999L
                val cancelReason = "사용자 요청"
                val token = "valid-token"
                
                val mockToken = WaitingToken.create(token, userId)
                
                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns false
                
                // when & then
                shouldThrow<UserNotFoundException> {
                    reservationUseCase.cancelReservation(reservationId, userId, cancelReason, token)
                }
                
                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
            }
        }

        context("이미 확정된 예약을 취소하려 할 때") {
            it("예약 서비스에서 적절한 예외를 던져야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val cancelReason = "사용자 요청"
                val token = "valid-token"

                val mockToken = WaitingToken.create(token, userId)

                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationService.cancelReservation(reservationId, userId, cancelReason) } throws
                        IllegalStateException("확정된 예약은 취소할 수 없습니다")

                // when & then
                shouldThrow<IllegalStateException> {
                    reservationUseCase.cancelReservation(reservationId, userId, cancelReason, token)
                }

                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { reservationService.cancelReservation(reservationId, userId, cancelReason) }
            }
        }

        context("null 취소 사유로 예약을 취소할 때") {
            it("null 사유로도 정상 처리되어야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val cancelReason: String? = null
                val token = "valid-token"

                val mockToken = WaitingToken.create(token, userId)
                val reservation = mockk<Reservation>()

                every { tokenUseCase.validateActiveToken(token) } returns mockToken
                every { userService.existsById(userId) } returns true
                every { reservationService.cancelReservation(reservationId, userId, cancelReason) } returns reservation

                // when
                val result = reservationUseCase.cancelReservation(reservationId, userId, cancelReason, token)

                // then
                result shouldNotBe null
                verify { tokenUseCase.validateActiveToken(token) }
                verify { userService.existsById(userId) }
                verify { reservationService.cancelReservation(reservationId, userId, cancelReason) }
            }
        }
    }
    
    describe("getReservationById") {
        context("존재하는 예약 ID로 조회할 때") {
            it("해당 예약을 반환해야 한다") {
                // given
                val reservationId = 1L
                val reservation = mockk<Reservation>()
                
                every { reservationService.getReservationById(reservationId) } returns reservation
                
                // when
                val result = reservationUseCase.getReservationById(reservationId)
                
                // then
                result shouldNotBe null
                result shouldBe reservation
                verify { reservationService.getReservationById(reservationId) }
            }
        }
    }
    
    describe("getReservationsByCondition") {
        context("조건으로 예약 목록을 조회할 때") {
            it("해당 조건의 예약 목록을 반환해야 한다") {
                // given
                val condition = ReservationSearchCondition(
                    userId = 1L,
                    pageNumber = 1,
                    pageSize = 10
                )
                val page = ReservationDto.Page(
                    reservations = emptyList(),
                    totalCount = 0,
                    pageNumber = 1,
                    totalPages = 1  ,
                    pageSize = 10
                )
                
                every { reservationService.getReservationsByCondition(condition) } returns page
                
                // when
                val result = reservationUseCase.getReservationsByCondition(condition)
                
                // then
                result shouldNotBe null
                result.pageNumber shouldBe 1
                result.pageSize shouldBe 10
                verify { reservationService.getReservationsByCondition(condition) }
            }
        }
    }
    
    describe("cleanupExpiredReservations") {
        context("만료된 예약이 있을 때") {
            it("만료된 예약들을 정리해야 한다") {
                // given
                val cleanupCount = 2
                
                every { reservationService.cleanupExpiredReservations() } returns cleanupCount
                
                // when
                val result = reservationUseCase.cleanupExpiredReservations()
                
                // then
                result shouldBe 2
                verify { reservationService.cleanupExpiredReservations() }
            }
        }
        
        context("만료된 예약이 없을 때") {
            it("0을 반환해야 한다") {
                // given
                every { reservationService.cleanupExpiredReservations() } returns 0
                
                // when
                val result = reservationUseCase.cleanupExpiredReservations()
                
                // then
                result shouldBe 0
                verify { reservationService.cleanupExpiredReservations() }
            }
        }
    }
})