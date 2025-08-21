package kr.hhplus.be.server.domain.concert.aop

import kr.hhplus.be.server.domain.concert.service.ConcertStatsService
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class ViewCountAspect(
    private val concertStatsService: ConcertStatsService
) {
    
    @AfterReturning("@annotation(incrementViewCount)")
    fun incrementViewCount(joinPoint: JoinPoint, incrementViewCount: IncrementViewCount) {
        val parameterNames = joinPoint.signature.toString()
        val args = joinPoint.args
        
        // 파라미터에서 concertId 찾기
        val concertId = findConcertIdFromArgs(joinPoint, incrementViewCount.concertIdParam)
        
        if (concertId != null) {
            concertStatsService.incrementViewCount(concertId)
        }
    }
    
    private fun findConcertIdFromArgs(joinPoint: JoinPoint, paramName: String): Long? {
        // 리플렉션으로 파라미터 이름 찾기는 복잡하므로
        // 일단 첫 번째 Long 타입 파라미터를 concertId로 가정
        return joinPoint.args.firstOrNull { it is Long } as? Long
    }
}
