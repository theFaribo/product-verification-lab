package ru.example.productverification.mvc.infrastructure.crypto

import org.springframework.stereotype.Component
import ru.example.productverification.domain.model.SignatureStatus
import ru.example.productverification.mvc.application.SignatureVerifier

@Component
class LocalSignatureVerifier : SignatureVerifier {

    override fun verify(code: String): SignatureStatus {
        // Граница интеграции уже выделена. На следующем этапе здесь должен быть
        // реальный crypto adapter либо вызов внешнего Crypto Verification API.
        return SignatureStatus.VALID
    }
}
