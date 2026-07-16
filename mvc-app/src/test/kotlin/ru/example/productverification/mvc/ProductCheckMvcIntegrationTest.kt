package ru.example.productverification.mvc

import com.jayway.jsonpath.JsonPath
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.UnavailabilityReason
import ru.example.productverification.mvc.api.ScanSource
import ru.example.productverification.mvc.application.CreateProductCheckCommand
import ru.example.productverification.mvc.application.ProductCheckService
import ru.example.productverification.mvc.application.ProductCodeProcessor
import ru.example.productverification.mvc.application.ProductRegistryClient
import ru.example.productverification.mvc.persistence.entity.ProductCatalogEntity
import ru.example.productverification.mvc.persistence.entity.ProductInstanceEntity
import ru.example.productverification.mvc.persistence.repository.ProductCatalogJpaRepository
import ru.example.productverification.mvc.persistence.repository.ProductCheckJpaRepository
import ru.example.productverification.mvc.persistence.repository.ProductInstanceJpaRepository

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(ProductCheckMvcIntegrationTest.TestClientConfiguration::class)
class ProductCheckMvcIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var registryClient: ControllableProductRegistryClient

    @Autowired
    private lateinit var productCheckRepository: ProductCheckJpaRepository

    @Autowired
    private lateinit var productInstanceRepository: ProductInstanceJpaRepository

    @Autowired
    private lateinit var productCatalogRepository: ProductCatalogJpaRepository

    @Autowired
    private lateinit var codeProcessor: ProductCodeProcessor

    @Autowired
    private lateinit var productCheckService: ProductCheckService

    @BeforeEach
    fun setUp() {
        productCheckRepository.deleteAllInBatch()
        productInstanceRepository.deleteAllInBatch()
        productCatalogRepository.deleteAllInBatch()
        registryClient.respondWith(RegistryLookup.Found(defaultRegistryProduct()))
    }

    @Test
    fun `should create and then return a valid product check`() {
        val createResult = mockMvc.perform(
            createRequest(
                code = VALID_CODE,
                idempotencyKey = "check-1",
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/product-checks/")))
            .andExpect(jsonPath("$.decision").value("VALID"))
            .andExpect(jsonPath("$.degraded").value(false))
            .andExpect(jsonPath("$.reasonCodes.length()").value(0))
            .andExpect(jsonPath("$.product.gtin").value("04601234567890"))
            .andReturn()

        val checkId = JsonPath.read<String>(
            createResult.response.contentAsString,
            "$.checkId",
        )

        mockMvc.perform(get("/api/v1/product-checks/{checkId}", checkId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkId").value(checkId))
            .andExpect(jsonPath("$.decision").value("VALID"))
            .andExpect(jsonPath("$.product.serialNumber").value("SERIAL-REGISTRY-001"))

        assertEquals(1, productCheckRepository.count())
        assertNotEquals(
            VALID_CODE,
            productCheckRepository.findAll().single().codeHash,
        )
    }

    @Test
    fun `should replay the same result for the same idempotency key and request`() {
        val first = mockMvc.perform(
            createRequest(
                code = VALID_CODE,
                idempotencyKey = "same-key",
            ),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val second = mockMvc.perform(
            createRequest(
                code = VALID_CODE,
                idempotencyKey = "same-key",
            ),
        )
            .andExpect(status().isOk)
            .andReturn()

        val firstId = JsonPath.read<String>(first.response.contentAsString, "$.checkId")
        val secondId = JsonPath.read<String>(second.response.contentAsString, "$.checkId")

        assertEquals(firstId, secondId)
        assertEquals(1, productCheckRepository.count())
    }

    @Test
    fun `should create one row for concurrent requests with the same idempotency key`() {
        val executor = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val command = CreateProductCheckCommand(
            clientId = "mobile-app",
            idempotencyKey = "concurrent-key",
            code = VALID_CODE,
            scanSource = ScanSource.MOBILE,
            regionCode = "77",
        )

        try {
            val futures = (1..20).map {
                executor.submit<String> {
                    start.await()
                    productCheckService.check(command).record.id.toString()
                }
            }

            start.countDown()

            val checkIds = futures.map { future ->
                future.get(15, TimeUnit.SECONDS)
            }

            assertEquals(1, checkIds.distinct().size)
            assertEquals(1, productCheckRepository.count())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `should reject reused idempotency key with another request`() {
        mockMvc.perform(
            createRequest(
                code = VALID_CODE,
                idempotencyKey = "conflicting-key",
            ),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            createRequest(
                code = ANOTHER_VALID_CODE,
                idempotencyKey = "conflicting-key",
            ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Idempotency key conflict"))

        assertEquals(1, productCheckRepository.count())
    }

    @Test
    fun `should persist invalid decision when code is absent in registry`() {
        registryClient.respondWith(RegistryLookup.NotFound)

        mockMvc.perform(
            createRequest(
                code = VALID_CODE,
                idempotencyKey = "not-found",
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.decision").value("INVALID"))
            .andExpect(jsonPath("$.reasonCodes[0]").value("CODE_NOT_FOUND"))
            .andExpect(jsonPath("$.product").doesNotExist())
    }

    @Test
    fun `should persist degraded unknown decision when registry times out`() {
        registryClient.respondWith(
            RegistryLookup.Unavailable(UnavailabilityReason.TIMEOUT),
        )

        mockMvc.perform(
            createRequest(
                code = VALID_CODE,
                idempotencyKey = "timeout",
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.decision").value("UNKNOWN"))
            .andExpect(jsonPath("$.degraded").value(true))
            .andExpect(jsonPath("$.reasonCodes[0]").value("REGISTRY_UNAVAILABLE"))
            .andExpect(jsonPath("$.unavailableSources[0]").value("PRODUCT_REGISTRY"))
    }

    @Test
    fun `should detect inconsistency between local storage and registry`() {
        val processedCode = codeProcessor.process(
            rawCode = VALID_CODE,
            scanSource = ScanSource.MOBILE,
            regionCode = "77",
        )
        val now = Instant.parse("2026-07-15T12:00:00Z")
        val catalog = productCatalogRepository.saveAndFlush(
            ProductCatalogEntity(
                gtin = "01234567890123",
                name = "Локальный товар",
                manufacturerInn = "7701234567",
                manufacturerName = "ООО Локальный производитель",
                categoryCode = "TEST",
                createdAt = now,
                updatedAt = now,
            ),
        )
        productInstanceRepository.saveAndFlush(
            ProductInstanceEntity(
                codeHash = processedCode.codeHash,
                serialNumber = "LOCAL-SERIAL",
                catalog = catalog,
                circulationStatus = CirculationStatus.IN_CIRCULATION,
                productionDate = LocalDate.parse("2026-01-01"),
                expirationDate = LocalDate.parse("2027-01-01"),
                updatedAt = now,
            ),
        )

        mockMvc.perform(
            createRequest(
                code = VALID_CODE,
                idempotencyKey = "inconsistent",
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.decision").value("UNKNOWN"))
            .andExpect(jsonPath("$.reasonCodes[0]").value("INCONSISTENT_DATA"))
    }

    @Test
    fun `should return invalid decision for syntactically invalid code`() {
        mockMvc.perform(
            createRequest(
                code = "not-a-data-matrix",
                idempotencyKey = "invalid-format",
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.decision").value("INVALID"))
            .andExpect(jsonPath("$.reasonCodes[0]").value("CODE_FORMAT_INVALID"))
    }

    @Test
    fun `should reject malformed request before calling application service`() {
        mockMvc.perform(
            post("/api/v1/product-checks")
                .header("X-Client-Id", "mobile-app")
                .header("Idempotency-Key", "bad-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "",
                      "scanSource": "MOBILE",
                      "regionCode": "invalid"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Request validation failed"))

        assertEquals(0, productCheckRepository.count())
    }

    private fun createRequest(
        code: String,
        idempotencyKey: String,
    ) =
        post("/api/v1/product-checks")
            .header("X-Client-Id", "mobile-app")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "code": "$code",
                  "scanSource": "MOBILE",
                  "regionCode": "77"
                }
                """.trimIndent(),
            )

    @TestConfiguration(proxyBeanMethods = false)
    class TestClientConfiguration {

        @Bean
        fun productRegistryClient(): ControllableProductRegistryClient =
            ControllableProductRegistryClient()
    }

    class ControllableProductRegistryClient : ProductRegistryClient {
        private val response = AtomicReference<RegistryLookup>(
            RegistryLookup.Found(defaultRegistryProduct()),
        )

        fun respondWith(result: RegistryLookup) {
            response.set(result)
        }

        override fun findProduct(codeHash: String): RegistryLookup {
            check(!TransactionSynchronizationManager.isActualTransactionActive()) {
                "External HTTP calls must not run inside a database transaction"
            }
            return response.get()
        }
    }

    companion object {
        private const val VALID_CODE = "010460123456789021SERIAL001"
        private const val ANOTHER_VALID_CODE = "010460123456789021SERIAL002"

        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("product_verification_test")
            .withUsername("test")
            .withPassword("test")

        private fun defaultRegistryProduct(): ProductSnapshot =
            ProductSnapshot(
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
                productionDate = LocalDate.parse("2026-06-15"),
                expirationDate = LocalDate.parse("2027-07-15"),
            )
    }
}
