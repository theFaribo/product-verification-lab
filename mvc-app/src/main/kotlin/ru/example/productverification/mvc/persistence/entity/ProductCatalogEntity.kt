package ru.example.productverification.mvc.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "product_catalog")
open class ProductCatalogEntity(
    @Id
    @Column(name = "gtin", nullable = false, length = 14)
    open var gtin: String = "",

    @Column(name = "name", nullable = false, length = 512)
    open var name: String = "",

    @Column(name = "manufacturer_inn", nullable = false, length = 12)
    open var manufacturerInn: String = "",

    @Column(name = "manufacturer_name", nullable = false, length = 512)
    open var manufacturerName: String = "",

    @Column(name = "category_code", nullable = false, length = 64)
    open var categoryCode: String = "",

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.EPOCH,
)
