package ru.example.productverification.stubs

import kotlinx.coroutines.delay
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.random.Random

@RestController
@RequestMapping("/stub/v1/registry")
class RegistryStubController {

    @GetMapping("/items/{codeHash}")
    suspend fun findItem(
        @PathVariable codeHash: String,
    ): RegistryItemResponse {
        delay(Random.nextLong(from = 30, until = 151))

        return RegistryItemResponse(
            codeHash = codeHash,
            gtin = "04601234567890",
            name = "Тестовый товар",
            circulationStatus = "IN_CIRCULATION",
        )
    }
}

data class RegistryItemResponse(
    val codeHash: String,
    val gtin: String,
    val name: String,
    val circulationStatus: String,
)
