package ru.example.productverification.reactive.infrastructure.crypto

import org.springframework.stereotype.Component
import ru.example.productverification.domain.model.SignatureStatus
import ru.example.productverification.reactive.application.SignatureVerifier

@Component
class LocalSignatureVerifier : SignatureVerifier {

    override suspend fun verify(code: String): SignatureStatus =
        SignatureStatus.VALID
}
