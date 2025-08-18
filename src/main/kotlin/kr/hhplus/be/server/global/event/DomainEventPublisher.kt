package kr.hhplus.be.server.global.event

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Component
class DomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(DomainEventPublisher::class.java)
        private val asyncExecutor: Executor = createOptimizedExecutor()
        
        private fun createOptimizedExecutor(): Executor {
            val executor = ThreadPoolExecutor(
                2, // 코어 스레드 수
                8, // 최대 스레드 수
                60L, TimeUnit.SECONDS, // 유휴 스레드 생존 시간
                LinkedBlockingQueue(500), // 큐 크기
                { runnable -> 
                    Thread(runnable, "domain-event-${Thread.currentThread().id}").apply {
                        isDaemon = true
                        priority = Thread.NORM_PRIORITY
                    }
                },
                ThreadPoolExecutor.CallerRunsPolicy() // 큐가 가득 찬 경우 호출자 스레드에서 실행
            )
            
            // JVM 종료 시 graceful shutdown
            Runtime.getRuntime().addShutdownHook(Thread {
                executor.shutdown()
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    executor.shutdownNow()
                }
            })
            
            return executor
        }
    }
    
    // 성능 모니터링 메트릭
    private val publishedEvents = AtomicLong(0)
    private val failedEvents = AtomicLong(0)
    private val totalProcessingTime = AtomicLong(0)
    private val asyncEvents = AtomicLong(0)
    private val syncEvents = AtomicLong(0)
    
    fun publish(event: DomainEvent) {
        val startTime = System.currentTimeMillis()
        
        try {
            val eventInfo = "${event.javaClass.simpleName}[${event.eventId}]"
            val transactionInfo = getTransactionInfo()
            
            logger.debug("🚀 이벤트 발행: {} ({})", eventInfo, transactionInfo)
            
            applicationEventPublisher.publishEvent(event)
            
            publishedEvents.incrementAndGet()
            syncEvents.incrementAndGet()
            
        } catch (e: Exception) {
            failedEvents.incrementAndGet()
            logger.error("❌ 이벤트 발행 실패: {}", event.javaClass.simpleName, e)
            throw EventPublishException("이벤트 발행 실패: ${event.javaClass.simpleName}", e)
        } finally {
            val elapsed = System.currentTimeMillis() - startTime
            totalProcessingTime.addAndGet(elapsed)
            
            if (elapsed > 100) { // 100ms 이상 걸린 경우 경고
                logger.warn("🐌 이벤트 발행 시간 초과: {}ms, event: {}", elapsed, event.javaClass.simpleName)
            }
        }
    }
    
    fun publishAll(events: List<DomainEvent>) {
        if (events.isEmpty()) {
            logger.debug("발행할 이벤트가 없습니다")
            return
        }
        
        val startTime = System.currentTimeMillis()
        val transactionInfo = getTransactionInfo()
        
        logger.debug("📦 {}개의 이벤트 일괄 발행 시작 ({})", events.size, transactionInfo)
        
        var successCount = 0
        var failureCount = 0
        
        try {
            events.forEach { event ->
                try {
                    applicationEventPublisher.publishEvent(event)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    logger.error("이벤트 발행 실패: {}", event.javaClass.simpleName, e)
                }
            }
            
            publishedEvents.addAndGet(successCount.toLong())
            failedEvents.addAndGet(failureCount.toLong())
            syncEvents.addAndGet(successCount.toLong())
            
            logger.debug("✅ 이벤트 일괄 발행 완료: 성공 {}, 실패 {}", successCount, failureCount)
            
        } finally {
            val elapsed = System.currentTimeMillis() - startTime
            totalProcessingTime.addAndGet(elapsed)
            
            if (elapsed > 500) { // 500ms 이상 걸린 경우 경고
                logger.warn("🐌 일괄 이벤트 발행 시간 초과: {}ms, count: {}", elapsed, events.size)
            }
        }
        
        if (failureCount > 0) {
            throw EventPublishException("일괄 이벤트 발행 중 $failureCount 개 실패")
        }
    }
    
    fun publishAsync(event: DomainEvent) {
        val eventInfo = "${event.javaClass.simpleName}[${event.eventId}]"
        logger.debug("🚀 비동기 이벤트 발행: {}", eventInfo)
        
        val future = CompletableFuture.runAsync({
            val startTime = System.currentTimeMillis()
            try {
                applicationEventPublisher.publishEvent(event)
                asyncEvents.incrementAndGet()
                publishedEvents.incrementAndGet()
                
                val elapsed = System.currentTimeMillis() - startTime
                totalProcessingTime.addAndGet(elapsed)
                
                logger.debug("✅ 비동기 이벤트 발행 완료: {} ({}ms)", eventInfo, elapsed)
            } catch (e: Exception) {
                failedEvents.incrementAndGet()
                logger.error("❌ 비동기 이벤트 발행 실패: {}", eventInfo, e)
            }
        }, asyncExecutor)
        
        // 타임아웃 설정으로 무한 대기 방지
        future.orTimeout(30, TimeUnit.SECONDS)
            .exceptionally { throwable ->
                logger.error("비동기 이벤트 발행 타임아웃 또는 예외: {}", eventInfo, throwable)
                failedEvents.incrementAndGet()
                null
            }
    }
    
    fun publishAllAsync(events: List<DomainEvent>) {
        if (events.isEmpty()) return
        
        logger.debug("📦 {}개의 이벤트 비동기 일괄 발행 시작", events.size)
        
        val futures = events.map { event ->
            CompletableFuture.runAsync({
                try {
                    applicationEventPublisher.publishEvent(event)
                    asyncEvents.incrementAndGet()
                    publishedEvents.incrementAndGet()
                } catch (e: Exception) {
                    failedEvents.incrementAndGet()
                    logger.error("비동기 이벤트 발행 실패: {}", event.javaClass.simpleName, e)
                }
            }, asyncExecutor)
        }
        
        // 모든 이벤트 발행 완료를 기다리지 않고 fire-and-forget
        CompletableFuture.allOf(*futures.toTypedArray())
            .orTimeout(60, TimeUnit.SECONDS)
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    logger.error("비동기 일괄 이벤트 발행 중 일부 실패", throwable)
                } else {
                    logger.debug("✅ 비동기 일괄 이벤트 발행 완료: {}개", events.size)
                }
            }
    }
    
    fun publishAfterCommit(event: DomainEvent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            val eventInfo = "${event.javaClass.simpleName}[${event.eventId}]"
            logger.debug("📅 커밋 후 이벤트 발행 예약: {}", eventInfo)
            
            TransactionSynchronizationManager.registerSynchronization(
                object : org.springframework.transaction.support.TransactionSynchronizationAdapter() {
                    override fun afterCommit() {
                        try {
                            logger.debug("✅ 커밋 후 이벤트 발행: {}", eventInfo)
                            applicationEventPublisher.publishEvent(event)
                            publishedEvents.incrementAndGet()
                            syncEvents.incrementAndGet()
                        } catch (e: Exception) {
                            failedEvents.incrementAndGet()
                            logger.error("❌ 커밋 후 이벤트 발행 실패: {}", eventInfo, e)
                        }
                    }
                    
                    override fun afterCompletion(status: Int) {
                        if (status == STATUS_ROLLED_BACK) {
                            logger.debug("🔄 트랜잭션 롤백으로 인한 이벤트 발행 취소: {}", eventInfo)
                        }
                    }
                }
            )
        } else {
            logger.debug("🚀 트랜잭션 외부에서 이벤트 즉시 발행: {}", event.javaClass.simpleName)
            publish(event)
        }
    }
    
    // 배치 처리용 - 여러 이벤트를 커밋 후 일괄 발행
    fun publishAllAfterCommit(events: List<DomainEvent>) {
        if (events.isEmpty()) return
        
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            logger.debug("📅 커밋 후 일괄 이벤트 발행 예약: {}개", events.size)
            
            TransactionSynchronizationManager.registerSynchronization(
                object : org.springframework.transaction.support.TransactionSynchronizationAdapter() {
                    override fun afterCommit() {
                        publishAll(events)
                    }
                    
                    override fun afterCompletion(status: Int) {
                        if (status == STATUS_ROLLED_BACK) {
                            logger.debug("🔄 트랜잭션 롤백으로 인한 일괄 이벤트 발행 취소: {}개", events.size)
                        }
                    }
                }
            )
        } else {
            publishAll(events)
        }
    }
    
    // 성능 모니터링 메서드
    fun getEventStatistics(): EventStatistics {
        val total = publishedEvents.get() + failedEvents.get()
        return EventStatistics(
            publishedEvents = publishedEvents.get(),
            failedEvents = failedEvents.get(),
            totalEvents = total,
            successRate = if (total > 0) (publishedEvents.get().toDouble() / total * 100) else 0.0,
            averageProcessingTimeMs = if (publishedEvents.get() > 0) {
                totalProcessingTime.get() / publishedEvents.get()
            } else 0L,
            asyncEvents = asyncEvents.get(),
            syncEvents = syncEvents.get()
        )
    }
    
    fun resetStatistics() {
        publishedEvents.set(0)
        failedEvents.set(0)
        totalProcessingTime.set(0)
        asyncEvents.set(0)
        syncEvents.set(0)
    }
    
    private fun getTransactionInfo(): String {
        return if (TransactionSynchronizationManager.isActualTransactionActive()) {
            val transactionName = TransactionSynchronizationManager.getCurrentTransactionName()
            val readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            "tx[name=$transactionName, readOnly=$readOnly]"
        } else {
            "no-tx"
        }
    }
}

data class EventStatistics(
    val publishedEvents: Long,
    val failedEvents: Long,
    val totalEvents: Long,
    val successRate: Double,
    val averageProcessingTimeMs: Long,
    val asyncEvents: Long,
    val syncEvents: Long
)

class EventPublishException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
