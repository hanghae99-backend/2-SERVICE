package kr.hhplus.be.server.api.reservation.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.api.reservation.usecase.ReserveSeatUseCase
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.auth.service.TokenDomainService
import kr.hhplus.be.server.domain.auth.service.TokenLifecycleManager
import kr.hhplus.be.server.domain.auth.models.TokenStatus
import kr.hhplus.be.server.domain.auth.models.WaitingToken
import kr.hhplus.be.server.domain.auth.exception.TokenActivationException
import kr.hhplus.be.server.domain.auth.exception.TokenNotFoundException
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatusType
import java.math.BigDecimal
import java.time.LocalDateTime

class ReserveSeatUseCaseTest : DescribeSpec({
    
    val reservationService = mockk<ReservationService>()
    val seatService = mockk<SeatService>()
    val tokenDomainService = mockk<TokenDomainService>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()

    val reserveSeatUseCase = ReserveSeatUseCase(
        reservationService,
        seatService,
        tokenDomainService,
        tokenLifecycleManager
    )
    
    describe("execute") {
        context("유효한 토큰과 예약 가능한 좌석으로 예약할 때") {
            it("예약을 성공적으로 생성해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val waitingToken = WaitingToken.create(token, userId)
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val reservation = Reservation.createTemporary(userId, concertId, seatId, "A1", BigDecimal("100000"), temporaryStatus)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) } returns Unit
                every { seatService.isSeatAvailable(seatId) } returns true
                every { reservationService.reserveSeat(userId, concertId, seatId) } returns reservation
                
                // when
                val result = reserveSeatUseCase.execute(userId, concertId, seatId, token)
                
                // then
                result shouldNotBe null
                result.userId shouldBe userId
                result.concertId shouldBe concertId
                result.seatId shouldBe seatId
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) }
                verify { seatService.isSeatAvailable(seatId) }
                verify { reservationService.reserveSeat(userId, concertId, seatId) }
            }
        }
        
        context("비활성화된 토큰으로 예약할 때") {
            it("TokenActivationException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "inactive-token"
                
                val waitingToken = WaitingToken.create(token, userId)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.WAITING) } throws 
                    TokenActivationException("활성화된 토큰이 아닙니다")
                
                // when & then
                shouldThrow<TokenActivationException> {
                    reserveSeatUseCase.execute(userId, concertId, seatId, token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.WAITING) }
            }
        }
        
        context("존재하지 않는 토큰으로 예약할 때") {
            it("TokenNotFoundException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "non-existent-token"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.EXPIRED
                every { tokenDomainService.validateActiveToken(null, TokenStatus.EXPIRED) } throws 
                    TokenNotFoundException("유효하지 않은 토큰입니다")
                
                // when & then
                shouldThrow<TokenNotFoundException> {
                    reserveSeatUseCase.execute(userId, concertId, seatId, token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(null, TokenStatus.EXPIRED) }
            }
        }
        
        context("예약 불가능한 좌석으로 예약할 때") {
            it("IllegalStateException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token"
                
                val waitingToken = WaitingToken.create(token, userId)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) } returns Unit
                every { seatService.isSeatAvailable(seatId) } returns false
                
                // when & then
                shouldThrow<IllegalStateException> {
                    reserveSeatUseCase.execute(userId, concertId, seatId, token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) }
                verify { seatService.isSeatAvailable(seatId) }
            }
        }
    }
})
