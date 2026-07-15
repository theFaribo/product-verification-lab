package ru.example.productverification.stubs.registry

import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class RegistryStubService(
    private val properties: RegistryStubProperties,
    private val clock: Clock,
) {

    suspend fun findItem(
        codeHash: String,
        scenario: RegistryScenario,
        requestedDelayMs: Long?,
    ): RegistryStubExecution {
        val normalizedCodeHash = normalizeAndValidateCodeHash(codeHash)
        val effectiveDelayMs = resolveDelayMs(
            scenario = scenario,
            requestedDelayMs = requestedDelayMs,
        )

        if (effectiveDelayMs > 0) {
            delay(effectiveDelayMs)
        }

        when (scenario) {
            RegistryScenario.NOT_FOUND -> {
                throw RegistryItemNotFoundException(normalizedCodeHash)
            }

            RegistryScenario.SERVER_ERROR -> {
                throw SimulatedRegistryFailureException()
            }

            else -> Unit
        }

        return RegistryStubExecution(
            response = createResponse(
                codeHash = normalizedCodeHash,
                scenario = scenario,
            ),
            effectiveDelayMs = effectiveDelayMs,
        )
    }

    private fun normalizeAndValidateCodeHash(codeHash: String): String {
        if (!CODE_HASH_PATTERN.matches(codeHash)) {
            throw InvalidRegistryStubRequestException(
                "codeHash must be a 64-character hexadecimal SHA-256 value",
            )
        }

        return codeHash.lowercase()
    }

    private fun resolveDelayMs(
        scenario: RegistryScenario,
        requestedDelayMs: Long?,
    ): Long {
        val effectiveDelayMs = requestedDelayMs ?: when (scenario) {
            RegistryScenario.SLOW -> properties.slowDelayMs
            else -> properties.defaultDelayMs
        }

        if (effectiveDelayMs !in 0..properties.maxDelayMs) {
            throw InvalidRegistryStubRequestException(
                "delayMs must be between 0 and ${properties.maxDelayMs}",
            )
        }

        return effectiveDelayMs
    }

    private fun createResponse(
        codeHash: String,
        scenario: RegistryScenario,
    ): RegistryItemResponse {
        val today = LocalDate.now(clock)

        val circulationStatus = when (scenario) {
            RegistryScenario.WITHDRAWN -> RegistryCirculationStatus.WITHDRAWN
            RegistryScenario.SOLD -> RegistryCirculationStatus.SOLD
            else -> RegistryCirculationStatus.IN_CIRCULATION
        }

        val expirationDate = when (scenario) {
            RegistryScenario.EXPIRED -> today.minusDays(1)
            else -> today.plusYears(1)
        }

        return RegistryItemResponse(
            codeHash = codeHash,
            gtin = DEFAULT_GTIN,
            serialNumber = codeHash.take(SERIAL_NUMBER_LENGTH).uppercase(),
            name = "Тестовый товар",
            manufacturer = RegistryManufacturerResponse(
                inn = "7701234567",
                name = "ООО Производитель",
            ),
            circulationStatus = circulationStatus,
            productionDate = today.minusDays(30),
            expirationDate = expirationDate,
            registryVersion = DEFAULT_REGISTRY_VERSION,
            updatedAt = clock.instant(),
        )
    }

    private companion object {
        val CODE_HASH_PATTERN = Regex("^[a-fA-F0-9]{64}$")
        const val DEFAULT_GTIN = "04601234567890"
        const val SERIAL_NUMBER_LENGTH = 20
        const val DEFAULT_REGISTRY_VERSION = 1L
    }
}
