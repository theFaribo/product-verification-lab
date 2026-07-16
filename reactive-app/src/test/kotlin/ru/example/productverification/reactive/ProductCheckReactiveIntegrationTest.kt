package ru.example.productverification.reactive

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.reactive.TransactionSynchronizationManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Decision
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductCheckDecision
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.UnavailabilityReason
import ru.example.productverification.reactive.api.CreateProductCheckRequest
import ru.example.productverification.reactive.api.ProductCheckResponse
import ru.example.productverification.reactive.api.ScanSource
import ru.example.productverification.reactive.application.CreateProductCheckCommand
import ru.example.productverification.reactive.application.ProductCheckService
import ru.example.productverification.reactive.application.ProductCodeProcessor
import ru.example.productverification.reactive.application.ProductRegistryClient
import ru.example.productverification.reactive.persistence.ProductCheckR2dbcRepository
import ru.example.productverification.reactive.persistence.ProductCheckRowMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(ProductCheckReactiveIntegrationTest.TestClientConfiguration::class)
class ProductCheckReactiveIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var productCheckService: ProductCheckService

    @Autowired
    private lateinit var codeProcessor: ProductCodeProcessor

    @Autowired
    private lateinit var registryClient: ControllableProductRegistryClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var repository: ProductCheckR2dbcRepository

    @Autowired
    private lateinit var rowMapper: ProductCheckRowMapper

    @Autowired
    private lateinit var transactionalOperator: TransactionalOperator

    @BeforeEach
    fun cleanDatabase(): Unit = runBlocking {
        registryClient.reset()
        databaseClient.sql("delete from outbox_event").fetch().awaitRowsUpdated()
        databaseClient.sql("delete from product_check").fetch().awaitRowsUpdated()
        databaseClient.sql("delete from product_instance").fetch().awaitRowsUpdated()
        databaseClient.sql("delete from product_catalog").fetch().awaitRowsUpdated()
    }

    @Test
    fun `creates product check and reads it by id`() {
        val created = postCheck(idempotencyKey = "create-1")
            .expectStatus().isCreated
            .expectHeader().valueMatches("Location", "/api/v1/product-checks/.+")
            .expectBody(ProductCheckResponse::class.java)
            .returnResult()
            .responseBody

        assertNotNull(created)
        assertEquals(Decision.VALID, created!!.decision)
        assertEquals(DEFAULT_REGISTRY_PRODUCT.identity.gtin, created.product?.gtin)
        assertFalse(created.degraded)

        webTestClient.get()
            .uri("/api/v1/product-checks/{id}", created.checkId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.checkId").isEqualTo(created.checkId.toString())
            .jsonPath("$.decision").isEqualTo("VALID")

        assertEquals(1L, countChecks())
        val storedHash = runBlocking {
            databaseClient.sql("select code_hash from product_check")
                .map { row, _ -> requireNotNull(row.get("code_hash", String::class.java)) }
                .awaitOne()
        }
        assertNotEquals(VALID_CODE, storedHash)
        assertEquals(64, storedHash.length)
    }

    @Test
    fun `replays result for the same idempotency key and request`() {
        val first = postCheck(idempotencyKey = "replay-1")
            .expectStatus().isCreated
            .expectBody(ProductCheckResponse::class.java)
            .returnResult()
            .responseBody!!

        val second = postCheck(idempotencyKey = "replay-1")
            .expectStatus().isOk
            .expectBody(ProductCheckResponse::class.java)
            .returnResult()
            .responseBody!!

        assertEquals(first.checkId, second.checkId)
        assertEquals(1L, countChecks())
        assertEquals(1, registryClient.callCount.get())
    }

    @Test
    fun `returns conflict when idempotency key is reused for another request`() {
        postCheck(idempotencyKey = "conflict-1")
            .expectStatus().isCreated

        postCheck(
            idempotencyKey = "conflict-1",
            code = ANOTHER_VALID_CODE,
        )
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Idempotency key conflict")

        assertEquals(1L, countChecks())
    }

    @Test
    fun `concurrent requests with one idempotency key create one row`() = runBlocking {
        val command = defaultCommand(idempotencyKey = "concurrent-1")
        val start = CompletableDeferred<Unit>()

        val results = coroutineScope {
            val jobs = (1..20).map {
                async(Dispatchers.Default) {
                    start.await()
                    productCheckService.check(command)
                }
            }
            start.complete(Unit)
            jobs.awaitAll()
        }

        assertEquals(1, results.map { it.record.id }.toSet().size)
        assertEquals(1, results.count { it.created })
        assertEquals(1L, countChecks())
    }

    @Test
    fun `maps unavailable registry to degraded unknown`() {
        registryClient.returnLookup(
            RegistryLookup.Unavailable(UnavailabilityReason.TIMEOUT),
        )

        postCheck(idempotencyKey = "timeout-1")
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.decision").isEqualTo("UNKNOWN")
            .jsonPath("$.reasonCodes[0]").isEqualTo("REGISTRY_UNAVAILABLE")
            .jsonPath("$.degraded").isEqualTo(true)
            .jsonPath("$.unavailableSources[0]").isEqualTo("PRODUCT_REGISTRY")
    }

    @Test
    fun `maps registry not found to invalid`() {
        registryClient.returnLookup(RegistryLookup.NotFound)

        postCheck(idempotencyKey = "not-found-1")
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.decision").isEqualTo("INVALID")
            .jsonPath("$.reasonCodes[0]").isEqualTo("CODE_NOT_FOUND")
            .jsonPath("$.degraded").isEqualTo(false)
    }

    @Test
    fun `returns unknown for inconsistent local and registry identities`(): Unit = runBlocking {
        val codeHash = codeProcessor.process(
            rawCode = VALID_CODE,
            scanSource = ScanSource.MOBILE,
            regionCode = "77",
        ).codeHash
        insertLocalProduct(
            codeHash = codeHash,
            serialNumber = "DIFFERENT-SERIAL",
        )

        postCheck(idempotencyKey = "inconsistent-1")
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.decision").isEqualTo("UNKNOWN")
            .jsonPath("$.reasonCodes[0]").isEqualTo("INCONSISTENT_DATA")
    }

    @Test
    fun `invalid format does not call external registry`() {
        postCheck(
            idempotencyKey = "format-1",
            code = "not-a-data-matrix",
        )
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.decision").isEqualTo("INVALID")
            .jsonPath("$.reasonCodes[0]").isEqualTo("CODE_FORMAT_INVALID")

        assertEquals(0, registryClient.callCount.get())
    }

    @Test
    fun `validates request body`() {
        postCheck(
            idempotencyKey = "validation-1",
            code = " ",
        )
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.title").isEqualTo("Request validation failed")
            .jsonPath("$.violations").isArray
    }

    @Test
    fun `cancelling parent coroutine cancels registry request and persists nothing`() = runBlocking {
        val probe = registryClient.suspendUntilCancelled()
        val job = launch(Dispatchers.Default) {
            productCheckService.check(defaultCommand(idempotencyKey = "cancel-1"))
        }

        withTimeout(1_000) {
            probe.started.await()
        }
        job.cancelAndJoin()
        withTimeout(1_000) {
            probe.cancelled.await()
        }

        assertEquals(0L, countChecksSuspend())
    }

    @Test
    fun `reactive transaction rolls back insert on failure`() {
        val row = invalidRow(idempotencyKey = "rollback-1")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                transactionalOperator.executeAndAwait {
                    repository.insert(row)
                    throw IllegalStateException("force rollback")
                }
            }
        }

        assertEquals(0L, countChecks())
    }

    @Test
    fun `unique constraint violation is translated by DatabaseClient`() {
        val first = invalidRow(idempotencyKey = "duplicate-1")
        val second = first.copy(id = UUID.randomUUID())

        runBlocking {
            transactionalOperator.executeAndAwait { repository.insert(first) }
        }
        assertThrows(DataIntegrityViolationException::class.java) {
            runBlocking {
                transactionalOperator.executeAndAwait { repository.insert(second) }
            }
        }
    }

    private fun invalidRow(idempotencyKey: String) =
        rowMapper.toRow(
            id = UUID.randomUUID(),
            clientId = "test-client",
            idempotencyKey = idempotencyKey,
            requestHash = "a".repeat(64),
            codeHash = "b".repeat(64),
            decision = ProductCheckDecision(
                decision = Decision.INVALID,
                reasonCodes = listOf(ReasonCode.CODE_FORMAT_INVALID),
                degraded = false,
                unavailableSources = emptyList(),
            ),
            checkedAt = FIXED_CLOCK.instant(),
            durationMs = 1,
            createdAt = FIXED_CLOCK.instant(),
        )

    private fun postCheck(
        idempotencyKey: String,
        code: String = VALID_CODE,
    ): WebTestClient.ResponseSpec =
        webTestClient.post()
            .uri("/api/v1/product-checks")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Client-Id", "mobile-app")
            .header("Idempotency-Key", idempotencyKey)
            .bodyValue(
                CreateProductCheckRequest(
                    code = code,
                    scanSource = ScanSource.MOBILE,
                    regionCode = "77",
                ),
            )
            .exchange()

    private fun defaultCommand(idempotencyKey: String): CreateProductCheckCommand =
        CreateProductCheckCommand(
            clientId = "mobile-app",
            idempotencyKey = idempotencyKey,
            code = VALID_CODE,
            scanSource = ScanSource.MOBILE,
            regionCode = "77",
        )

    private fun countChecks(): Long = runBlocking {
        countChecksSuspend()
    }

    private suspend fun countChecksSuspend(): Long =
        databaseClient.sql("select count(*) as count from product_check")
            .map { row, _ -> requireNotNull(row.get("count", Long::class.java)) }
            .awaitOne()

    private suspend fun insertLocalProduct(
        codeHash: String,
        serialNumber: String,
    ) {
        databaseClient.sql(
            """
                insert into product_catalog (
                    gtin, name, manufacturer_inn, manufacturer_name,
                    category_code, created_at, updated_at
                ) values (
                    :gtin, :name, :manufacturerInn, :manufacturerName,
                    :categoryCode, now(), now()
                )
            """.trimIndent(),
        )
            .bind("gtin", DEFAULT_REGISTRY_PRODUCT.identity.gtin)
            .bind("name", "Локальный товар")
            .bind("manufacturerInn", "7701234567")
            .bind("manufacturerName", "ООО Локальный производитель")
            .bind("categoryCode", "TEST")
            .fetch()
            .awaitRowsUpdated()

        databaseClient.sql(
            """
                insert into product_instance (
                    code_hash, gtin, serial_number, circulation_status,
                    production_date, expiration_date, version, updated_at
                ) values (
                    :codeHash, :gtin, :serialNumber, :circulationStatus,
                    :productionDate, :expirationDate, 0, now()
                )
            """.trimIndent(),
        )
            .bind("codeHash", codeHash)
            .bind("gtin", DEFAULT_REGISTRY_PRODUCT.identity.gtin)
            .bind("serialNumber", serialNumber)
            .bind("circulationStatus", CirculationStatus.IN_CIRCULATION.name)
            .bind("productionDate", LocalDate.of(2026, 1, 1))
            .bind("expirationDate", LocalDate.of(2027, 1, 1))
            .fetch()
            .awaitRowsUpdated()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestClientConfiguration {

        @Bean
        fun productRegistryClient(): ControllableProductRegistryClient =
            ControllableProductRegistryClient()

        @Bean
        @Primary
        fun fixedClock(): Clock = FIXED_CLOCK
    }

    companion object {
        private const val VALID_CODE = "010460123456789021SERIAL001"
        private const val ANOTHER_VALID_CODE = "010460123456789021SERIAL002"

        private val FIXED_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-07-15T12:00:00Z"),
            ZoneOffset.UTC,
        )

        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("product_verification_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }
}

class ControllableProductRegistryClient : ProductRegistryClient {

    val callCount = AtomicInteger()
    private val behavior = AtomicReference<Behavior>(
        Behavior.Return(DEFAULT_REGISTRY_LOOKUP),
    )

    override suspend fun findProduct(codeHash: String): RegistryLookup {
        assertOutsideReactiveTransaction()
        callCount.incrementAndGet()

        return when (val current = behavior.get()) {
            is Behavior.Return -> current.lookup
            is Behavior.AwaitCancellation -> {
                current.probe.started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    current.probe.cancelled.complete(Unit)
                }
            }
        }
    }

    fun returnLookup(lookup: RegistryLookup) {
        behavior.set(Behavior.Return(lookup))
    }

    fun suspendUntilCancelled(): CancellationProbe {
        val probe = CancellationProbe()
        behavior.set(Behavior.AwaitCancellation(probe))
        return probe
    }

    fun reset() {
        callCount.set(0)
        behavior.set(Behavior.Return(DEFAULT_REGISTRY_LOOKUP))
    }

    private suspend fun assertOutsideReactiveTransaction() {
        try {
            TransactionSynchronizationManager.forCurrentTransaction().awaitSingle()
        } catch (_: NoTransactionException) {
            return
        }
        error("External registry call must not run inside a database transaction")
    }

    private sealed interface Behavior {
        data class Return(val lookup: RegistryLookup) : Behavior
        data class AwaitCancellation(val probe: CancellationProbe) : Behavior
    }

    class CancellationProbe(
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val cancelled: CompletableDeferred<Unit> = CompletableDeferred(),
    )
}

private val DEFAULT_REGISTRY_PRODUCT = ProductSnapshot(
    identity = ProductIdentity(
        gtin = "04601234567890",
        serialNumber = "SERIAL-REGISTRY-001",
    ),
    name = "Тестовый товар",
    manufacturer = Manufacturer(
        inn = "7701234567",
        name = "ООО Производитель",
    ),
    circulationStatus = CirculationStatus.IN_CIRCULATION,
    productionDate = LocalDate.of(2026, 1, 1),
    expirationDate = LocalDate.of(2027, 1, 1),
)

private val DEFAULT_REGISTRY_LOOKUP = RegistryLookup.Found(DEFAULT_REGISTRY_PRODUCT)
