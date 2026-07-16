package ru.example.productverification.reactive.api

import jakarta.validation.ConstraintViolationException
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebInputException
import ru.example.productverification.reactive.application.IdempotencyConflictException
import ru.example.productverification.reactive.application.ProductCheckNotFoundException

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ProductCheckNotFoundException::class)
    fun handleNotFound(
        exception: ProductCheckNotFoundException,
        request: ServerHttpRequest,
    ): ProblemDetail =
        problem(
            status = HttpStatus.NOT_FOUND,
            title = "Product check not found",
            detail = exception.message ?: "Product check was not found",
            type = "/problems/product-check-not-found",
            request = request,
        )

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(
        exception: IdempotencyConflictException,
        request: ServerHttpRequest,
    ): ProblemDetail =
        problem(
            status = HttpStatus.CONFLICT,
            title = "Idempotency key conflict",
            detail = exception.message ?: "Idempotency key was already used for another request",
            type = "/problems/idempotency-conflict",
            request = request,
        )

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(
        exception: WebExchangeBindException,
        request: ServerHttpRequest,
    ): ProblemDetail {
        val detail = problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Request validation failed",
            detail = "One or more request fields are invalid",
            type = "/problems/request-validation",
            request = request,
        )
        detail.setProperty(
            "violations",
            exception.bindingResult.fieldErrors.map { error ->
                mapOf(
                    "field" to error.field,
                    "message" to (error.defaultMessage ?: "invalid value"),
                )
            },
        )
        return detail
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        exception: ConstraintViolationException,
        request: ServerHttpRequest,
    ): ProblemDetail {
        val detail = problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Request validation failed",
            detail = "One or more request parameters are invalid",
            type = "/problems/request-validation",
            request = request,
        )
        detail.setProperty(
            "violations",
            exception.constraintViolations.map { violation ->
                mapOf(
                    "field" to violation.propertyPath.toString(),
                    "message" to violation.message,
                )
            },
        )
        return detail
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleUnreadableBody(
        exception: ServerWebInputException,
        request: ServerHttpRequest,
    ): ProblemDetail =
        problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Malformed request body",
            detail = "Request body is missing or contains invalid JSON values",
            type = "/problems/malformed-request",
            request = request,
        )

    private fun problem(
        status: HttpStatus,
        title: String,
        detail: String,
        type: String,
        request: ServerHttpRequest,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            this.type = URI.create(type)
            this.instance = URI.create(request.path.value())
        }
}
