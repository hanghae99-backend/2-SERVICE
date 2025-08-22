package kr.hhplus.be.server.global.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
class DatabaseConfig {

    @Bean
    @Primary
    @Profile("!test")
    fun optimizedDataSource(databaseProperties: DatabaseProperties): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = databaseProperties.url
            username = databaseProperties.username
            password = databaseProperties.password
            driverClassName = databaseProperties.driverClassName
            
            maximumPoolSize = databaseProperties.hikari.maximumPoolSize
            minimumIdle = databaseProperties.hikari.minimumIdle
            connectionTimeout = databaseProperties.hikari.connectionTimeout
            idleTimeout = databaseProperties.hikari.idleTimeout
            maxLifetime = databaseProperties.hikari.maxLifetime
            
            isAutoCommit = false
            connectionTestQuery = "SELECT 1"
            validationTimeout = 3000L
            leakDetectionThreshold = 30000L
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("useLocalSessionState", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
            addDataSourceProperty("cacheResultSetMetadata", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")
            
            addDataSourceProperty("useSSL", "false")
            addDataSourceProperty("allowPublicKeyRetrieval", "true")
            addDataSourceProperty("serverTimezone", "UTC")
            addDataSourceProperty("useLegacyDatetimeCode", "false")
        }
        
        return HikariDataSource(config)
    }
}

@ConfigurationProperties(prefix = "spring.datasource")
data class DatabaseProperties @ConstructorBinding constructor(
    val url: String,
    val username: String,
    val password: String,
    val driverClassName: String,
    val hikari: HikariProperties = HikariProperties()
)

data class HikariProperties(
    val maximumPoolSize: Int = 10,
    val minimumIdle: Int = 5,
    val connectionTimeout: Long = 30000L,
    val idleTimeout: Long = 600000L,
    val maxLifetime: Long = 1800000L
)