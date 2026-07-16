package ru.example.productverification.reactive.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Decision
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.SourceSystem
import ru.example.productverification.reactive.application.ProductCheckRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ScanSource {
    MOBILE,
    WEB,
    INTERNAL,
}

data class CreateProductCheckRequest(
    @field:NotBlank
    @field:Size(max = 512)
    val code: String,

    val scanSource: ScanSource,

    @field:Pattern(regexp = "^\\d{2,3}$")
    val regionCode: String,
)

data class ProductCheckResponse(
    val checkId: UUID,
    val decision: Decision,
    val reasonCodes: List<ReasonCode>,
    val product: ProductResponse?,
    val circulationStatus: CirculationStatus?,
    val degraded: Boolean,
    val unavailableSources: List<SourceSystem>,
    val checkedAt: Instant,
)

data class ProductResponse(
    val gtin: String,
    val serialNumber: String,
    val name: String,
    val manufacturer: ManufacturerResponse,
    val productionDate: LocalDate?,
    val expirationDate: LocalDate?,
)

data class ManufacturerResponse(
    val inn: String,
    val name: String,
)

fun ProductCheckRecord.toResponse(): ProductCheckResponse =
    ProductCheckResponse(
        checkId = id,
        decision = decision,
        reasonCodes = reasonCodes,
        product = product?.toResponse(),
        circulationStatus = product?.circulationStatus,
        degraded = degraded,
        unavailableSources = unavailableSources,
        checkedAt = checkedAt,
    )

private fun ProductSnapshot.toResponse(): ProductResponse =
    ProductResponse(
        gtin = identity.gtin,
        serialNumber = identity.serialNumber,
        name = name,
        manufacturer = manufacturer.toResponse(),
        productionDate = productionDate,
        expirationDate = expirationDate,
    )

private fun Manufacturer.toResponse(): ManufacturerResponse =
    ManufacturerResponse(
        inn = inn,
        name = name,
    )
