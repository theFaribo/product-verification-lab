package ru.example.productverification.mvc.infrastructure.registry

import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.UnavailabilityReason
import ru.example.productverification.mvc.application.ProductRegistryClient
import java.net.http.HttpTimeoutException
import java.net.SocketTimeoutException
import java.time.LocalDate

class HttpProductRegistryClient(
    private val restClient: RestClient,
    private val properties: ProductRegistryClientProperties,
) : ProductRegistryClient {

    override fun findProduct(codeHash: String): RegistryLookup =
        try {
            val response = restClient.get()
                .uri { builder ->
                    builder
                        .path("/stub/v1/registry/items/{codeHash}")
                        .queryParam("scenario", properties.scenario)
                        .build(codeHash)
                }
                .retrieve()
                .body(RegistryItemResponse::class.java)
                ?: throw IllegalStateException("Registry returned an empty response body")

            RegistryLookup.Found(response.toDomain())
        } catch (exception: HttpClientErrorException) {
            when (exception.statusCode.value()) {
                404 -> RegistryLookup.NotFound
                429 -> unavailable(UnavailabilityReason.RATE_LIMITED, codeHash, exception)
                else -> unavailable(UnavailabilityReason.SERVER_ERROR, codeHash, exception)
            }
        } catch (exception: HttpServerErrorException) {
            unavailable(UnavailabilityReason.SERVER_ERROR, codeHash, exception)
        } catch (exception: ResourceAccessException) {
            val reason = if (exception.hasTimeoutCause()) {
                UnavailabilityReason.TIMEOUT
            } else {
                UnavailabilityReason.CONNECTION_ERROR
            }
            unavailable(reason, codeHash, exception)
        } catch (exception: RestClientException) {
            unavailable(UnavailabilityReason.CONNECTION_ERROR, codeHash, exception)
        } catch (exception: IllegalArgumentException) {
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
            if (current is SocketTimeoutException || current is HttpTimeoutException) {
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
