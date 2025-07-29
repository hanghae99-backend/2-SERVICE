package kr.hhplus.be.server.api.balance.usecase

import kr.hhplus.be.server.domain.balance.models.Point
import kr.hhplus.be.server.domain.balance.models.PointHistory
import kr.hhplus.be.server.domain.balance.service.BalanceService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.domain.user.exception.UserNotFoundException
import kr.hhplus.be.server.global.lock.DistributedLock
import kr.hhplus.be.server.global.lock.LockKeyManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal


@Service
@Transactional(readOnly = true)
class BalanceUseCase(
    private val balanceService: BalanceService,
    private val userService: UserService,
    private val distributedLock: DistributedLock
) {

    @Transactional
    fun chargeBalance(userId: Long, amount: BigDecimal): Point {
        val lockKey = LockKeyManager.userBalance(userId)

        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {

            if (!userService.existsById(userId)) {
                throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
            }
            

            balanceService.chargeBalance(userId, amount)
        }
    }

    fun getBalance(userId: Long): Point {

        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return balanceService.getBalance(userId)
    }

    @Transactional
    fun deductBalance(userId: Long, amount: BigDecimal): Point {
        val lockKey = LockKeyManager.userBalance(userId)

        return distributedLock.executeWithLock(
            lockKey = lockKey,
            lockTimeoutMs = 10000L,
            waitTimeoutMs = 5000L
        ) {

            if (!userService.existsById(userId)) {
                throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
            }
            

            balanceService.deductBalance(userId, amount)
        }
    }


    @Transactional
    fun deductBalanceInternal(userId: Long, amount: BigDecimal): Point {
        return balanceService.deductBalance(userId, amount)
    }

    fun getPointHistory(userId: Long): List<PointHistory> {

        if (!userService.existsById(userId)) {
            throw UserNotFoundException("존재하지 않는 사용자입니다: $userId")
        }
        
        return balanceService.getPointHistory(userId)
    }
}
