package ru.example.productverification.mvc.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import ru.example.productverification.domain.model.CirculationStatus
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "product_instance")
open class ProductInstanceEntity(
    @Id
    @Column(name = "code_hash", nullable = false, length = 64)
    open var codeHash: String = "",

    @Column(name = "serial_number", nullable = false, length = 64)
    open var serialNumber: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gtin", nullable = false)
    open var catalog: ProductCatalogEntity = ProductCatalogEntity(),

    @Column(name = "circulation_status", nullable = false, length = 32)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    open var circulationStatus: CirculationStatus = CirculationStatus.IN_CIRCULATION,

    @Column(name = "production_date")
    open var productionDate: LocalDate? = null,

    @Column(name = "expiration_date")
    open var expirationDate: LocalDate? = null,

    @Version
    @Column(name = "version", nullable = false)
    open var version: Long = 0,

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.EPOCH,
)
