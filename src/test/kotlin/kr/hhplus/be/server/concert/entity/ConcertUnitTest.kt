package kr.hhplus.be.server.concert.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.comparables.shouldBeGreaterThan
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

class ConcertUnitTest : BehaviorSpec({
    
    given("Concert 도메인 객체") {
        `when`("모든 필수 정보로 Concert를 생성하면") {
            then("모든 속성이 올바르게 설정된다") {
                val concert = Concert(
                    concertId = 1L,
                    title = "IU 콘서트",
                    artist = "아이유",
                    venue = "올림픽공원 체조경기장",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("150000"),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
                
                concert.concertId shouldBe 1L
                concert.title shouldBe "IU 콘서트"
                concert.artist shouldBe "아이유"
                concert.venue shouldBe "올림픽공원 체조경기장"
                concert.concertDate shouldBe LocalDate.of(2024, 12, 25)
                concert.startTime shouldBe LocalTime.of(19, 0)
                concert.endTime shouldBe LocalTime.of(21, 0)
                concert.basePrice shouldBe BigDecimal("150000")
                concert.createdAt.shouldNotBeNull()
                concert.updatedAt.shouldNotBeNull()
            }
        }
        
        `when`("동일한 정보로 두 개의 Concert를 생성하면") {
            then("동등성 비교가 true이다") {
                val date = LocalDate.of(2024, 12, 25)
                val startTime = LocalTime.of(19, 0)
                val endTime = LocalTime.of(21, 0)
                val basePrice = BigDecimal("150000")
                val createdAt = LocalDateTime.now()
                val updatedAt = LocalDateTime.now()
                
                val concert1 = Concert(
                    concertId = 1L,
                    title = "IU 콘서트",
                    artist = "아이유",
                    venue = "올림픽공원 체조경기장",
                    concertDate = date,
                    startTime = startTime,
                    endTime = endTime,
                    basePrice = basePrice,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                val concert2 = Concert(
                    concertId = 1L,
                    title = "IU 콘서트",
                    artist = "아이유",
                    venue = "올림픽공원 체조경기장",
                    concertDate = date,
                    startTime = startTime,
                    endTime = endTime,
                    basePrice = basePrice,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                concert1 shouldBe concert2
                concert1.hashCode() shouldBe concert2.hashCode()
            }
        }
        
        `when`("다른 concertId를 가진 Concert를 생성하면") {
            then("동등성 비교가 false이다") {
                val date = LocalDate.of(2024, 12, 25)
                val startTime = LocalTime.of(19, 0)
                val endTime = LocalTime.of(21, 0)
                val basePrice = BigDecimal("150000")
                val createdAt = LocalDateTime.now()
                val updatedAt = LocalDateTime.now()
                
                val concert1 = Concert(
                    concertId = 1L,
                    title = "IU 콘서트",
                    artist = "아이유",
                    venue = "올림픽공원 체조경기장",
                    concertDate = date,
                    startTime = startTime,
                    endTime = endTime,
                    basePrice = basePrice,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                val concert2 = Concert(
                    concertId = 2L,
                    title = "IU 콘서트",
                    artist = "아이유",
                    venue = "올림픽공원 체조경기장",
                    concertDate = date,
                    startTime = startTime,
                    endTime = endTime,
                    basePrice = basePrice,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
                
                concert1 shouldNotBe concert2
            }
        }
        
        `when`("copy를 사용하여 일부 속성을 변경하면") {
            then("변경된 속성의 새로운 인스턴스가 생성된다") {
                val original = Concert(
                    concertId = 1L,
                    title = "원본 콘서트",
                    artist = "원본 아티스트",
                    venue = "원본 장소",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("150000")
                )
                
                val changedTitle = original.copy(title = "변경된 콘서트")
                val changedPrice = original.copy(basePrice = BigDecimal("200000"))
                
                changedTitle.title shouldBe "변경된 콘서트"
                changedTitle.artist shouldBe "원본 아티스트"
                
                changedPrice.basePrice shouldBe BigDecimal("200000")
                changedPrice.title shouldBe "원본 콘서트"
            }
        }
        
        `when`("다양한 가격으로 Concert를 생성하면") {
            then("모든 가격이 정상적으로 처리된다") {
                val lowPrice = Concert(
                    concertId = 1L,
                    title = "저가 콘서트",
                    artist = "신인가수",
                    venue = "소극장",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("50000")
                )
                
                val highPrice = Concert(
                    concertId = 2L,
                    title = "프리미엄 콘서트",
                    artist = "월드스타",
                    venue = "대형 아레나",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("500000")
                )
                
                lowPrice.basePrice shouldBe BigDecimal("50000")
                highPrice.basePrice shouldBe BigDecimal("500000")
                highPrice.basePrice shouldBeGreaterThan lowPrice.basePrice
            }
        }
        
        `when`("다양한 시간으로 Concert를 생성하면") {
            then("시작 시간과 종료 시간이 올바르게 설정된다") {
                val matinee = Concert(
                    concertId = 1L,
                    title = "오후 콘서트",
                    artist = "아티스트",
                    venue = "콘서트홀",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(16, 0),
                    basePrice = BigDecimal("150000")
                )
                
                val evening = Concert(
                    concertId = 2L,
                    title = "저녁 콘서트",
                    artist = "아티스트",
                    venue = "콘서트홀",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(19, 30),
                    endTime = LocalTime.of(22, 0),
                    basePrice = BigDecimal("150000")
                )
                
                matinee.startTime shouldBe LocalTime.of(14, 0)
                matinee.endTime shouldBe LocalTime.of(16, 0)
                
                evening.startTime shouldBe LocalTime.of(19, 30)
                evening.endTime shouldBe LocalTime.of(22, 0)
            }
        }
        
        `when`("다양한 날짜로 Concert를 생성하면") {
            then("모든 날짜가 정상적으로 처리된다") {
                val today = Concert(
                    concertId = 1L,
                    title = "오늘 콘서트",
                    artist = "아티스트",
                    venue = "콘서트홀",
                    concertDate = LocalDate.now(),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("150000")
                )
                
                val future = Concert(
                    concertId = 2L,
                    title = "미래 콘서트",
                    artist = "아티스트",
                    venue = "콘서트홀",
                    concertDate = LocalDate.now().plusMonths(6),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("150000")
                )
                
                today.concertDate shouldBe LocalDate.now()
                future.concertDate shouldBe LocalDate.now().plusMonths(6)
            }
        }
        
        `when`("기본값으로 Concert를 생성하면") {
            then("createdAt과 updatedAt이 현재 시간으로 설정된다") {
                val beforeCreation = LocalDateTime.now()
                
                val concert = Concert(
                    title = "테스트 콘서트",
                    artist = "테스트 아티스트",
                    venue = "테스트 장소",
                    concertDate = LocalDate.of(2024, 12, 25),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 0),
                    basePrice = BigDecimal("150000")
                )
                
                val afterCreation = LocalDateTime.now()
                
                concert.concertId shouldBe 0L // 기본값
                concert.createdAt.shouldNotBeNull()
                concert.updatedAt.shouldNotBeNull()
                
                // 생성 시간이 테스트 시간 범위 내에 있는지 확인
                concert.createdAt shouldBeGreaterThan beforeCreation.minusSeconds(1)
                concert.updatedAt shouldBeGreaterThan beforeCreation.minusSeconds(1)
            }
        }
    }
})
