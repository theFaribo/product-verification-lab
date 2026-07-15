package ru.example.productverification.stubs.registry

import java.time.Instant
import java.time.LocalDate

enum class RegistryScenario {
    SUCCESS,
    NOT_FOUND,
    SERVER_ERROR,
    SLOW,
    WITHDRAWN,
    SOLD,
    EXPIRED,
}

enum class RegistryCirculationStatus {
    IN_CIRCULATION,
    SOLD,
    WITHDRAWN,
}

data class RegistryItemResponse(
    val codeHash: String,
    val gtin: String,
    val serialNumber: String,
    val name: String,
    val manufacturer: RegistryManufacturerResponse,
    val circulationStatus: RegistryCirculationStatus,
    val productionDate: LocalDate,
    val expirationDate: LocalDate,
    val registryVersion: Long,
    val updatedAt: Instant,
)

data class RegistryManufacturerResponse(
    val inn: String,
    val name: String,
)

data class RegistryStubExecution(
    val response: RegistryItemResponse,
    val effectiveDelayMs: Long,
)
