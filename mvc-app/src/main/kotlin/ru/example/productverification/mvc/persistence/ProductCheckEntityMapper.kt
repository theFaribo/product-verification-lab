package ru.example.productverification.mvc.persistence

import org.springframework.stereotype.Component
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductCheckDecision
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.SourceSystem
import ru.example.productverification.mvc.application.ProductCheckRecord
import ru.example.productverification.mvc.persistence.entity.ProductCheckEntity
import ru.example.productverification.mvc.persistence.entity.StoredCheckSnapshot
import ru.example.productverification.mvc.persistence.entity.StoredProductSnapshot
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Component
class ProductCheckEntityMapper {

    fun toEntity(
        id: UUID,
        clientId: String,
        idempotencyKey: String,
        requestHash: String,
        codeHash: String,
        decision: ProductCheckDecision,
        checkedAt: Instant,
        durationMs: Long,
        createdAt: Instant,
    ): ProductCheckEntity =
        ProductCheckEntity(
            id = id,
            clientId = clientId,
            idempotencyKey = idempotencyKey,
            requestHash = requestHash,
            codeHash = codeHash,
            decision = decision.decision,
            reasonCodes = decision.reasonCodes.map { it.name }.toMutableList(),
            unavailableSources = decision.unavailableSources
                .map { it.name }
                .toMutableList(),
            degraded = decision.degraded,
            resultSnapshot = StoredCheckSnapshot(
                checkedAt = checkedAt.toString(),
                product = decision.product?.toStoredSnapshot(),
            ),
            durationMs = durationMs,
            createdAt = createdAt,
        )

    fun toRecord(entity: ProductCheckEntity): ProductCheckRecord =
        ProductCheckRecord(
            id = entity.id,
            decision = entity.decision,
            reasonCodes = entity.reasonCodes.map { ReasonCode.valueOf(it) },
            degraded = entity.degraded,
            unavailableSources = entity.unavailableSources.map { SourceSystem.valueOf(it) },
            product = entity.resultSnapshot.product?.toDomain(),
            checkedAt = Instant.parse(entity.resultSnapshot.checkedAt),
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
