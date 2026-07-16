package ru.example.productverification.mvc.persistence

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.example.productverification.domain.model.Manufacturer
import ru.example.productverification.domain.model.ProductIdentity
import ru.example.productverification.domain.model.ProductSnapshot
import ru.example.productverification.mvc.persistence.entity.ProductCheckEntity
import ru.example.productverification.mvc.persistence.repository.ProductCheckJpaRepository
import ru.example.productverification.mvc.persistence.repository.ProductInstanceJpaRepository
import java.util.UUID

@Service
class ProductCheckPersistenceService(
    private val productCheckRepository: ProductCheckJpaRepository,
    private val productInstanceRepository: ProductInstanceJpaRepository,
) {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ProductCheckEntity? =
        productCheckRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun findByIdempotencyKey(
        clientId: String,
        idempotencyKey: String,
    ): ProductCheckEntity? =
        productCheckRepository.findByClientIdAndIdempotencyKey(
            clientId = clientId,
            idempotencyKey = idempotencyKey,
        )

    @Transactional(readOnly = true)
    fun findLocalProduct(codeHash: String): ProductSnapshot? =
        productInstanceRepository.findWithCatalogByCodeHash(codeHash)
            ?.let { instance ->
                ProductSnapshot(
                    identity = ProductIdentity(
                        gtin = instance.catalog.gtin,
                        serialNumber = instance.serialNumber,
                    ),
                    name = instance.catalog.name,
                    manufacturer = Manufacturer(
                        inn = instance.catalog.manufacturerInn,
                        name = instance.catalog.manufacturerName,
                    ),
                    circulationStatus = instance.circulationStatus,
                    productionDate = instance.productionDate,
                    expirationDate = instance.expirationDate,
                )
            }

    @Transactional
    fun insert(entity: ProductCheckEntity): ProductCheckEntity =
        productCheckRepository.saveAndFlush(entity)
}
