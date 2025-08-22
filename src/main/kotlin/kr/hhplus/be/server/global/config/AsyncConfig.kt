package kr.hhplus.be.server.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {
    
    @Value("\${app.async.core-pool-size:5}")
    private var corePoolSize: Int = 5
    
    @Value("\${app.async.max-pool-size:20}")
    private var maxPoolSize: Int = 20
    
    @Value("\${app.async.queue-capacity:100}")
    private var queueCapacity: Int = 100
    
    @Value("\${app.async.thread-name-prefix:Async-}")
    private lateinit var threadNamePrefix: String
    
    @Bean("taskExecutor")
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = corePoolSize
        executor.maxPoolSize = maxPoolSize
        executor.queueCapacity = queueCapacity
        executor.setThreadNamePrefix(threadNamePrefix)
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.setRejectedExecutionHandler { runnable, _ ->
            // 큐가 가득 찬 경우 동기적으로 실행
            runnable.run()
        }
        executor.initialize()
        return executor
    }
    
    @Bean("domainEventExecutor")
    fun domainEventExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 3
        executor.maxPoolSize = 10
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("DomainEvent-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.setRejectedExecutionHandler { runnable, _ ->
            // 도메인 이벤트는 유실되면 안 되므로 동기적으로 실행
            runnable.run()
        }
        executor.initialize()
        return executor
    }
    
    @Bean("scheduledTaskExecutor")
    fun scheduledTaskExecutor(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 5
        scheduler.setThreadNamePrefix("Scheduled-")
        scheduler.setWaitForTasksToCompleteOnShutdown(true)
        scheduler.setAwaitTerminationSeconds(30)
        scheduler.initialize()
        return scheduler
    }
    
    @Bean("lockExecutor")
    fun lockExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 20
        executor.setThreadNamePrefix("Lock-")
        executor.setWaitForTasksToCompleteOnShutdown(false)
        executor.setAwaitTerminationSeconds(10)
        executor.initialize()
        return executor
    }
    
    @Bean("redisListenerExecutor")
    fun redisListenerExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("Redis-Listener-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(10)
        executor.initialize()
        return executor
    }
}
