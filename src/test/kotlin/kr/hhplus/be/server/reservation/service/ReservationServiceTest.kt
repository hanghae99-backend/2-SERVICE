package kr.hhplus.be.server.reservation.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import kr.hhplus.be.server.global.event.DomainEventPublisher
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.reservation.dto.request.ReservationSearchCondition
import kr.hhplus.be.server.reservation.entity.Reservation
import kr.hhplus.be.server.reservation.entity.ReservationStatusType
import kr.hhplus.be.server.reservation.repository.ReservationRepository
import kr.hhplus.be.server.reservation.repository.ReservationStatusTypePojoRepository
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDateTime

class ReservationServiceTest : DescribeSpec({
    
    val reservationRepository = mockk<ReservationRepository>()
    val reservationStatusTypePojoRepository = mockk<ReservationStatusTypePojoRepository>()
    val distributedLock = mockk<DistributedLock>()
    val eventPublisher = mockk<DomainEventPublisher>()

    val reservationService = ReservationService(reservationRepository,reservationStatusTypePojoRepository, distributedLock, eventPublisher)
    
    // DistributedLock executeWithLock 메서드의 기본 동작 설정
    fun setupDistributedLockMock() {
        every { 
            distributedLock.executeWithLock<Any>(
                lockKey = any(),
                lockTimeoutMs = any(),
                waitTimeoutMs = any(),
                action = any()
            )
        } answers {
            val action = args[3] as () -> Any
            action.invoke()
        }
    }
    
    // EventPublisher mock 설정
    fun setupEventPublisherMock() {
        justRun { eventPublisher.publish(any()) }
    }
    
    describe("reserveSeat") {
        context("예약 가능한 좌석을 예약할 때") {
            it("임시 예약을 생성해야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "test-token"
                
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val confirmedStatus = ReservationStatusType("CONFIRMED", "확정", "확정 예약 상태", true, LocalDateTime.now())
                val activeStatuses = listOf(temporaryStatus.code, confirmedStatus.code)
                val reservation = Reservation.createTemporary(userId, concertId, seatId, "A1", BigDecimal("50000"), temporaryStatus)
                
                setupDistributedLockMock()
                setupEventPublisherMock()
                every { reservationStatusTypePojoRepository.getTemporaryStatus() } returns temporaryStatus
                every { reservationStatusTypePojoRepository.getConfirmedStatus() } returns confirmedStatus
                every { reservationRepository.findBySeatIdAndStatusCodeIn(seatId, activeStatuses) } returns null
                every { reservationRepository.save(any()) } returns reservation
                
                // when
                val result = reservationService.reserveSeat(userId, concertId, seatId, token)
                
                // then
                result shouldNotBe null
                result.userId shouldBe userId
                result.concertId shouldBe concertId
                result.seatId shouldBe seatId
            }
        }
        
        context("이미 확정된 좌석을 예약할 때") {
            it("IllegalStateException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "test-token"
                
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val confirmedStatus = ReservationStatusType("CONFIRMED", "확정", "확정 예약 상태", true, LocalDateTime.now())
                val activeStatuses = listOf(temporaryStatus.code, confirmedStatus.code)
                val existingReservation = mockk<Reservation>(relaxed = true)
                
                setupDistributedLockMock()
                every { reservationStatusTypePojoRepository.getTemporaryStatus() } returns temporaryStatus
                every { reservationStatusTypePojoRepository.getConfirmedStatus() } returns confirmedStatus
                every { reservationRepository.findBySeatIdAndStatusCodeIn(seatId, activeStatuses) } returns existingReservation
                every { existingReservation.isConfirmed() } returns true
                
                // when & then
                shouldThrow<IllegalStateException> {
                    reservationService.reserveSeat(userId, concertId, seatId, token)
                }
            }
        }
        
        context("임시 점유 중인 좌석을 예약할 때") {
            it("IllegalStateException을 던져야 한다") {
                // given
                val userId = 1L
                val concertId = 1L
                val seatId = 1L
                val token = "test-token"
                
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val confirmedStatus = ReservationStatusType("CONFIRMED", "확정", "확정 예약 상태", true, LocalDateTime.now())
                val activeStatuses = listOf(temporaryStatus.code, confirmedStatus.code)
                val existingReservation = mockk<Reservation>(relaxed = true)
                
                setupDistributedLockMock()
                every { reservationStatusTypePojoRepository.getTemporaryStatus() } returns temporaryStatus
                every { reservationStatusTypePojoRepository.getConfirmedStatus() } returns confirmedStatus
                every { reservationRepository.findBySeatIdAndStatusCodeIn(seatId, activeStatuses) } returns existingReservation
                every { existingReservation.isConfirmed() } returns false
                every { existingReservation.isTemporary() } returns true
                every { existingReservation.isExpired() } returns false
                
                // when & then
                shouldThrow<IllegalStateException> {
                    reservationService.reserveSeat(userId, concertId, seatId, token)
                }
            }
        }
    }
    
    describe("confirmReservation") {
        context("유효한 예약을 확정할 때") {
            it("예약을 확정 상태로 변경해야 한다") {
                // given
                val reservationId = 1L
                val paymentId = 1L
                val reservation = mockk<Reservation>(relaxed = true)
                val confirmedStatus = ReservationStatusType("CONFIRMED", "확정", "확정 예약 상태", true, LocalDateTime.now())
                
                setupEventPublisherMock()
                every { reservationRepository.findById(reservationId) } returns reservation
                every { reservationStatusTypePojoRepository.getConfirmedStatus() } returns confirmedStatus
                every { reservationRepository.save(any()) } returns reservation
                
                // when
                val result = reservationService.confirmReservation(reservationId, paymentId)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("존재하지 않는 예약을 확정할 때") {
            it("IllegalArgumentException을 던져야 한다") {
                // given
                val reservationId = 999L
                val paymentId = 1L
                
                every { reservationRepository.findById(reservationId) } returns null
                
                // when & then
                shouldThrow<IllegalArgumentException> {
                    reservationService.confirmReservation(reservationId, paymentId)
                }
            }
        }
    }
    
    describe("cancelReservation") {
        context("본인의 예약을 취소할 때") {
            it("예약을 취소 상태로 변경해야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val cancelReason = "사용자 요청"
                val reservation = mockk<Reservation>(relaxed = true)
                val cancelledStatus = ReservationStatusType("CANCELLED", "취소", "취소된 예약 상태", true, LocalDateTime.now())
                
                setupEventPublisherMock()
                every { reservationRepository.findById(reservationId) } returns reservation
                every { reservation.userId } returns userId
                every { reservationStatusTypePojoRepository.getCancelledStatus() } returns cancelledStatus
                every { reservationRepository.save(any()) } returns reservation
                
                // when
                val result = reservationService.cancelReservation(reservationId, userId, cancelReason)
                
                // then
                result shouldNotBe null
            }
        }
        
        context("다른 사용자의 예약을 취소할 때") {
            it("IllegalArgumentException을 던져야 한다") {
                // given
                val reservationId = 1L
                val userId = 1L
                val otherUserId = 2L
                val cancelReason = "사용자 요청"
                val reservation = mockk<Reservation>(relaxed = true)
                
                every { reservationRepository.findById(reservationId) } returns reservation
                every { reservation.userId } returns otherUserId
                
                // when & then
                shouldThrow<IllegalArgumentException> {
                    reservationService.cancelReservation(reservationId, userId, cancelReason)
                }
            }
        }
    }
    
    describe("getReservationById") {
        context("존재하는 예약 ID로 조회할 때") {
            it("해당 예약을 반환해야 한다") {
                // given
                val reservationId = 1L
                val reservation = mockk<Reservation>(relaxed = true)
                
                every { reservationRepository.findById(reservationId) } returns reservation
                
                // when
                val result = reservationService.getReservationById(reservationId)
                
                // then
                result shouldNotBe null
                result shouldBe reservation
            }
        }
        
        context("존재하지 않는 예약 ID로 조회할 때") {
            it("IllegalArgumentException을 던져야 한다") {
                // given
                val reservationId = 999L
                
                every { reservationRepository.findById(reservationId) } returns null
                
                // when & then
                shouldThrow<IllegalArgumentException> {
                    reservationService.getReservationById(reservationId)
                }
            }
        }
    }
    
    describe("getReservationsByCondition") {
        context("사용자 ID로 예약 목록을 조회할 때") {
            it("해당 사용자의 예약 목록을 반환해야 한다") {
                // given
                val condition = ReservationSearchCondition(
                    userId = 1L,
                    pageNumber = 1,
                    pageSize = 10
                )
                val reservations = listOf(mockk<Reservation>(relaxed = true))
                val page = PageImpl(reservations, PageRequest.of(0, 10), 1)
                
                every { reservationRepository.findByUserIdOrderByReservedAtDesc(1L, any()) } returns page
                
                // when
                val result = reservationService.getReservationsByCondition(condition)
                
                // then
                result shouldNotBe null
                result.totalCount shouldBe 1
                result.pageNumber shouldBe 1
                result.pageSize shouldBe 10
            }
        }
        
        context("콘서트 ID로 예약 목록을 조회할 때") {
            it("해당 콘서트의 예약 목록을 반환해야 한다") {
                // given
                val condition = ReservationSearchCondition(
                    concertId = 1L,
                    pageNumber = 1,
                    pageSize = 10
                )
                val reservations = listOf(mockk<Reservation>(relaxed = true))
                val page = PageImpl(reservations, PageRequest.of(0, 10), 1)
                
                every { reservationRepository.findByConcertIdOrderByReservedAtDesc(1L, any()) } returns page
                
                // when
                val result = reservationService.getReservationsByCondition(condition)
                
                // then
                result shouldNotBe null
                result.totalCount shouldBe 1
            }
        }
    }
    
    describe("cleanupExpiredReservations") {
        context("만료된 예약이 있을 때") {
            it("만료된 예약들을 취소 상태로 변경해야 한다") {
                // given
                val expiredReservation1 = mockk<Reservation>(relaxed = true)
                val expiredReservation2 = mockk<Reservation>(relaxed = true)
                val expiredReservations = listOf(expiredReservation1, expiredReservation2)
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                val cancelledStatus = ReservationStatusType("CANCELLED", "취소", "취소된 예약 상태", true, LocalDateTime.now())
                
                setupEventPublisherMock()
                every { reservationStatusTypePojoRepository.getTemporaryStatus() } returns temporaryStatus
                every { reservationStatusTypePojoRepository.getCancelledStatus() } returns cancelledStatus
                every { 
                    reservationRepository.findByExpiresAtBeforeAndStatusCode(any(), temporaryStatus.code) 
                } returns expiredReservations
                every { reservationRepository.save(any()) } returnsMany expiredReservations
                
                // when
                val result = reservationService.cleanupExpiredReservations()
                
                // then
                result shouldBe 2
            }
        }
        
        context("만료된 예약이 없을 때") {
            it("0을 반환해야 한다") {
                // given
                val temporaryStatus = ReservationStatusType("TEMPORARY", "임시예약", "임시 예약 상태", true, LocalDateTime.now())
                
                every { reservationStatusTypePojoRepository.getTemporaryStatus() } returns temporaryStatus
                every { 
                    reservationRepository.findByExpiresAtBeforeAndStatusCode(any(), temporaryStatus.code) 
                } returns emptyList()
                
                // when
                val result = reservationService.cleanupExpiredReservations()
                
                // then
                result shouldBe 0
            }
        }
    }
})
