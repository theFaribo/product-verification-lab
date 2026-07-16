package ru.example.productverification.reactive.infrastructure.registry

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.UnavailabilityReason
import ru.example.productverification.reactive.application.ProductRegistryClient
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeoutException

class HttpProductRegistryClient(
    private val webClient: WebClient,
    private val properties: ProductRegistryClientProperties,
) : ProductRegistryClient {

    override suspend fun findProduct(codeHash: String): RegistryLookup =
        try {
            val response = webClient.get()
                .uri { builder ->
                    builder
                        .path("/stub/v1/registry/items/{codeHash}")
                        .queryParam("scenario", properties.scenario)
                        .build(codeHash)
                }
                .retrieve()
                .awaitBody<RegistryItemResponse>()

            RegistryLookup.Found(response.toDomain())
        } catch (exception: CancellationException) {
            // Cancellation belongs to structured concurrency and must never become a degraded result.
            throw exception
        } catch (exception: WebClientResponseException.NotFound) {
            RegistryLookup.NotFound
        } catch (exception: WebClientResponseException) {
            val reason = when (exception.statusCode.value()) {
                429 -> UnavailabilityReason.RATE_LIMITED
                else -> UnavailabilityReason.SERVER_ERROR
            }
            unavailable(reason, codeHash, exception)
        } catch (exception: WebClientRequestException) {
            val reason = if (exception.hasTimeoutCause()) {
                UnavailabilityReason.TIMEOUT
            } else {
                UnavailabilityReason.CONNECTION_ERROR
            }
            unavailable(reason, codeHash, exception)
        } catch (exception: IllegalArgumentException) {
            unavailable(UnavailabilityReason.SERVER_ERROR, codeHash, exception)
        } catch (exception: DateTimeParseException) {
            unavailable(UnavailabilityReason.SERVER_ERROR, codeHash, exception)
        }

    private fun RegistryItemResponse.toDomain(): ProductSnapshot =
        ProductSnapshot(
            identity = ProductIdentity(
                gtin = gtin,
                serialNumber = serialNumber,
            ),
            name = name,
            manufacturer = Manufacturer(
                inn = manufacturer.inn,
                name = manufacturer.name,
            ),
            circulationStatus = CirculationStatus.valueOf(circulationStatus),
            productionDate = productionDate?.let(LocalDate::parse),
            expirationDate = expirationDate?.let(LocalDate::parse),
        )

    private fun unavailable(
        reason: UnavailabilityReason,
        codeHash: String,
        exception: Exception,
    ): RegistryLookup.Unavailable {
        logger.warn(
            "Product registry call failed: reason={}, codeHashPrefix={}",
            reason,
            codeHash.take(HASH_PREFIX_LENGTH),
            exception,
        )
        return RegistryLookup.Unavailable(reason)
    }

    private fun Throwable.hasTimeoutCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (
                current is SocketTimeoutException ||
                current is TimeoutException ||
                current.javaClass.simpleName.contains("Timeout", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        val logger = LoggerFactory.getLogger(HttpProductRegistryClient::class.java)
        const val HASH_PREFIX_LENGTH = 12
    }
}

private data class RegistryItemResponse(
    val codeHash: String,
    val gtin: String,
    val serialNumber: String,
    val name: String,
    val manufacturer: RegistryManufacturerResponse,
    val circulationStatus: String,
    val productionDate: String?,
    val expirationDate: String?,
    val registryVersion: Long,
    val updatedAt: String,
)

private data class RegistryManufacturerResponse(
    val inn: String,
    val name: String,
)
