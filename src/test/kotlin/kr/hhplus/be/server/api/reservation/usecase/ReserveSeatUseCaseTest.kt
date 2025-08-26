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
import kr.hhplus.be.server.domain.reservation.models.Reservation
import kr.hhplus.be.server.domain.reservation.models.ReservationStatusType
import kr.hhplus.be.server.config.TestDataFixture
import kr.hhplus.be.server.domain.reservation.exception.ReservationFailedException
import kr.hhplus.be.server.api.concert.dto.SeatDto
import kr.hhplus.be.server.global.event.DomainEventPublisher
import java.math.BigDecimal
import java.time.LocalDateTime

class ReserveSeatUseCaseTest : DescribeSpec({
    
    val reservationService = mockk<ReservationService>()
    val seatService = mockk<SeatService>()
    val tokenDomainService = mockk<TokenDomainService>()
    val tokenLifecycleManager = mockk<TokenLifecycleManager>()
    val eventPublisher = mockk<DomainEventPublisher>()

    val reserveSeatUseCase = ReserveSeatUseCase(
        reservationService,
        seatService,
        tokenDomainService,
        tokenLifecycleManager,
        eventPublisher
    )
    
    describe("execute") {
        context("유효한 토큰과 예약 가능한 좌석으로 예약할 때") {
            it("예약을 성공적으로 생성해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token-12345"
                
                val waitingToken = WaitingToken.create(token, userId)
                val temporaryStatus = TestDataFixture.createReservationStatusType()
                val reservation = Reservation.createTemporary(userId, concertId, seatId, "A1", BigDecimal("100000"), temporaryStatus)
                
                val seatDto = mockk<SeatDto> {
                    every { this@mockk.seatId } returns seatId
                    every { this@mockk.price } returns BigDecimal("100000")
                    every { this@mockk.seatNumber } returns "A1"
                    every { this@mockk.statusCode } returns "AVAILABLE"
                }
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) } returns Unit
                every { seatService.getSeatById(seatId) } returns seatDto
                every { reservationService.reserveSeat(userId, concertId, seatId) } returns reservation
                every { eventPublisher.publish(any()) } returns Unit
                
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
                verify { seatService.getSeatById(seatId) }
                verify { reservationService.reserveSeat(userId, concertId, seatId) }
            }
        }
        
        context("비활성화된 토큰으로 예약할 때") {
            it("ReservationFailedException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "inactive-token-12345"
                
                val waitingToken = WaitingToken.create(token, userId)
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.WAITING
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.WAITING) } throws 
                    TokenActivationException("활성화된 토큰이 아닙니다")
                
                // when & then
                shouldThrow<ReservationFailedException> {
                    reserveSeatUseCase.execute(userId, concertId, seatId, token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.WAITING) }
            }
        }
        
        context("존재하지 않는 토큰으로 예약할 때") {
            it("ReservationFailedException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "non-existent-token-12345"
                
                every { tokenLifecycleManager.findToken(token) } returns null
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.EXPIRED
                every { tokenDomainService.validateActiveToken(null, TokenStatus.EXPIRED) } throws 
                    TokenNotFoundException("유효하지 않은 토큰입니다")
                
                // when & then
                shouldThrow<ReservationFailedException> {
                    reserveSeatUseCase.execute(userId, concertId, seatId, token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(null, TokenStatus.EXPIRED) }
            }
        }
        
        context("예약 불가능한 좌석으로 예약할 때") {
            it("ReservationFailedException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "valid-token-12345"
                
                val waitingToken = WaitingToken.create(token, userId)
                val seatDto = mockk<SeatDto> {
                    every { this@mockk.seatId } returns seatId
                    every { this@mockk.price } returns BigDecimal("100000")
                    every { this@mockk.seatNumber } returns "A1"
                    every { this@mockk.statusCode } returns "RESERVED"
                }
                
                every { tokenLifecycleManager.findToken(token) } returns waitingToken
                every { tokenLifecycleManager.getTokenStatus(token) } returns TokenStatus.ACTIVE
                every { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) } returns Unit
                every { seatService.getSeatById(seatId) } returns seatDto
                
                // when & then
                shouldThrow<ReservationFailedException> {
                    reserveSeatUseCase.execute(userId, concertId, seatId, token)
                }
                
                verify { tokenLifecycleManager.findToken(token) }
                verify { tokenLifecycleManager.getTokenStatus(token) }
                verify { tokenDomainService.validateActiveToken(waitingToken, TokenStatus.ACTIVE) }
                verify { seatService.getSeatById(seatId) }
            }
        }
    }
})
