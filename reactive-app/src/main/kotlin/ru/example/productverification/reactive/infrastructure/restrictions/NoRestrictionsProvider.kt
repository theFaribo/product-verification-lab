package ru.example.productverification.reactive.infrastructure.restrictions

import org.springframework.stereotype.Component
import ru.example.productverification.domain.model.ProductRestrictions
import ru.example.productverification.domain.model.RestrictionsLookup
import ru.example.productverification.reactive.application.ProductRestrictionsProvider

@Component
class NoRestrictionsProvider : ProductRestrictionsProvider {

    override suspend fun findRestrictions(gtin: String): RestrictionsLookup =
        RestrictionsLookup.Found(ProductRestrictions())
}
