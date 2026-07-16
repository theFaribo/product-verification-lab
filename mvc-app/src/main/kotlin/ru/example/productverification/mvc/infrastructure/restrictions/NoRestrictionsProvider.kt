package ru.example.productverification.mvc.infrastructure.restrictions

import org.springframework.stereotype.Component
import ru.example.productverification.domain.model.ProductRestrictions
import ru.example.productverification.domain.model.RestrictionsLookup
import ru.example.productverification.mvc.application.ProductRestrictionsProvider

@Component
class NoRestrictionsProvider : ProductRestrictionsProvider {

    override fun findRestrictions(gtin: String): RestrictionsLookup =
        RestrictionsLookup.Found(ProductRestrictions())
}
