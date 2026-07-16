package ru.example.productverification.mvc.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.example.productverification.domain.model.Decision
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "product_check")
open class ProductCheckEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UUID(0, 0),

    @Column(name = "client_id", nullable = false, length = 128)
    open var clientId: String = "",

    @Column(name = "idempotency_key", nullable = false, length = 128)
    open var idempotencyKey: String = "",

    @Column(name = "request_hash", nullable = false, length = 64)
    open var requestHash: String = "",

    @Column(name = "code_hash", nullable = false, length = 64)
    open var codeHash: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    open var decision: Decision = Decision.UNKNOWN,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", nullable = false, columnDefinition = "jsonb")
    open var reasonCodes: MutableList<String> = mutableListOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unavailable_sources", nullable = false, columnDefinition = "jsonb")
    open var unavailableSources: MutableList<String> = mutableListOf(),

    @Column(name = "degraded", nullable = false)
    open var degraded: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
    open var resultSnapshot: StoredCheckSnapshot = StoredCheckSnapshot(),

    @Column(name = "duration_ms", nullable = false)
    open var durationMs: Long = 0,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.EPOCH,
)

data class StoredCheckSnapshot(
    val checkedAt: String = Instant.EPOCH.toString(),
    val product: StoredProductSnapshot? = null,
)

data class StoredProductSnapshot(
    val gtin: String = "",
    val serialNumber: String = "",
    val name: String = "",
    val manufacturerInn: String = "",
    val manufacturerName: String = "",
    val circulationStatus: String = "",
    val productionDate: String? = null,
    val expirationDate: String? = null,
)
