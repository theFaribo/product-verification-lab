package ru.example.productverification.mvc.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.example.productverification.mvc.persistence.entity.ProductCatalogEntity

interface ProductCatalogJpaRepository : JpaRepository<ProductCatalogEntity, String>
