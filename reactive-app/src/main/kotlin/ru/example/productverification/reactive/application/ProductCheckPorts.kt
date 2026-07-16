package ru.example.productverification.reactive.application

import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.RestrictionsLookup
import ru.example.productverification.domain.model.SignatureStatus

interface ProductRegistryClient {
    suspend fun findProduct(codeHash: String): RegistryLookup
}

interface SignatureVerifier {
    suspend fun verify(code: String): SignatureStatus
}

interface ProductRestrictionsProvider {
    suspend fun findRestrictions(gtin: String): RestrictionsLookup
}
