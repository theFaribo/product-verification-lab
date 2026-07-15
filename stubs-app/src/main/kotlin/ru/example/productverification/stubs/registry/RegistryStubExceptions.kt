package ru.example.productverification.stubs.registry

sealed class RegistryStubException(
    message: String,
) : RuntimeException(message)

class InvalidRegistryStubRequestException(
    message: String,
) : RegistryStubException(message)

class RegistryItemNotFoundException(
    codeHash: String,
) : RegistryStubException(
        "registry item was not found for code hash '${codeHash.take(HASH_PREFIX_LENGTH)}...'",
    ) {
    private companion object {
        const val HASH_PREFIX_LENGTH = 12
    }
}

class SimulatedRegistryFailureException : RegistryStubException(
    "simulated product registry failure",
)
