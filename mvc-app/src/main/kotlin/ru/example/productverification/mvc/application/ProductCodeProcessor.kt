package ru.example.productverification.mvc.application

import org.springframework.stereotype.Component
import ru.example.productverification.mvc.api.ScanSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

@Component
class ProductCodeProcessor {

    fun process(
        rawCode: String,
        scanSource: ScanSource,
        regionCode: String,
    ): ProcessedProductCode {
        val normalizedCode = rawCode.trim(' ', '\t', '\r', '\n')

        return ProcessedProductCode(
            normalizedCode = normalizedCode,
            formatValid = normalizedCode.length <= MAX_CODE_LENGTH &&
                DATA_MATRIX_PATTERN.matches(normalizedCode),
            codeHash = sha256(normalizedCode),
            requestHash = sha256(
                listOf(
                    normalizedCode,
                    scanSource.name,
                    regionCode,
                ).joinToString(separator = "\u0000"),
            ),
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))

        return HexFormat.of().formatHex(digest)
    }

    private companion object {
        const val MAX_CODE_LENGTH = 512

        // Учебный валидатор проверяет обязательные AI 01 (GTIN) и 21 (serial).
        // Полную GS1/Data Matrix грамматику следует вынести в отдельный parser library.
        val DATA_MATRIX_PATTERN = Regex("^01\\d{14}21.{1,}$")
    }
}

data class ProcessedProductCode(
    val normalizedCode: String,
    val formatValid: Boolean,
    val codeHash: String,
    val requestHash: String,
)
