package ru.example.productverification.mvc.application

import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.RestrictionsLookup
import ru.example.productverification.domain.model.SignatureStatus

interface ProductRegistryClient {
    fun findProduct(codeHash: String): RegistryLookup
}

interface SignatureVerifier {
    fun verify(code: String): SignatureStatus
}

interface ProductRestrictionsProvider {
    fun findRestrictions(gtin: String): RestrictionsLookup
}
