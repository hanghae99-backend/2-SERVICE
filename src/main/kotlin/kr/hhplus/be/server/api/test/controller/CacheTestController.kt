package kr.hhplus.be.server.api.test.controller

import kr.hhplus.be.server.api.concert.dto.PopularConcertDto
import kr.hhplus.be.server.api.test.service.CacheTestService
import kr.hhplus.be.server.global.response.CommonApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/test/cache")
class CacheTestController(
    private val cacheTestService: CacheTestService
) {

    @GetMapping("/popular/scheduler")
    fun getPopularConcertsScheduler(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<CommonApiResponse<TestResult>> {
        val startTime = System.currentTimeMillis()
        
        val result = cacheTestService.getPopularConcertsScheduler(limit)
        
        val endTime = System.currentTimeMillis()
        val responseTime = endTime - startTime
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = TestResult(
                    method = "SCHEDULER",
                    responseTime = responseTime,
                    dataSize = result.size,
                    data = result
                ),
                message = "스케줄러 캐시 방식 조회 완료 (${responseTime}ms)"
            )
        )
    }

    @GetMapping("/popular/cacheable")
    fun getPopularConcertsCacheable(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<CommonApiResponse<TestResult>> {
        val startTime = System.currentTimeMillis()
        
        val result = cacheTestService.getPopularConcertsCacheable(limit)
        
        val endTime = System.currentTimeMillis()
        val responseTime = endTime - startTime
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = TestResult(
                    method = "CACHEABLE",
                    responseTime = responseTime,
                    dataSize = result.size,
                    data = result
                ),
                message = "@Cacheable 방식 조회 완료 (${responseTime}ms)"
            )
        )
    }

    @GetMapping("/popular/direct")
    fun getPopularConcertsDirect(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<CommonApiResponse<TestResult>> {
        val startTime = System.currentTimeMillis()
        
        val result = cacheTestService.getPopularConcertsDirect(limit)
        
        val endTime = System.currentTimeMillis()
        val responseTime = endTime - startTime
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = TestResult(
                    method = "DIRECT",
                    responseTime = responseTime,
                    dataSize = result.size,
                    data = result
                ),
                message = "직접 조회 방식 완료 (${responseTime}ms)"
            )
        )
    }

    @GetMapping("/comparison")
    fun compareAllMethods(
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "10") iterations: Int
    ): ResponseEntity<CommonApiResponse<ComparisonResult>> {
        val results = mutableListOf<SingleTestResult>()
        
        repeat(iterations) { i ->
            // 스케줄러 방식
            val schedulerStart = System.currentTimeMillis()
            val schedulerResult = cacheTestService.getPopularConcertsScheduler(limit)
            val schedulerTime = System.currentTimeMillis() - schedulerStart
            
            Thread.sleep(10) // 간격 두기
            
            // @Cacheable 방식
            val cacheableStart = System.currentTimeMillis()
            val cacheableResult = cacheTestService.getPopularConcertsCacheable(limit)
            val cacheableTime = System.currentTimeMillis() - cacheableStart
            
            Thread.sleep(10)
            
            // 직접 조회 방식
            val directStart = System.currentTimeMillis()
            val directResult = cacheTestService.getPopularConcertsDirect(limit)
            val directTime = System.currentTimeMillis() - directStart
            
            results.add(
                SingleTestResult(
                    iteration = i + 1,
                    schedulerTime = schedulerTime,
                    cacheableTime = cacheableTime,
                    directTime = directTime,
                    dataConsistency = checkDataConsistency(schedulerResult, cacheableResult, directResult)
                )
            )
            
            Thread.sleep(50) // 테스트 간격
        }
        
        val comparison = ComparisonResult(
            iterations = iterations,
            limit = limit,
            results = results,
            summary = calculateSummary(results)
        )
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = comparison,
                message = "성능 비교 테스트 완료 (${iterations}회 반복)"
            )
        )
    }

    @PostMapping("/cache/clear")
    fun clearAllCaches(): ResponseEntity<CommonApiResponse<String>> {
        cacheTestService.clearAllCaches()
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = "OK",
                message = "모든 캐시가 삭제되었습니다"
            )
        )
    }

    @PostMapping("/cache/warmup")
    fun warmupCaches(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<CommonApiResponse<String>> {
        val startTime = System.currentTimeMillis()
        
        cacheTestService.warmupCaches(limit)
        
        val endTime = System.currentTimeMillis()
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = "OK",
                message = "캐시 워밍업 완료 (${endTime - startTime}ms)"
            )
        )
    }

    @GetMapping("/cache/status")
    fun getCacheStatus(): ResponseEntity<CommonApiResponse<Map<String, Any>>> {
        val status = cacheTestService.getCacheStatus()
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = status,
                message = "캐시 상태 조회 완료"
            )
        )
    }

    @GetMapping("/quick-test")
    fun quickPerformanceTest(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<CommonApiResponse<QuickTestResult>> {
        
        // 각 방식을 5번씩 실행해서 평균 측정
        val iterations = 5
        
        val schedulerTimes = mutableListOf<Long>()
        val cacheableTimes = mutableListOf<Long>()
        val directTimes = mutableListOf<Long>()
        
        repeat(iterations) {
            // 스케줄러 방식
            val schedulerStart = System.currentTimeMillis()
            cacheTestService.getPopularConcertsScheduler(limit)
            schedulerTimes.add(System.currentTimeMillis() - schedulerStart)
            
            Thread.sleep(20)
            
            // @Cacheable 방식
            val cacheableStart = System.currentTimeMillis()
            cacheTestService.getPopularConcertsCacheable(limit)
            cacheableTimes.add(System.currentTimeMillis() - cacheableStart)
            
            Thread.sleep(20)
            
            // 직접 조회 방식
            val directStart = System.currentTimeMillis()
            cacheTestService.getPopularConcertsDirect(limit)
            directTimes.add(System.currentTimeMillis() - directStart)
            
            Thread.sleep(50)
        }
        
        val result = QuickTestResult(
            limit = limit,
            iterations = iterations,
            schedulerAvg = schedulerTimes.average(),
            cacheableAvg = cacheableTimes.average(),
            directAvg = directTimes.average(),
            schedulerMin = schedulerTimes.minOrNull() ?: 0,
            cacheableMin = cacheableTimes.minOrNull() ?: 0,
            directMin = directTimes.minOrNull() ?: 0,
            schedulerMax = schedulerTimes.maxOrNull() ?: 0,
            cacheableMax = cacheableTimes.maxOrNull() ?: 0,
            directMax = directTimes.maxOrNull() ?: 0,
            winner = determineWinner(schedulerTimes.average(), cacheableTimes.average(), directTimes.average())
        )
        
        return ResponseEntity.ok(
            CommonApiResponse.success(
                data = result,
                message = "빠른 성능 테스트 완료"
            )
        )
    }

    private fun checkDataConsistency(
        schedulerResult: List<PopularConcertDto>,
        cacheableResult: List<PopularConcertDto>,
        directResult: List<PopularConcertDto>
    ): Boolean {
        // 데이터 일관성 확인 (콘서트 ID 순서가 같은지)
        val schedulerIds = schedulerResult.map { it.concertId }
        val cacheableIds = cacheableResult.map { it.concertId }
        val directIds = directResult.map { it.concertId }
        
        return schedulerIds == cacheableIds && cacheableIds == directIds
    }

    private fun calculateSummary(results: List<SingleTestResult>): TestSummary {
        val schedulerTimes = results.map { it.schedulerTime }
        val cacheableTimes = results.map { it.cacheableTime }
        val directTimes = results.map { it.directTime }
        
        return TestSummary(
            schedulerAvg = schedulerTimes.average(),
            cacheableAvg = cacheableTimes.average(),
            directAvg = directTimes.average(),
            schedulerMin = schedulerTimes.minOrNull() ?: 0,
            cacheableMin = cacheableTimes.minOrNull() ?: 0,
            directMin = directTimes.minOrNull() ?: 0,
            schedulerMax = schedulerTimes.maxOrNull() ?: 0,
            cacheableMax = cacheableTimes.maxOrNull() ?: 0,
            directMax = directTimes.maxOrNull() ?: 0,
            dataConsistencyRate = results.count { it.dataConsistency }.toDouble() / results.size * 100
        )
    }

    private fun determineWinner(schedulerAvg: Double, cacheableAvg: Double, directAvg: Double): String {
        return when {
            schedulerAvg <= cacheableAvg && schedulerAvg <= directAvg -> "SCHEDULER"
            cacheableAvg <= schedulerAvg && cacheableAvg <= directAvg -> "CACHEABLE"
            else -> "DIRECT"
        }
    }
}

data class TestResult(
    val method: String,
    val responseTime: Long,
    val dataSize: Int,
    val data: List<PopularConcertDto>
)

data class SingleTestResult(
    val iteration: Int,
    val schedulerTime: Long,
    val cacheableTime: Long,
    val directTime: Long,
    val dataConsistency: Boolean
)

data class ComparisonResult(
    val iterations: Int,
    val limit: Int,
    val results: List<SingleTestResult>,
    val summary: TestSummary
)

data class TestSummary(
    val schedulerAvg: Double,
    val cacheableAvg: Double,
    val directAvg: Double,
    val schedulerMin: Long,
    val cacheableMin: Long,
    val directMin: Long,
    val schedulerMax: Long,
    val cacheableMax: Long,
    val directMax: Long,
    val dataConsistencyRate: Double
)

data class QuickTestResult(
    val limit: Int,
    val iterations: Int,
    val schedulerAvg: Double,
    val cacheableAvg: Double,
    val directAvg: Double,
    val schedulerMin: Long,
    val cacheableMin: Long,
    val directMin: Long,
    val schedulerMax: Long,
    val cacheableMax: Long,
    val directMax: Long,
    val winner: String
)