package ru.example.productverification.reactive.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import ru.example.productverification.domain.decision.ProductCheckDecisionEngine
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun productCheckDecisionEngine(): ProductCheckDecisionEngine =
        ProductCheckDecisionEngine()

    @Bean
    fun transactionalOperator(
        transactionManager: ReactiveTransactionManager,
    ): TransactionalOperator =
        TransactionalOperator.create(transactionManager)
}
