package ru.example.productverification.reactive.persistence

import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.stereotype.Repository
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Decision
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.SourceSystem
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class ProductCheckR2dbcRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) {

    suspend fun findById(id: UUID): ProductCheckRow? =
        databaseClient.sql(FIND_CHECK_BY_ID_SQL)
            .bind("id", id)
            .map(::mapCheckRow)
            .awaitOneOrNull()

    suspend fun findByIdempotencyKey(
        clientId: String,
        idempotencyKey: String,
    ): ProductCheckRow? =
        databaseClient.sql(FIND_CHECK_BY_IDEMPOTENCY_KEY_SQL)
            .bind("clientId", clientId)
            .bind("idempotencyKey", idempotencyKey)
            .map(::mapCheckRow)
            .awaitOneOrNull()

    suspend fun findLocalProduct(codeHash: String): ProductSnapshot? =
        databaseClient.sql(FIND_LOCAL_PRODUCT_SQL)
            .bind("codeHash", codeHash)
            .map { row, _ -> mapProduct(row) }
            .awaitOneOrNull()

    suspend fun insert(row: ProductCheckRow): ProductCheckRow {
        val updatedRows = databaseClient.sql(INSERT_CHECK_SQL)
            .bind("id", row.id)
            .bind("clientId", row.clientId)
            .bind("idempotencyKey", row.idempotencyKey)
            .bind("requestHash", row.requestHash)
            .bind("codeHash", row.codeHash)
            .bind("decision", row.decision.name)
            .bind("reasonCodes", objectMapper.writeValueAsString(row.reasonCodes.map { it.name }))
            .bind(
                "unavailableSources",
                objectMapper.writeValueAsString(row.unavailableSources.map { it.name }),
            )
            .bind("degraded", row.degraded)
            .bind("resultSnapshot", objectMapper.writeValueAsString(row.resultSnapshot))
            .bind("durationMs", row.durationMs)
            .bind("createdAt", OffsetDateTime.ofInstant(row.createdAt, ZoneOffset.UTC))
            .fetch()
            .awaitRowsUpdated()

        check(updatedRows == 1L) {
            "Expected one inserted product_check row, got $updatedRows"
        }
        return row
    }

    private fun mapCheckRow(
        row: Row,
        metadata: io.r2dbc.spi.RowMetadata,
    ): ProductCheckRow =
        ProductCheckRow(
            id = row.required("id", UUID::class.java),
            clientId = row.required("client_id", String::class.java),
            idempotencyKey = row.required("idempotency_key", String::class.java),
            requestHash = row.required("request_hash", String::class.java),
            codeHash = row.required("code_hash", String::class.java),
            decision = Decision.valueOf(row.required("decision", String::class.java)),
            reasonCodes = readEnumArray(
                json = row.required("reason_codes_json", String::class.java),
                enumValueOf = ReasonCode::valueOf,
            ),
            unavailableSources = readEnumArray(
                json = row.required("unavailable_sources_json", String::class.java),
                enumValueOf = SourceSystem::valueOf,
            ),
            degraded = row.required("degraded", Boolean::class.java),
            resultSnapshot = objectMapper.readValue(
                row.required("result_snapshot_json", String::class.java),
                StoredCheckSnapshot::class.java,
            ),
            durationMs = row.required("duration_ms", Long::class.java),
            createdAt = row.required("created_at", OffsetDateTime::class.java).toInstant(),
        )

    private fun mapProduct(row: Row): ProductSnapshot =
        ProductSnapshot(
            identity = ProductIdentity(
                gtin = row.required("gtin", String::class.java),
                serialNumber = row.required("serial_number", String::class.java),
            ),
            name = row.required("name", String::class.java),
            manufacturer = Manufacturer(
                inn = row.required("manufacturer_inn", String::class.java),
                name = row.required("manufacturer_name", String::class.java),
            ),
            circulationStatus = CirculationStatus.valueOf(
                row.required("circulation_status", String::class.java),
            ),
            productionDate = row.get("production_date", LocalDate::class.java),
            expirationDate = row.get("expiration_date", LocalDate::class.java),
        )

    private fun <T> readEnumArray(
        json: String,
        enumValueOf: (String) -> T,
    ): List<T> =
        objectMapper.readValue(json, Array<String>::class.java)
            .map(enumValueOf)

    private fun <T : Any> Row.required(
        column: String,
        type: Class<T>,
    ): T =
        requireNotNull(get(column, type)) {
            "Database column '$column' must not be null"
        }

    private companion object {
        val CHECK_COLUMNS = """
            id,
            client_id,
            idempotency_key,
            request_hash,
            code_hash,
            decision,
            cast(reason_codes as text) as reason_codes_json,
            cast(unavailable_sources as text) as unavailable_sources_json,
            degraded,
            cast(result_snapshot as text) as result_snapshot_json,
            duration_ms,
            created_at
        """.trimIndent()

        val FIND_CHECK_BY_ID_SQL = """
            select $CHECK_COLUMNS
            from product_check
            where id = :id
        """.trimIndent()

        val FIND_CHECK_BY_IDEMPOTENCY_KEY_SQL = """
            select $CHECK_COLUMNS
            from product_check
            where client_id = :clientId
              and idempotency_key = :idempotencyKey
        """.trimIndent()

        val FIND_LOCAL_PRODUCT_SQL = """
            select
                pi.code_hash,
                pi.serial_number,
                pi.circulation_status,
                pi.production_date,
                pi.expiration_date,
                pc.gtin,
                pc.name,
                pc.manufacturer_inn,
                pc.manufacturer_name
            from product_instance pi
            join product_catalog pc on pc.gtin = pi.gtin
            where pi.code_hash = :codeHash
        """.trimIndent()

        val INSERT_CHECK_SQL = """
            insert into product_check (
                id,
                client_id,
                idempotency_key,
                request_hash,
                code_hash,
                decision,
                reason_codes,
                unavailable_sources,
                degraded,
                result_snapshot,
                duration_ms,
                created_at
            ) values (
                :id,
                :clientId,
                :idempotencyKey,
                :requestHash,
                :codeHash,
                :decision,
                cast(:reasonCodes as jsonb),
                cast(:unavailableSources as jsonb),
                :degraded,
                cast(:resultSnapshot as jsonb),
                :durationMs,
                :createdAt
            )
        """.trimIndent()
    }
}
