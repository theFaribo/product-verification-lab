package ru.example.productverification.domain.decision

import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.Decision
import ru.example.productverification.domain.model.ProductCheckDecision
import ru.example.productverification.domain.model.ProductCheckInput
import ru.example.productverification.domain.model.ProductRestrictions
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.domain.model.ReasonCode
import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.RestrictionsLookup
import ru.example.productverification.domain.model.SignatureStatus
import ru.example.productverification.domain.model.SourceSystem
import java.time.LocalDate
import java.time.ZoneOffset

class ProductCheckDecisionEngine {

    fun decide(input: ProductCheckInput): ProductCheckDecision {
        if (!input.codeFormatValid) {
            return terminalDecision(
                decision = Decision.INVALID,
                reasonCode = ReasonCode.CODE_FORMAT_INVALID,
            )
        }

        when (input.signatureStatus) {
            SignatureStatus.INVALID -> {
                return terminalDecision(
                    decision = Decision.INVALID,
                    reasonCode = ReasonCode.SIGNATURE_INVALID,
                )
            }

            SignatureStatus.UNAVAILABLE -> {
                return terminalDecision(
                    decision = Decision.UNKNOWN,
                    reasonCode = ReasonCode.CRYPTO_UNAVAILABLE,
                    unavailableSource = SourceSystem.CRYPTO_VERIFICATION,
                )
            }

            SignatureStatus.NOT_CHECKED -> {
                throw IllegalArgumentException(
                    "signature must be checked when code format is valid",
                )
            }

            SignatureStatus.VALID -> Unit
        }

        val registryProduct = when (val registryLookup = input.registryLookup) {
            is RegistryLookup.Found -> registryLookup.product

            RegistryLookup.NotFound -> {
                return terminalDecision(
                    decision = Decision.INVALID,
                    reasonCode = ReasonCode.CODE_NOT_FOUND,
                )
            }

            is RegistryLookup.Unavailable -> {
                return terminalDecision(
                    decision = Decision.UNKNOWN,
                    reasonCode = ReasonCode.REGISTRY_UNAVAILABLE,
                    unavailableSource = SourceSystem.PRODUCT_REGISTRY,
                )
            }

            RegistryLookup.NotRequested -> {
                throw IllegalArgumentException(
                    "product registry must be requested after successful signature verification",
                )
            }
        }

        if (!isIdentityConsistent(input.localProduct, registryProduct)) {
            return ProductCheckDecision(
                decision = Decision.UNKNOWN,
                reasonCodes = listOf(ReasonCode.INCONSISTENT_DATA),
                degraded = false,
                unavailableSources = emptyList(),
                product = registryProduct,
            )
        }

        return decideUsingProductFacts(
            product = registryProduct,
            restrictionsLookup = input.restrictionsLookup,
            checkedAtDate = LocalDate.ofInstant(input.checkedAt, ZoneOffset.UTC),
        )
    }

    private fun decideUsingProductFacts(
        product: ProductSnapshot,
        restrictionsLookup: RestrictionsLookup,
        checkedAtDate: LocalDate,
    ): ProductCheckDecision {
        val reasonCodes = mutableListOf<ReasonCode>()
        val unavailableSources = mutableListOf<SourceSystem>()

        when (product.circulationStatus) {
            CirculationStatus.WITHDRAWN -> {
                reasonCodes += ReasonCode.PRODUCT_WITHDRAWN
            }

            CirculationStatus.SOLD -> {
                reasonCodes += ReasonCode.PRODUCT_SOLD
            }

            CirculationStatus.IN_CIRCULATION -> Unit
        }

        when (restrictionsLookup) {
            is RestrictionsLookup.Found -> {
                appendRestrictionReasons(
                    restrictions = restrictionsLookup.restrictions,
                    reasonCodes = reasonCodes,
                )
            }

            is RestrictionsLookup.Unavailable -> {
                reasonCodes += ReasonCode.RESTRICTIONS_UNAVAILABLE
                unavailableSources += SourceSystem.PRODUCT_RESTRICTIONS
            }

            RestrictionsLookup.NotRequested -> {
                require(reasonCodes.any { it.isInvalidReason() }) {
                    "product restrictions must be requested before a non-terminal decision"
                }
            }
        }

        if (product.expirationDate?.isBefore(checkedAtDate) == true) {
            reasonCodes += ReasonCode.PRODUCT_EXPIRED
        }

        return ProductCheckDecision(
            decision = resolveDecision(reasonCodes),
            reasonCodes = reasonCodes,
            degraded = unavailableSources.isNotEmpty(),
            unavailableSources = unavailableSources,
            product = product,
        )
    }

    private fun appendRestrictionReasons(
        restrictions: ProductRestrictions,
        reasonCodes: MutableList<ReasonCode>,
    ) {
        if (restrictions.prohibited) {
            reasonCodes += ReasonCode.PRODUCT_PROHIBITED
        }
        if (restrictions.recalled) {
            reasonCodes += ReasonCode.PRODUCT_RECALLED
        }
    }

    private fun resolveDecision(reasonCodes: List<ReasonCode>): Decision =
        when {
            reasonCodes.any { it.isInvalidReason() } -> Decision.INVALID
            reasonCodes.any { it.isWarningReason() } -> Decision.WARNING
            else -> Decision.VALID
        }

    private fun ReasonCode.isInvalidReason(): Boolean =
        this == ReasonCode.PRODUCT_WITHDRAWN ||
            this == ReasonCode.PRODUCT_SOLD ||
            this == ReasonCode.PRODUCT_PROHIBITED

    private fun ReasonCode.isWarningReason(): Boolean =
        this == ReasonCode.PRODUCT_RECALLED ||
            this == ReasonCode.PRODUCT_EXPIRED

    private fun isIdentityConsistent(
        localProduct: ProductSnapshot?,
        registryProduct: ProductSnapshot,
    ): Boolean =
        localProduct == null || localProduct.identity == registryProduct.identity

    private fun terminalDecision(
        decision: Decision,
        reasonCode: ReasonCode,
        unavailableSource: SourceSystem? = null,
    ): ProductCheckDecision =
        ProductCheckDecision(
            decision = decision,
            reasonCodes = listOf(reasonCode),
            degraded = unavailableSource != null,
            unavailableSources = listOfNotNull(unavailableSource),
            product = null,
        )
}
