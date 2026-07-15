package ru.example.productverification.domain.decision

import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Decision
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductCheckInput
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductRestrictions
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.RestrictionsLookup
import ru.example.productverification.domain.model.SignatureStatus
import ru.example.productverification.domain.model.SourceSystem
import ru.example.productverification.domain.model.UnavailabilityReason
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductCheckDecisionEngineTest {

    private val engine = ProductCheckDecisionEngine()

    @Test
    fun `invalid code format short-circuits all external checks`() {
        val decision = engine.decide(
            ProductCheckInput(
                codeFormatValid = false,
                checkedAt = CHECKED_AT,
            ),
        )

        assertEquals(Decision.INVALID, decision.decision)
        assertEquals(listOf(ReasonCode.CODE_FORMAT_INVALID), decision.reasonCodes)
        assertFalse(decision.degraded)
        assertNull(decision.product)
    }

    @Test
    fun `invalid signature returns invalid decision`() {
        val decision = engine.decide(
            baseInput(signatureStatus = SignatureStatus.INVALID),
        )

        assertEquals(Decision.INVALID, decision.decision)
        assertEquals(listOf(ReasonCode.SIGNATURE_INVALID), decision.reasonCodes)
    }

    @Test
    fun `unavailable crypto verification returns degraded unknown decision`() {
        val decision = engine.decide(
            baseInput(signatureStatus = SignatureStatus.UNAVAILABLE),
        )

        assertEquals(Decision.UNKNOWN, decision.decision)
        assertEquals(listOf(ReasonCode.CRYPTO_UNAVAILABLE), decision.reasonCodes)
        assertEquals(listOf(SourceSystem.CRYPTO_VERIFICATION), decision.unavailableSources)
        assertTrue(decision.degraded)
    }

    @Test
    fun `signature must be checked for a valid code format`() {
        assertFailsWith<IllegalArgumentException> {
            engine.decide(baseInput(signatureStatus = SignatureStatus.NOT_CHECKED))
        }
    }

    @Test
    fun `missing product in registry returns invalid decision`() {
        val decision = engine.decide(
            baseInput(registryLookup = RegistryLookup.NotFound),
        )

        assertEquals(Decision.INVALID, decision.decision)
        assertEquals(listOf(ReasonCode.CODE_NOT_FOUND), decision.reasonCodes)
    }

    @Test
    fun `unavailable registry returns degraded unknown decision`() {
        val decision = engine.decide(
            baseInput(
                registryLookup = RegistryLookup.Unavailable(
                    UnavailabilityReason.TIMEOUT,
                ),
            ),
        )

        assertEquals(Decision.UNKNOWN, decision.decision)
        assertEquals(listOf(ReasonCode.REGISTRY_UNAVAILABLE), decision.reasonCodes)
        assertEquals(listOf(SourceSystem.PRODUCT_REGISTRY), decision.unavailableSources)
        assertTrue(decision.degraded)
    }

    @Test
    fun `different local and registry identities return unknown decision`() {
        val registryProduct = product()
        val localProduct = product(
            identity = ProductIdentity(
                gtin = "04601234567891",
                serialNumber = "SERIAL-2",
            ),
        )

        val decision = engine.decide(
            baseInput(
                localProduct = localProduct,
                registryLookup = RegistryLookup.Found(registryProduct),
            ),
        )

        assertEquals(Decision.UNKNOWN, decision.decision)
        assertEquals(listOf(ReasonCode.INCONSISTENT_DATA), decision.reasonCodes)
        assertFalse(decision.degraded)
        assertEquals(registryProduct, decision.product)
    }

    @Test
    fun `withdrawn product returns invalid decision`() {
        val decision = decideFor(
            product = product(circulationStatus = CirculationStatus.WITHDRAWN),
        )

        assertEquals(Decision.INVALID, decision.decision)
        assertEquals(listOf(ReasonCode.PRODUCT_WITHDRAWN), decision.reasonCodes)
    }

    @Test
    fun `sold product returns invalid decision`() {
        val decision = decideFor(
            product = product(circulationStatus = CirculationStatus.SOLD),
        )

        assertEquals(Decision.INVALID, decision.decision)
        assertEquals(listOf(ReasonCode.PRODUCT_SOLD), decision.reasonCodes)
    }

    @Test
    fun `prohibited product returns invalid decision`() {
        val decision = decideFor(
            restrictions = ProductRestrictions(prohibited = true),
        )

        assertEquals(Decision.INVALID, decision.decision)
        assertEquals(listOf(ReasonCode.PRODUCT_PROHIBITED), decision.reasonCodes)
    }

    @Test
    fun `recalled product returns warning`() {
        val decision = decideFor(
            restrictions = ProductRestrictions(recalled = true),
        )

        assertEquals(Decision.WARNING, decision.decision)
        assertEquals(listOf(ReasonCode.PRODUCT_RECALLED), decision.reasonCodes)
    }

    @Test
    fun `product expired before check date returns warning`() {
        val decision = decideFor(
            product = product(expirationDate = LocalDate.of(2026, 7, 14)),
        )

        assertEquals(Decision.WARNING, decision.decision)
        assertEquals(listOf(ReasonCode.PRODUCT_EXPIRED), decision.reasonCodes)
    }

    @Test
    fun `product remains valid during its expiration date`() {
        val decision = decideFor(
            product = product(expirationDate = LocalDate.of(2026, 7, 15)),
        )

        assertEquals(Decision.VALID, decision.decision)
        assertTrue(decision.reasonCodes.isEmpty())
    }

    @Test
    fun `unavailable optional restrictions produce degraded valid decision`() {
        val product = product()

        val decision = engine.decide(
            baseInput(
                registryLookup = RegistryLookup.Found(product),
                restrictionsLookup = RestrictionsLookup.Unavailable(
                    UnavailabilityReason.SERVER_ERROR,
                ),
            ),
        )

        assertEquals(Decision.VALID, decision.decision)
        assertEquals(listOf(ReasonCode.RESTRICTIONS_UNAVAILABLE), decision.reasonCodes)
        assertEquals(listOf(SourceSystem.PRODUCT_RESTRICTIONS), decision.unavailableSources)
        assertTrue(decision.degraded)
    }

    @Test
    fun `business invalidity wins while optional source degradation is preserved`() {
        val soldProduct = product(circulationStatus = CirculationStatus.SOLD)

        val decision = engine.decide(
            baseInput(
                registryLookup = RegistryLookup.Found(soldProduct),
                restrictionsLookup = RestrictionsLookup.Unavailable(
                    UnavailabilityReason.TIMEOUT,
                ),
            ),
        )

        assertEquals(Decision.INVALID, decision.decision)
        assertEquals(
            listOf(
                ReasonCode.PRODUCT_SOLD,
                ReasonCode.RESTRICTIONS_UNAVAILABLE,
            ),
            decision.reasonCodes,
        )
        assertTrue(decision.degraded)
    }

    @Test
    fun `all applicable warning reasons are returned in deterministic order`() {
        val decision = decideFor(
            product = product(expirationDate = LocalDate.of(2026, 7, 14)),
            restrictions = ProductRestrictions(recalled = true),
        )

        assertEquals(Decision.WARNING, decision.decision)
        assertEquals(
            listOf(
                ReasonCode.PRODUCT_RECALLED,
                ReasonCode.PRODUCT_EXPIRED,
            ),
            decision.reasonCodes,
        )
    }

    @Test
    fun `restrictions must be requested before returning non-terminal decision`() {
        assertFailsWith<IllegalArgumentException> {
            engine.decide(
                baseInput(
                    registryLookup = RegistryLookup.Found(product()),
                    restrictionsLookup = RestrictionsLookup.NotRequested,
                ),
            )
        }
    }

    @Test
    fun `successful checks return valid product`() {
        val product = product()

        val decision = decideFor(product = product)

        assertEquals(Decision.VALID, decision.decision)
        assertTrue(decision.reasonCodes.isEmpty())
        assertFalse(decision.degraded)
        assertEquals(product, decision.product)
    }

    private fun decideFor(
        product: ProductSnapshot = product(),
        restrictions: ProductRestrictions = ProductRestrictions(),
    ) =
        engine.decide(
            baseInput(
                registryLookup = RegistryLookup.Found(product),
                restrictionsLookup = RestrictionsLookup.Found(restrictions),
            ),
        )

    private fun baseInput(
        signatureStatus: SignatureStatus = SignatureStatus.VALID,
        localProduct: ProductSnapshot? = null,
        registryLookup: RegistryLookup = RegistryLookup.NotRequested,
        restrictionsLookup: RestrictionsLookup = RestrictionsLookup.NotRequested,
    ) =
        ProductCheckInput(
            codeFormatValid = true,
            signatureStatus = signatureStatus,
            localProduct = localProduct,
            registryLookup = registryLookup,
            restrictionsLookup = restrictionsLookup,
            checkedAt = CHECKED_AT,
        )

    private fun product(
        identity: ProductIdentity = ProductIdentity(
            gtin = "04601234567890",
            serialNumber = "SERIAL-1",
        ),
        circulationStatus: CirculationStatus = CirculationStatus.IN_CIRCULATION,
        expirationDate: LocalDate? = LocalDate.of(2027, 7, 15),
    ) =
        ProductSnapshot(
            identity = identity,
            name = "Тестовый товар",
            manufacturer = Manufacturer(
                inn = "7701234567",
                name = "ООО Производитель",
            ),
            circulationStatus = circulationStatus,
            productionDate = LocalDate.of(2026, 6, 1),
            expirationDate = expirationDate,
        )

    private companion object {
        val CHECKED_AT: Instant = Instant.parse("2026-07-15T12:00:00Z")
    }
}
