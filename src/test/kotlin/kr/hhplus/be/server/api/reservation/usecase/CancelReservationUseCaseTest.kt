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
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.service.ReservationService

class CancelReservationUseCaseTest : DescribeSpec({

    val reservationService = mockk<ReservationService>()
    val tokenDomainService = mockk<TokenDomainService>(relaxed = true)
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()

    val cancelReservationUseCase = CancelReservationUseCase(
        reservationService,
        tokenDomainService,
        tokenLifecycleManager
    )

    describe("CancelReservationUseCase") {
        context("정상적인 예약 취소 요청이 들어오면") {
            it("예약을 성공적으로 취소한다") {
                // given
                val reservationId = 1L
                val userId = 10L
                val token = "valid-token"
                val cancelReason = "변경된 일정"
                val waitingToken = WaitingToken.create(token, userId)
                val tokenStatus = TokenStatus.ACTIVE

                val reservation = mockk<Reservation>()

                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns tokenStatus
                every {
                    reservationService.cancelReservation(reservationId, userId, cancelReason)
                } returns reservation

                // when
                val result = cancelReservationUseCase.execute(reservationId, userId, cancelReason, token)

                // then
                result shouldBe reservation
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, tokenStatus) }
                verify { reservationService.cancelReservation(reservationId, userId, cancelReason) }
            }
        }

        context("예약이 이미 확정되어 있어 취소할 수 없는 경우") {
            it("IllegalStateException을 던진다") {
                // given
                val reservationId = 1L
                val userId = 10L
                val token = "valid-token"
                val cancelReason = "변경된 일정"
                val waitingToken = WaitingToken.create(token, userId)
                val tokenStatus = TokenStatus.EXPIRED

                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns tokenStatus
                every {
                    reservationService.cancelReservation(reservationId, userId, cancelReason)
                } throws IllegalStateException("확정된 예약은 취소할 수 없습니다")

                // when & then
                shouldThrow<IllegalStateException> {
                    cancelReservationUseCase.execute(reservationId, userId, cancelReason, token)
                }

                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, tokenStatus) }
                verify { reservationService.cancelReservation(reservationId, userId, cancelReason) }
            }
        }
    }
})
