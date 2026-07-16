package ru.example.productverification.mvc.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.example.productverification.mvc.persistence.entity.ProductCheckEntity
import java.util.UUID

interface ProductCheckJpaRepository : JpaRepository<ProductCheckEntity, UUID> {

    fun findByClientIdAndIdempotencyKey(
        clientId: String,
        idempotencyKey: String,
    ): ProductCheckEntity?
}
