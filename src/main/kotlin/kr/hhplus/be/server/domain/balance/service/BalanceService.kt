package kr.hhplus.be.server.domain.balance.service

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.repositories.PointHistoryRepository
import kr.hhplus.be.server.domain.balance.repositories.PointRepository
import kr.hhplus.be.server.domain.user.aop.ValidateUserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class BalanceService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository
) {
    @ValidateUserId
    fun getBalance(userId: Long): Point {
        return pointRepository.findByUserId(userId)
            ?: Point.create(userId, BigDecimal.ZERO)
    }

    @ValidateUserId
    fun getPointHistory(userId: Long): List<PointHistory> {
        return pointHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
}