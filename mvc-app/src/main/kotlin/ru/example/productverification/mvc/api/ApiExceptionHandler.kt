package ru.example.productverification.mvc.api

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import ru.example.productverification.mvc.application.IdempotencyConflictException
import ru.example.productverification.mvc.application.ProductCheckNotFoundException
import java.net.URI

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ProductCheckNotFoundException::class)
    fun handleNotFound(
        exception: ProductCheckNotFoundException,
        request: HttpServletRequest,
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
        request: HttpServletRequest,
    ): ProblemDetail =
        problem(
            status = HttpStatus.CONFLICT,
            title = "Idempotency key conflict",
            detail = exception.message ?: "Idempotency key was already used for another request",
            type = "/problems/idempotency-conflict",
            request = request,
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
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

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
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
        request: HttpServletRequest,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            this.type = URI.create(type)
            this.instance = URI.create(request.requestURI)
        }
}
