package ru.example.productverification.reactive.persistence

import ru.example.productverification.domain.model.Decision
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.SourceSystem
import java.time.Instant
import java.util.UUID

data class ProductCheckRow(
    val id: UUID,
    val clientId: String,
    val idempotencyKey: String,
    val requestHash: String,
    val codeHash: String,
    val decision: Decision,
    val reasonCodes: List<ReasonCode>,
    val unavailableSources: List<SourceSystem>,
    val degraded: Boolean,
    val resultSnapshot: StoredCheckSnapshot,
    val durationMs: Long,
    val createdAt: Instant,
)

data class StoredCheckSnapshot(
    val checkedAt: String,
    val product: StoredProductSnapshot?,
)

data class StoredProductSnapshot(
    val gtin: String,
    val serialNumber: String,
    val name: String,
    val manufacturerInn: String,
    val manufacturerName: String,
    val circulationStatus: String,
    val productionDate: String?,
    val expirationDate: String?,
)
