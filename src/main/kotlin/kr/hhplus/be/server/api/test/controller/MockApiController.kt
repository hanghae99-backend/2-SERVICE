package kr.hhplus.be.server.api.test.controller

import kr.hhplus.be.server.api.test.dto.MockApiResponse
import kr.hhplus.be.server.domain.concert.service.ConcertReservationData
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/test/mock")
@ConditionalOnProperty(name = ["mock-api.enabled"], havingValue = "true", matchIfMissing = true)
class MockApiController {
    
    private val logger = LoggerFactory.getLogger(MockApiController::class.java)
    private val receivedData = mutableListOf<ConcertReservationData>()
    
    @PostMapping("/data-platform/reservations")
    fun receiveReservationData(@RequestBody data: ConcertReservationData): ResponseEntity<String> {
        logger.info("📥 Mock API 데이터 수신 - reservationId: ${data.reservationId}, concertId: ${data.concertId}")
        
        receivedData.add(data)
        
        return ResponseEntity.ok("수신 완료")
    }
    
    @GetMapping("/data-platform/reservations")
    fun getReceivedReservations(): ResponseEntity<List<ConcertReservationData>> {
        return ResponseEntity.ok(receivedData.sortedByDescending { it.reservationId })
    }
}