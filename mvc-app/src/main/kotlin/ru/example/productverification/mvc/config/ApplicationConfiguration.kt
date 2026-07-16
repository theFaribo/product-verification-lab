package ru.example.productverification.mvc.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.example.productverification.domain.decision.ProductCheckDecisionEngine
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun productCheckDecisionEngine(): ProductCheckDecisionEngine =
        ProductCheckDecisionEngine()
}
