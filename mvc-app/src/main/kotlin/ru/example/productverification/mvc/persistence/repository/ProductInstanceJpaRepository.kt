package ru.example.productverification.mvc.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.example.productverification.mvc.persistence.entity.ProductInstanceEntity

interface ProductInstanceJpaRepository : JpaRepository<ProductInstanceEntity, String> {

    @Query(
        """
        select productInstance
        from ProductInstanceEntity productInstance
        join fetch productInstance.catalog
        where productInstance.codeHash = :codeHash
        """,
    )
    fun findWithCatalogByCodeHash(
        @Param("codeHash") codeHash: String,
    ): ProductInstanceEntity?
}
