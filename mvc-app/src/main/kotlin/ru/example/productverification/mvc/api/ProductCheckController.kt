package ru.example.productverification.mvc.api

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.net.URI
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.example.productverification.mvc.application.CreateProductCheckCommand
import ru.example.productverification.mvc.application.ProductCheckService

@Validated
@RestController
@RequestMapping("/api/v1/product-checks")
class ProductCheckController(
    private val productCheckService: ProductCheckService,
) {

    @PostMapping
    fun create(
        @RequestHeader("X-Client-Id")
        @Size(min = 1, max = 128)
        clientId: String,

        @RequestHeader("Idempotency-Key")
        @Size(min = 1, max = 128)
        idempotencyKey: String,

        @Valid
        @RequestBody
        request: CreateProductCheckRequest,
    ): ResponseEntity<ProductCheckResponse> {
        val result = productCheckService.check(
            CreateProductCheckCommand(
                clientId = clientId,
                idempotencyKey = idempotencyKey,
                code = request.code,
                scanSource = request.scanSource,
                regionCode = request.regionCode,
            ),
        )
        val response = result.record.toResponse()

        return if (result.created) {
            ResponseEntity
                .created(URI.create("/api/v1/product-checks/${response.checkId}"))
                .body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }

    @GetMapping("/{checkId}")
    fun get(
        @PathVariable checkId: UUID,
    ): ProductCheckResponse =
        productCheckService.get(checkId).toResponse()
}
