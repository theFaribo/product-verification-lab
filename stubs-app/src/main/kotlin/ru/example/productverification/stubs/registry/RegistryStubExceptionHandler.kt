package ru.example.productverification.stubs.registry

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RegistryStubExceptionHandler {

    @ExceptionHandler(InvalidRegistryStubRequestException::class)
    fun handleInvalidRequest(
        exception: InvalidRegistryStubRequestException,
    ): ProblemDetail =
        problemDetail(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid registry stub request",
            detail = exception.message,
        )

    @ExceptionHandler(RegistryItemNotFoundException::class)
    fun handleNotFound(
        exception: RegistryItemNotFoundException,
    ): ProblemDetail =
        problemDetail(
            status = HttpStatus.NOT_FOUND,
            title = "Registry item not found",
            detail = exception.message,
        )

    @ExceptionHandler(SimulatedRegistryFailureException::class)
    fun handleSimulatedFailure(
        exception: SimulatedRegistryFailureException,
    ): ProblemDetail =
        problemDetail(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            title = "Simulated registry failure",
            detail = exception.message,
        )

    private fun problemDetail(
        status: HttpStatus,
        title: String,
        detail: String?,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            status,
            detail ?: status.reasonPhrase,
        ).apply {
            this.title = title
            setProperty("errorCode", status.name)
        }
}
