package ru.example.productverification.stubs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class StubConfiguration {

    @Bean
    fun utcClock(): Clock = Clock.systemUTC()
}
