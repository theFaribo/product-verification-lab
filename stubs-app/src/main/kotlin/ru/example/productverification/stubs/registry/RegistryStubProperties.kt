package ru.example.productverification.stubs.registry

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("stub.registry")
data class RegistryStubProperties(
    val defaultDelayMs: Long = 50,
    val slowDelayMs: Long = 1_000,
    val maxDelayMs: Long = 5_000,
) {
    init {
        require(defaultDelayMs >= 0) {
            "stub.registry.default-delay-ms must be non-negative"
        }
        require(slowDelayMs >= 0) {
            "stub.registry.slow-delay-ms must be non-negative"
        }
        require(maxDelayMs >= 0) {
            "stub.registry.max-delay-ms must be non-negative"
        }
        require(defaultDelayMs <= maxDelayMs) {
            "stub.registry.default-delay-ms must not exceed max-delay-ms"
        }
        require(slowDelayMs <= maxDelayMs) {
            "stub.registry.slow-delay-ms must not exceed max-delay-ms"
        }
    }
}
