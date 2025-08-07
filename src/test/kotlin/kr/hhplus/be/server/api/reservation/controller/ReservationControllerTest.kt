package kr.hhplus.be.server.api.reservation.controller

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.api.reservation.dto.ReservationDto
import kr.hhplus.be.server.api.reservation.dto.request.ReservationCreateRequest
import kr.hhplus.be.server.api.reservation.usecase.ReserveSeatUseCase
import kr.hhplus.be.server.api.reservation.usecase.CancelReservationUseCase
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import java.math.BigDecimal
import java.time.LocalDateTime

class ReservationControllerTest : DescribeSpec({
    
    describe("createReservation") {
        context("유효한 예약 생성 요청이 들어올 때") {
            it("예약을 생성하고 응답을 반환해야 한다") {
                // given
                val reservationService = mockk<ReservationService>()
                val reserveSeatUseCase = mockk<ReserveSeatUseCase>()
                val cancelReservationUseCase = mockk<CancelReservationUseCase>()
                val reservationController = ReservationController(reservationService, reserveSeatUseCase, cancelReservationUseCase)
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "test-token"
                val request = ReservationCreateRequest(userId, concertId, seatId, token)
                val reservationDto = ReservationDto(
                    reservationId = 1L,
                    userId = userId,
                    concertId = concertId,
                    seatId = seatId,
                    paymentId = null,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    statusCode = "TEMP",
                    statusName = "임시예약",
                    statusDescription = "임시 예약 상태",
                    reservedAt = LocalDateTime.now(),
                    expiresAt = LocalDateTime.now().plusMinutes(5),
                    confirmedAt = null
                )
                
                val mockStatus = mockk<kr.hhplus.be.server.domain.reservation.model.ReservationStatusType>()
                every { mockStatus.code } returns "TEMP"
                every { mockStatus.name } returns "임시예약"
                every { mockStatus.description } returns "임시 예약 상태"
                
                val mockReservation = mockk<kr.hhplus.be.server.domain.reservation.model.Reservation>(relaxed = true)
                every { mockReservation.reservationId } returns 1L
                every { mockReservation.userId } returns userId
                every { mockReservation.concertId } returns concertId
                every { mockReservation.seatId } returns seatId
                every { mockReservation.seatNumber } returns "A1"
                every { mockReservation.price } returns BigDecimal("50000")
                every { mockReservation.status } returns mockStatus
                every { mockReservation.reservedAt } returns LocalDateTime.now()
                every { mockReservation.expiresAt } returns LocalDateTime.now().plusMinutes(5)
                every { mockReservation.confirmedAt } returns null
                every { mockReservation.paymentId } returns null
                every { reserveSeatUseCase.execute(userId, concertId, seatId, token) } returns mockReservation
                
                // when
                val response = reservationController.createReservation(request)
                
                // then
                response shouldNotBe null
                response.statusCode.value() shouldBe 201
                response.body?.success shouldBe true
                response.body?.message shouldBe "좌석 예약 완료"
            }

        context("존재하지 않는 예약 ID로 조회할 때") {
            it("예외를 처리해야 한다") {
                // given
                val reservationService = mockk<ReservationService>()
                val reserveSeatUseCase = mockk<ReserveSeatUseCase>()
                val cancelReservationUseCase = mockk<CancelReservationUseCase>()
                val reservationController = ReservationController(reservationService, reserveSeatUseCase, cancelReservationUseCase)
                val invalidReservationId = 999L
                
                every { reservationService.getReservationWithDetails(invalidReservationId) } throws NoSuchElementException("예약을 찾을 수 없습니다")
                
                // when & then
                shouldThrow<NoSuchElementException> {
                    reservationController.getReservation(invalidReservationId)
                }
            }
        }

        context("잘못된 요청 데이터로 예약 생성 시") {
            it("예외를 처리해야 한다") {
                // given
                val reservationService = mockk<ReservationService>()
                val reserveSeatUseCase = mockk<ReserveSeatUseCase>()
                val cancelReservationUseCase = mockk<CancelReservationUseCase>()
                val reservationController = ReservationController(reservationService, reserveSeatUseCase, cancelReservationUseCase)
                val userId = 0L // 잘못된 userId
                val concertId = 1L
                val seatId = 1L
                val token = "test-token"
                val request = ReservationCreateRequest(userId, concertId, seatId, token)
                
                every { reserveSeatUseCase.execute(userId, concertId, seatId, token) } throws IllegalArgumentException("잘못된 사용자 ID")
                
                // when & then
                shouldThrow<IllegalArgumentException> {
                    reservationController.createReservation(request)
                }
            }
        }
        }
    }

    describe("getReservation") {
        context("존재하는 예약 ID로 조회할 때") {
            it("예약 정보를 반환해야 한다") {
                // given
                val reservationService = mockk<ReservationService>()
                val reserveSeatUseCase = mockk<ReserveSeatUseCase>()
                val cancelReservationUseCase = mockk<CancelReservationUseCase>()
                val reservationController = ReservationController(reservationService, reserveSeatUseCase, cancelReservationUseCase)
                val reservationId = 1L
                val reservationDto = ReservationDto(
                    reservationId = reservationId,
                    userId = 1L,
                    concertId = 1L,
                    seatId = 1L,
                    paymentId = 1L,
                    seatNumber = "A1",
                    price = BigDecimal("50000"),
                    statusCode = "CONF",
                    statusName = "확정",
                    statusDescription = "확정된 예약",
                    reservedAt = LocalDateTime.now(),
                    expiresAt = null,
                    confirmedAt = LocalDateTime.now()
                )
                
                val mockStatus = mockk<kr.hhplus.be.server.domain.reservation.model.ReservationStatusType>()
                every { mockStatus.code } returns "CONF"
                every { mockStatus.name } returns "확정"
                every { mockStatus.description } returns "확정된 예약"
                
                val mockReservation = mockk<kr.hhplus.be.server.domain.reservation.model.Reservation>(relaxed = true)
                every { mockReservation.reservationId } returns reservationId
                every { mockReservation.userId } returns 1L
                every { mockReservation.concertId } returns 1L
                every { mockReservation.seatId } returns 1L
                every { mockReservation.seatNumber } returns "A1"
                every { mockReservation.price } returns BigDecimal("50000")
                every { mockReservation.status } returns mockStatus
                every { mockReservation.reservedAt } returns LocalDateTime.now()
                every { mockReservation.expiresAt } returns null
                every { mockReservation.confirmedAt } returns LocalDateTime.now()
                every { mockReservation.paymentId } returns 1L
                every { reservationService.getReservationWithDetails(reservationId) } returns mockReservation
                
                // when
                val response = reservationController.getReservation(reservationId)
                
                // then
                response shouldNotBe null
                response.statusCode.value() shouldBe 200
                response.body?.success shouldBe true
                response.body?.message shouldBe "예약 정보 조회 완료"
            }
        }
    }
})
