package kr.hhplus.be.server.concert.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import java.math.BigDecimal
import java.time.LocalDateTime

class SeatUnitTest : BehaviorSpec({
    
    given("Seat 도메인 객체") {
        `when`("모든 필수 정보로 Seat을 생성하면") {
            then("모든 속성이 올바르게 설정된다") {
                val seat = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
                
                seat.seatId shouldBe 1L
                seat.concertId shouldBe 100L
                seat.seatNumber shouldBe 15
                seat.price shouldBe BigDecimal("180000")
                seat.status shouldBe SeatStatus.AVAILABLE
                seat.createdAt.shouldNotBeNull()
                seat.updatedAt.shouldNotBeNull()
            }
        }
        
        `when`("동일한 정보로 두 개의 Seat을 생성하면") {
            then("동등성 비교가 true이다") {
                val createdAt = LocalDateTime.now()
                val updatedAt = LocalDateTime.now()
                
                val seat1 = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                val seat2 = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                seat1 shouldBe seat2
                seat1.hashCode() shouldBe seat2.hashCode()
            }
        }
        
        `when`("다른 seatId를 가진 Seat을 생성하면") {
            then("동등성 비교가 false이다") {
                val createdAt = LocalDateTime.now()
                val updatedAt = LocalDateTime.now()
                
                val seat1 = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                val seat2 = Seat(
                    seatId = 2L,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                seat1 shouldNotBe seat2
            }
        }
        
        `when`("copy를 사용하여 좌석 상태를 변경하면") {
            then("변경된 상태의 새로운 인스턴스가 생성된다") {
                val original = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val reserved = original.copy(status = SeatStatus.RESERVED)
                val confirmed = original.copy(status = SeatStatus.CONFIRMED)
                
                reserved.status shouldBe SeatStatus.RESERVED
                reserved.seatNumber shouldBe 15
                reserved.price shouldBe BigDecimal("180000")
                
                confirmed.status shouldBe SeatStatus.CONFIRMED
                confirmed.seatNumber shouldBe 15
                confirmed.price shouldBe BigDecimal("180000")
                
                original.status shouldBe SeatStatus.AVAILABLE
            }
        }
        
        `when`("다양한 가격으로 Seat을 생성하면") {
            then("모든 가격이 정상적으로 처리된다") {
                val economySeat = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 50,
                    price = BigDecimal("120000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val premiumSeat = Seat(
                    seatId = 2L,
                    concertId = 100L,
                    seatNumber = 1,
                    price = BigDecimal("300000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val vipSeat = Seat(
                    seatId = 3L,
                    concertId = 100L,
                    seatNumber = 5,
                    price = BigDecimal("500000"),
                    status = SeatStatus.AVAILABLE
                )
                
                economySeat.price shouldBe BigDecimal("120000")
                premiumSeat.price shouldBe BigDecimal("300000")
                vipSeat.price shouldBe BigDecimal("500000")
                
                premiumSeat.price shouldBeGreaterThan economySeat.price
                vipSeat.price shouldBeGreaterThan premiumSeat.price
            }
        }
        
        `when`("다양한 좌석 번호로 Seat을 생성하면") {
            then("모든 좌석 번호가 정상적으로 처리된다") {
                val firstSeat = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 1,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val middleSeat = Seat(
                    seatId = 2L,
                    concertId = 100L,
                    seatNumber = 50,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val lastSeat = Seat(
                    seatId = 3L,
                    concertId = 100L,
                    seatNumber = 100,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                firstSeat.seatNumber shouldBe 1
                middleSeat.seatNumber shouldBe 50
                lastSeat.seatNumber shouldBe 100
            }
        }
        
        `when`("기본값으로 Seat을 생성하면") {
            then("createdAt과 updatedAt이 현재 시간으로 설정된다") {
                val beforeCreation = LocalDateTime.now()
                
                val seat = Seat(
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val afterCreation = LocalDateTime.now()
                
                seat.seatId shouldBe 0L // 기본값
                seat.createdAt.shouldNotBeNull()
                seat.updatedAt.shouldNotBeNull()
                
                // 생성 시간이 테스트 시간 범위 내에 있는지 확인
                seat.createdAt shouldBeGreaterThan beforeCreation.minusSeconds(1)
                seat.updatedAt shouldBeGreaterThan beforeCreation.minusSeconds(1)
            }
        }
        
        `when`("같은 콘서트의 다른 좌석들을 생성하면") {
            then("concertId는 같고 다른 속성들은 다르다") {
                val concertId = 100L
                
                val seat1 = Seat(
                    seatId = 1L,
                    concertId = concertId,
                    seatNumber = 1,
                    price = BigDecimal("150000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val seat2 = Seat(
                    seatId = 2L,
                    concertId = concertId,
                    seatNumber = 2,
                    price = BigDecimal("180000"),
                    status = SeatStatus.RESERVED
                )
                
                seat1.concertId shouldBe seat2.concertId
                seat1.seatId shouldNotBe seat2.seatId
                seat1.seatNumber shouldNotBe seat2.seatNumber
                seat1.price shouldNotBe seat2.price
                seat1.status shouldNotBe seat2.status
            }
        }
    }
    
    given("SeatStatus 열거형") {
        `when`("모든 SeatStatus 값을 확인하면") {
            then("정의된 모든 상태가 존재한다") {
                val allStatuses = SeatStatus.values()
                
                allStatuses.size shouldBe 4
                allStatuses shouldContain SeatStatus.AVAILABLE
                allStatuses shouldContain SeatStatus.RESERVED
                allStatuses shouldContain SeatStatus.CONFIRMED
                allStatuses shouldContain SeatStatus.UNAVAILABLE
            }
        }
        
        `when`("각 SeatStatus의 이름을 확인하면") {
            then("올바른 문자열 이름을 가진다") {
                SeatStatus.AVAILABLE.name shouldBe "AVAILABLE"
                SeatStatus.RESERVED.name shouldBe "RESERVED"
                SeatStatus.CONFIRMED.name shouldBe "CONFIRMED"
                SeatStatus.UNAVAILABLE.name shouldBe "UNAVAILABLE"
            }
        }
        
        `when`("SeatStatus를 비교하면") {
            then("동일한 상태는 같다고 판단된다") {
                val status1 = SeatStatus.AVAILABLE
                val status2 = SeatStatus.AVAILABLE
                
                status1 shouldBe status2
                status1.equals(status2) shouldBe true
            }
        }
        
        `when`("다른 SeatStatus를 비교하면") {
            then("다르다고 판단된다") {
                val available = SeatStatus.AVAILABLE
                val reserved = SeatStatus.RESERVED
                val confirmed = SeatStatus.CONFIRMED
                val unavailable = SeatStatus.UNAVAILABLE
                
                available shouldNotBe reserved
                available shouldNotBe confirmed
                available shouldNotBe unavailable
                reserved shouldNotBe confirmed
                reserved shouldNotBe unavailable
                confirmed shouldNotBe unavailable
            }
        }
        
        `when`("SeatStatus의 예약 관련 상태 흐름을 확인하면") {
            then("논리적인 상태 전환이 가능하다") {
                // AVAILABLE -> RESERVED -> CONFIRMED 흐름
                val initialStatus = SeatStatus.AVAILABLE
                val reservedStatus = SeatStatus.RESERVED
                val finalStatus = SeatStatus.CONFIRMED
                
                initialStatus shouldNotBe reservedStatus
                reservedStatus shouldNotBe finalStatus
                initialStatus shouldNotBe finalStatus
                
                // 각 상태가 고유하다
                listOf(initialStatus, reservedStatus, finalStatus).toSet().size shouldBe 3
            }
        }
    }
    
    given("SeatInfo DTO") {
        `when`("SeatInfo를 생성하면") {
            then("모든 속성이 올바르게 설정된다") {
                val seatInfo = SeatInfo(
                    seatId = 1L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                seatInfo.seatId shouldBe 1L
                seatInfo.seatNumber shouldBe 15
                seatInfo.price shouldBe BigDecimal("180000")
                seatInfo.status shouldBe SeatStatus.AVAILABLE
            }
        }
        
        `when`("Seat 엔티티에서 SeatInfo로 변환하면") {
            then("모든 필요한 정보가 매핑된다") {
                val seat = Seat(
                    seatId = 1L,
                    concertId = 100L,
                    seatNumber = 15,
                    price = BigDecimal("180000"),
                    status = SeatStatus.AVAILABLE
                )
                
                val seatInfo = SeatInfo(
                    seatId = seat.seatId,
                    seatNumber = seat.seatNumber,
                    price = seat.price,
                    status = seat.status
                )
                
                seatInfo.seatId shouldBe seat.seatId
                seatInfo.seatNumber shouldBe seat.seatNumber
                seatInfo.price shouldBe seat.price
                seatInfo.status shouldBe seat.status
            }
        }
        
        `when`("다양한 상태의 SeatInfo를 생성하면") {
            then("각 상태가 올바르게 표현된다") {
                val availableSeat = SeatInfo(1L, 1, BigDecimal("150000"), SeatStatus.AVAILABLE)
                val reservedSeat = SeatInfo(2L, 2, BigDecimal("150000"), SeatStatus.RESERVED)
                val confirmedSeat = SeatInfo(3L, 3, BigDecimal("150000"), SeatStatus.CONFIRMED)
                val unavailableSeat = SeatInfo(4L, 4, BigDecimal("150000"), SeatStatus.UNAVAILABLE)
                
                availableSeat.status shouldBe SeatStatus.AVAILABLE
                reservedSeat.status shouldBe SeatStatus.RESERVED
                confirmedSeat.status shouldBe SeatStatus.CONFIRMED
                unavailableSeat.status shouldBe SeatStatus.UNAVAILABLE
            }
        }
    }
})
