package kr.hhplus.be.server.payment.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.time.LocalDateTime

class ReservationUnitTest : BehaviorSpec({
    
    given("Reservation 도메인") {
        When("Reservation.create()로 생성할 때") {
            Then("정상적으로 생성된다") {
                val userId = 1L
                val seatId = 1L
                
                val reservation = Reservation.create(userId, seatId)
                
                reservation.userId shouldBe userId
                reservation.seatId shouldBe seatId
                reservation.status shouldBe ReservationStatus.TEMPORARY
                reservation.createdAt.shouldNotBeNull()
                reservation.expiresAt.shouldNotBeNull()
                reservation.confirmedAt.shouldBeNull()
            }
            
            Then("만료 시간이 5분 후로 설정된다") {
                val userId = 1L
                val seatId = 1L
                val beforeCreate = LocalDateTime.now()
                
                val reservation = Reservation.create(userId, seatId)
                
                val afterCreate = LocalDateTime.now()
                val expectedExpiry = beforeCreate.plusMinutes(5)
                
                reservation.expiresAt!! shouldBe expectedExpiry.withSecond(0).withNano(0)
            }
        }
        
        When("예약 확정 처리할 때") {
            Then("TEMPORARY 상태에서 CONFIRMED로 변경된다") {
                val reservation = Reservation.create(1L, 1L)
                
                val confirmedReservation = reservation.confirm()
                
                confirmedReservation.status shouldBe ReservationStatus.CONFIRMED
                confirmedReservation.confirmedAt.shouldNotBeNull()
                confirmedReservation.userId shouldBe reservation.userId
                confirmedReservation.seatId shouldBe reservation.seatId
            }
            
            Then("TEMPORARY 상태가 아닌 경우 예외가 발생한다") {
                val reservation = Reservation.create(1L, 1L)
                val confirmedReservation = reservation.confirm()
                
                shouldThrow<IllegalStateException> {
                    confirmedReservation.confirm()
                }
            }
        }
        
        When("예약 취소 처리할 때") {
            Then("TEMPORARY 상태에서 CANCELLED로 변경된다") {
                val reservation = Reservation.create(1L, 1L)
                
                val cancelledReservation = reservation.cancel()
                
                cancelledReservation.status shouldBe ReservationStatus.CANCELLED
                cancelledReservation.userId shouldBe reservation.userId
                cancelledReservation.seatId shouldBe reservation.seatId
            }
            
            Then("CONFIRMED 상태에서 취소 시 예외가 발생한다") {
                val reservation = Reservation.create(1L, 1L)
                val confirmedReservation = reservation.confirm()
                
                shouldThrow<IllegalStateException> {
                    confirmedReservation.cancel()
                }
            }
        }
        
        When("예약 만료 확인할 때") {
            Then("만료 시간이 지나면 true를 반환한다") {
                val pastTime = LocalDateTime.now().minusMinutes(10)
                val reservation = Reservation(
                    userId = 1L,
                    seatId = 1L,
                    status = ReservationStatus.TEMPORARY,
                    expiresAt = pastTime
                )
                
                reservation.isExpired() shouldBe true
            }
            
            Then("만료 시간이 지나지 않으면 false를 반환한다") {
                val futureTime = LocalDateTime.now().plusMinutes(10)
                val reservation = Reservation(
                    userId = 1L,
                    seatId = 1L,
                    status = ReservationStatus.TEMPORARY,
                    expiresAt = futureTime
                )
                
                reservation.isExpired() shouldBe false
            }
            
            Then("만료 시간이 null이면 false를 반환한다") {
                val reservation = Reservation(
                    userId = 1L,
                    seatId = 1L,
                    status = ReservationStatus.CONFIRMED,
                    expiresAt = null
                )
                
                reservation.isExpired() shouldBe false
            }
        }
        
        When("예약 상태를 확인할 때") {
            Then("isTemporary()가 정상 동작한다") {
                val temporaryReservation = Reservation.create(1L, 1L)
                val confirmedReservation = temporaryReservation.confirm()
                val cancelledReservation = temporaryReservation.cancel()
                
                temporaryReservation.isTemporary() shouldBe true
                confirmedReservation.isTemporary() shouldBe false
                cancelledReservation.isTemporary() shouldBe false
            }
            
            Then("isConfirmed()가 정상 동작한다") {
                val temporaryReservation = Reservation.create(1L, 1L)
                val confirmedReservation = temporaryReservation.confirm()
                val cancelledReservation = temporaryReservation.cancel()
                
                temporaryReservation.isConfirmed() shouldBe false
                confirmedReservation.isConfirmed() shouldBe true
                cancelledReservation.isConfirmed() shouldBe false
            }
        }
        
        When("같은 값으로 Reservation을 2개 생성할 때") {
            Then("동등성 비교가 정상 처리된다") {
                val userId = 1L
                val seatId = 1L
                
                val reservation1 = Reservation.create(userId, seatId)
                val reservation2 = Reservation.create(userId, seatId)
                
                // data class이므로 모든 필드가 같으면 동등 (createdAt, expiresAt은 다를 수 있음)
                reservation1.userId shouldBe reservation2.userId
                reservation1.seatId shouldBe reservation2.seatId
                reservation1.status shouldBe reservation2.status
            }
        }
    }
    
    given("ReservationStatus enum") {
        When("enum 값들을 확인할 때") {
            Then("TEMPORARY, CONFIRMED, CANCELLED 상태가 존재한다") {
                ReservationStatus.TEMPORARY.name shouldBe "TEMPORARY"
                ReservationStatus.CONFIRMED.name shouldBe "CONFIRMED"
                ReservationStatus.CANCELLED.name shouldBe "CANCELLED"
            }
        }
    }
})
