package kr.hhplus.be.server.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["kr.hhplus.be.server"])
@EnableTransactionManagement
class JpaConfig