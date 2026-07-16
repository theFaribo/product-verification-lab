package ru.example.productverification.reactive.persistence

import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import ru.example.productverification.domain.model.ProductSnapshot
import java.util.UUID

@Service
class ProductCheckPersistenceService(
    private val repository: ProductCheckR2dbcRepository,
    private val transactionalOperator: TransactionalOperator,
) {

    suspend fun findById(id: UUID): ProductCheckRow? =
        repository.findById(id)

    suspend fun findByIdempotencyKey(
        clientId: String,
        idempotencyKey: String,
    ): ProductCheckRow? =
        repository.findByIdempotencyKey(
            clientId = clientId,
            idempotencyKey = idempotencyKey,
        )

    suspend fun findLocalProduct(codeHash: String): ProductSnapshot? =
        repository.findLocalProduct(codeHash)

    suspend fun insert(row: ProductCheckRow): ProductCheckRow =
        transactionalOperator.executeAndAwait {
            repository.insert(row)
        }
}
