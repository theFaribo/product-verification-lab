package ru.example.productverification.domain.model

import java.time.LocalDate

enum class CirculationStatus {
    IN_CIRCULATION,
    SOLD,
    WITHDRAWN,
}

data class Manufacturer(
    val inn: String,
    val name: String,
) {
    init {
        require(INN_PATTERN.matches(inn)) {
            "manufacturer INN must contain 10 or 12 digits"
        }
        require(name.isNotBlank()) {
            "manufacturer name must not be blank"
        }
        require(name.length <= MAX_NAME_LENGTH) {
            "manufacturer name must not exceed $MAX_NAME_LENGTH characters"
        }
    }

    private companion object {
        val INN_PATTERN = Regex("^(?:\\d{10}|\\d{12})$")
        const val MAX_NAME_LENGTH = 512
    }
}

data class ProductIdentity(
    val gtin: String,
    val serialNumber: String,
) {
    init {
        require(GTIN_PATTERN.matches(gtin)) {
            "GTIN must contain exactly 14 digits"
        }
        require(serialNumber.isNotBlank()) {
            "serial number must not be blank"
        }
        require(serialNumber.length <= MAX_SERIAL_NUMBER_LENGTH) {
            "serial number must not exceed $MAX_SERIAL_NUMBER_LENGTH characters"
        }
    }

    private companion object {
        val GTIN_PATTERN = Regex("^\\d{14}$")
        const val MAX_SERIAL_NUMBER_LENGTH = 64
    }
}

data class ProductSnapshot(
    val identity: ProductIdentity,
    val name: String,
    val manufacturer: Manufacturer,
    val circulationStatus: CirculationStatus,
    val productionDate: LocalDate? = null,
    val expirationDate: LocalDate? = null,
) {
    init {
        require(name.isNotBlank()) {
            "product name must not be blank"
        }
        require(name.length <= MAX_PRODUCT_NAME_LENGTH) {
            "product name must not exceed $MAX_PRODUCT_NAME_LENGTH characters"
        }
        require(
            productionDate == null ||
                expirationDate == null ||
                !productionDate.isAfter(expirationDate),
        ) {
            "production date must not be after expiration date"
        }
    }

    private companion object {
        const val MAX_PRODUCT_NAME_LENGTH = 512
    }
}
