package kr.hhplus.be.server.api.reservation.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.reservation.exception.ReservationCancelFailedException
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.global.event.DomainEventPublisher
import java.math.BigDecimal

class CancelReservationUseCaseTest : DescribeSpec({

    val reservationService = mockk<ReservationService>()
    val tokenDomainService = mockk<TokenDomainService>(relaxed = true)
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    val eventPublisher = mockk<DomainEventPublisher>()

    val cancelReservationUseCase = CancelReservationUseCase(
        reservationService,
        tokenDomainService,
        tokenLifecycleManager,
        eventPublisher
    )

    describe("CancelReservationUseCase") {
        context("정상적인 예약 취소 요청이 들어오면") {
            it("예약을 성공적으로 취소한다") {
                // given
                val reservationId = 1L
                val userId = 10L
                val token = "valid-token-12345"
                val cancelReason = "변경된 일정"
                val waitingToken = WaitingToken.create(token, userId)
                val tokenStatus = TokenStatus.ACTIVE

                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, "NORMAL", 0, false, 5)
                val reservation = Reservation.createTemporary(userId, 1L, 1L, "A1", BigDecimal("50000"), temporaryStatus)
                reservation.reservationId = reservationId

                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns tokenStatus
                every { reservationService.getReservationById(reservationId) } returns reservation
                every {
                    reservationService.cancelReservation(reservationId, userId, cancelReason)
                } returns reservation
                every { eventPublisher.publish(any()) } returns Unit

                // when
                val result = cancelReservationUseCase.execute(reservationId, userId, cancelReason, token)

                // then
                result shouldBe reservation
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, tokenStatus) }
                verify { reservationService.getReservationById(reservationId) }
                verify { reservationService.cancelReservation(reservationId, userId, cancelReason) }
            }
        }

        context("비활성화된 토큰으로 요청할 때") {
            it("ReservationCancelFailedException을 던진다") {
                // given
                val reservationId = 1L
                val userId = 10L
                val token = "expired-token-12345"
                val cancelReason = "변경된 일정"
                val waitingToken = WaitingToken.create(token, userId)
                val tokenStatus = TokenStatus.EXPIRED

                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns tokenStatus
                every { tokenDomainService.validateActiveToken(waitingToken, tokenStatus) } throws 
                    TokenActivationException("활성화된 토큰이 아닙니다")

                // when & then
                shouldThrow<ReservationCancelFailedException> {
                    cancelReservationUseCase.execute(reservationId, userId, cancelReason, token)
                }

                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, tokenStatus) }
            }
        }
    }
})
