package kr.hhplus.be.server.api.balance.usecase

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.exception.PointNotFoundException
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryTypePojoRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class DeductBalanceUseCase(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointHistoryTypeRepository: PointHistoryTypePojoRepository,
) {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @ValidateUserId
    fun execute(userId: Long, amount: BigDecimal): Point {
        // 결제 시 포인트 차감은 비관적 락 사용 (충돌 시 재시도 비용이 크므로)
        val currentPoint = pointRepository.findByUserIdWithPessimisticLock(userId)
            ?: throw PointNotFoundException("포인트 정보를 찾을 수 없습니다")

        val deductedPoint = currentPoint.deduct(amount)
        val savedPoint = pointRepository.save(deductedPoint)
        val useType = pointHistoryTypeRepository.getUseType()
        val history = PointHistory.use(userId, amount, useType, "포인트 사용")
        pointHistoryRepository.save(history)

        return savedPoint
    }
}
