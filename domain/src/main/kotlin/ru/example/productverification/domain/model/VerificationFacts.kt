package ru.example.productverification.domain.model

import java.time.Instant

enum class SignatureStatus {
    VALID,
    INVALID,
    UNAVAILABLE,
    NOT_CHECKED,
}

enum class UnavailabilityReason {
    TIMEOUT,
    CONNECTION_ERROR,
    SERVER_ERROR,
    CIRCUIT_OPEN,
    RATE_LIMITED,
}

data class ProductRestrictions(
    val recalled: Boolean = false,
    val prohibited: Boolean = false,
)

sealed interface RegistryLookup {
    data class Found(
        val product: ProductSnapshot,
    ) : RegistryLookup

    data object NotFound : RegistryLookup

    data class Unavailable(
        val reason: UnavailabilityReason,
    ) : RegistryLookup

    data object NotRequested : RegistryLookup
}

sealed interface RestrictionsLookup {
    data class Found(
        val restrictions: ProductRestrictions,
    ) : RestrictionsLookup

    data class Unavailable(
        val reason: UnavailabilityReason,
    ) : RestrictionsLookup

    data object NotRequested : RestrictionsLookup
}

data class ProductCheckInput(
    val codeFormatValid: Boolean,
    val signatureStatus: SignatureStatus = SignatureStatus.NOT_CHECKED,
    val localProduct: ProductSnapshot? = null,
    val registryLookup: RegistryLookup = RegistryLookup.NotRequested,
    val restrictionsLookup: RestrictionsLookup = RestrictionsLookup.NotRequested,
    val checkedAt: Instant,
)
