package ru.example.productverification.reactive.persistence

import org.springframework.stereotype.Component
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductCheckDecision
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.reactive.application.ProductCheckRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Component
class ProductCheckRowMapper {

    fun toRow(
        id: UUID,
        clientId: String,
        idempotencyKey: String,
        requestHash: String,
        codeHash: String,
        decision: ProductCheckDecision,
        checkedAt: Instant,
        durationMs: Long,
        createdAt: Instant,
    ): ProductCheckRow =
        ProductCheckRow(
            id = id,
            clientId = clientId,
            idempotencyKey = idempotencyKey,
            requestHash = requestHash,
            codeHash = codeHash,
            decision = decision.decision,
            reasonCodes = decision.reasonCodes,
            unavailableSources = decision.unavailableSources,
            degraded = decision.degraded,
            resultSnapshot = StoredCheckSnapshot(
                checkedAt = checkedAt.toString(),
                product = decision.product?.toStoredSnapshot(),
            ),
            durationMs = durationMs,
            createdAt = createdAt,
        )

    fun toRecord(row: ProductCheckRow): ProductCheckRecord =
        ProductCheckRecord(
            id = row.id,
            decision = row.decision,
            reasonCodes = row.reasonCodes,
            degraded = row.degraded,
            unavailableSources = row.unavailableSources,
            product = row.resultSnapshot.product?.toDomain(),
            checkedAt = Instant.parse(row.resultSnapshot.checkedAt),
        )

    private fun ProductSnapshot.toStoredSnapshot(): StoredProductSnapshot =
        StoredProductSnapshot(
            gtin = identity.gtin,
            serialNumber = identity.serialNumber,
            name = name,
            manufacturerInn = manufacturer.inn,
            manufacturerName = manufacturer.name,
            circulationStatus = circulationStatus.name,
            productionDate = productionDate?.toString(),
            expirationDate = expirationDate?.toString(),
        )

    private fun StoredProductSnapshot.toDomain(): ProductSnapshot =
        ProductSnapshot(
            identity = ProductIdentity(
                gtin = gtin,
                serialNumber = serialNumber,
            ),
            name = name,
            manufacturer = Manufacturer(
                inn = manufacturerInn,
                name = manufacturerName,
            ),
            circulationStatus = CirculationStatus.valueOf(circulationStatus),
            productionDate = productionDate?.let(LocalDate::parse),
            expirationDate = expirationDate?.let(LocalDate::parse),
        )
}
