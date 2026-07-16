package ru.example.productverification.reactive.application

import ru.example.productverification.domain.model.Decision
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.SourceSystem
import ru.example.productverification.reactive.api.ScanSource
import java.time.Instant
import java.util.UUID

data class CreateProductCheckCommand(
    val clientId: String,
    val idempotencyKey: String,
    val code: String,
    val scanSource: ScanSource,
    val regionCode: String,
)

data class ProductCheckRecord(
    val id: UUID,
    val decision: Decision,
    val reasonCodes: List<ReasonCode>,
    val degraded: Boolean,
    val unavailableSources: List<SourceSystem>,
    val product: ProductSnapshot?,
    val checkedAt: Instant,
)

data class ProductCheckExecution(
    val record: ProductCheckRecord,
    val created: Boolean,
)

class ProductCheckNotFoundException(
    id: UUID,
) : RuntimeException("Product check $id was not found")

class IdempotencyConflictException : RuntimeException(
    "The supplied idempotency key was already used for a different request",
)
