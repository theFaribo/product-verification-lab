package ru.example.productverification.domain.model

enum class Decision {
    VALID,
    WARNING,
    INVALID,
    UNKNOWN,
}

enum class ReasonCode {
    CODE_FORMAT_INVALID,
    SIGNATURE_INVALID,
    CRYPTO_UNAVAILABLE,
    CODE_NOT_FOUND,
    PRODUCT_WITHDRAWN,
    PRODUCT_SOLD,
    PRODUCT_PROHIBITED,
    PRODUCT_RECALLED,
    PRODUCT_EXPIRED,
    REGISTRY_UNAVAILABLE,
    RESTRICTIONS_UNAVAILABLE,
    INCONSISTENT_DATA,
}

enum class SourceSystem {
    CRYPTO_VERIFICATION,
    PRODUCT_REGISTRY,
    PRODUCT_RESTRICTIONS,
}

data class ProductCheckDecision(
    val decision: Decision,
    val reasonCodes: List<ReasonCode>,
    val degraded: Boolean,
    val unavailableSources: List<SourceSystem>,
    val product: ProductSnapshot? = null,
) {
    init {
        require(reasonCodes.size == reasonCodes.distinct().size) {
            "reason codes must not contain duplicates"
        }
        require(unavailableSources.size == unavailableSources.distinct().size) {
            "unavailable sources must not contain duplicates"
        }
        require(degraded == unavailableSources.isNotEmpty()) {
            "degraded flag must match unavailable sources"
        }
        require(decision != Decision.VALID || degraded || reasonCodes.isEmpty()) {
            "non-degraded VALID decision must not contain reason codes"
        }
    }
}
