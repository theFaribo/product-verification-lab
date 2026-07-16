package ru.example.productverification.reactive.infrastructure.registry

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("clients.registry")
data class ProductRegistryClientProperties(
    val baseUrl: URI = URI.create("http://localhost:8090"),
    val scenario: String = "SUCCESS",
    val connectTimeout: Duration = Duration.ofMillis(300),
    val readTimeout: Duration = Duration.ofMillis(500),
)
