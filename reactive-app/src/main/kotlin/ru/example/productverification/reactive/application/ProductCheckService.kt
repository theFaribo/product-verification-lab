package ru.example.productverification.reactive.application

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import ru.example.productverification.domain.decision.ProductCheckDecisionEngine
import ru.example.productverification.domain.model.CirculationStatus
import ru.example.productverification.domain.model.ProductCheckDecision
import ru.example.productverification.domain.model.ProductCheckInput
import ru.example.productverification.domain.model.RegistryLookup
import ru.example.productverification.domain.model.RestrictionsLookup
import ru.example.productverification.domain.model.SignatureStatus
import ru.example.productverification.reactive.persistence.ProductCheckPersistenceService
import ru.example.productverification.reactive.persistence.ProductCheckRowMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class ProductCheckService(
    private val codeProcessor: ProductCodeProcessor,
    private val signatureVerifier: SignatureVerifier,
    private val productRegistryClient: ProductRegistryClient,
    private val restrictionsProvider: ProductRestrictionsProvider,
    private val decisionEngine: ProductCheckDecisionEngine,
    private val persistenceService: ProductCheckPersistenceService,
    private val rowMapper: ProductCheckRowMapper,
    private val clock: Clock,
) {

    suspend fun check(command: CreateProductCheckCommand): ProductCheckExecution {
        val processedCode = codeProcessor.process(
            rawCode = command.code,
            scanSource = command.scanSource,
            regionCode = command.regionCode,
        )

        persistenceService.findByIdempotencyKey(
            clientId = command.clientId,
            idempotencyKey = command.idempotencyKey,
        )?.let { existing ->
            return replayOrThrowConflict(
                existingRequestHash = existing.requestHash,
                actualRequestHash = processedCode.requestHash,
                existingRecord = rowMapper.toRecord(existing),
            )
        }

        val startedAtNanos = System.nanoTime()
        val checkedAt = clock.instant()
        val decision = executeVerification(
            processedCode = processedCode,
            checkedAt = checkedAt,
        )
        val durationMs = TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - startedAtNanos,
        )

        val row = rowMapper.toRow(
            id = UUID.randomUUID(),
            clientId = command.clientId,
            idempotencyKey = command.idempotencyKey,
            requestHash = processedCode.requestHash,
            codeHash = processedCode.codeHash,
            decision = decision,
            checkedAt = checkedAt,
            durationMs = durationMs,
            createdAt = clock.instant(),
        )

        return try {
            val saved = persistenceService.insert(row)
            ProductCheckExecution(
                record = rowMapper.toRecord(saved),
                created = true,
            )
        } catch (exception: DataIntegrityViolationException) {
            val winner = persistenceService.findByIdempotencyKey(
                clientId = command.clientId,
                idempotencyKey = command.idempotencyKey,
            ) ?: throw exception

            replayOrThrowConflict(
                existingRequestHash = winner.requestHash,
                actualRequestHash = processedCode.requestHash,
                existingRecord = rowMapper.toRecord(winner),
            )
        }
    }

    suspend fun get(id: UUID): ProductCheckRecord =
        persistenceService.findById(id)
            ?.let(rowMapper::toRecord)
            ?: throw ProductCheckNotFoundException(id)

    private suspend fun executeVerification(
        processedCode: ProcessedProductCode,
        checkedAt: Instant,
    ): ProductCheckDecision {
        if (!processedCode.formatValid) {
            return decisionEngine.decide(
                ProductCheckInput(
                    codeFormatValid = false,
                    checkedAt = checkedAt,
                ),
            )
        }

        val signatureStatus = signatureVerifier.verify(processedCode.normalizedCode)
        if (signatureStatus != SignatureStatus.VALID) {
            return decisionEngine.decide(
                ProductCheckInput(
                    codeFormatValid = true,
                    signatureStatus = signatureStatus,
                    checkedAt = checkedAt,
                ),
            )
        }

        return coroutineScope {
            val localProductDeferred = async {
                persistenceService.findLocalProduct(processedCode.codeHash)
            }
            val registryLookupDeferred = async {
                productRegistryClient.findProduct(processedCode.codeHash)
            }

            val registryLookup = registryLookupDeferred.await()
            val restrictionsLookupDeferred = async {
                restrictionsFor(registryLookup)
            }

            decisionEngine.decide(
                ProductCheckInput(
                    codeFormatValid = true,
                    signatureStatus = SignatureStatus.VALID,
                    localProduct = localProductDeferred.await(),
                    registryLookup = registryLookup,
                    restrictionsLookup = restrictionsLookupDeferred.await(),
                    checkedAt = checkedAt,
                ),
            )
        }
    }

    private suspend fun restrictionsFor(
        registryLookup: RegistryLookup,
    ): RestrictionsLookup =
        when (registryLookup) {
            is RegistryLookup.Found -> {
                when (registryLookup.product.circulationStatus) {
                    CirculationStatus.SOLD,
                    CirculationStatus.WITHDRAWN -> RestrictionsLookup.NotRequested

                    CirculationStatus.IN_CIRCULATION ->
                        restrictionsProvider.findRestrictions(
                            registryLookup.product.identity.gtin,
                        )
                }
            }

            RegistryLookup.NotFound,
            RegistryLookup.NotRequested,
            is RegistryLookup.Unavailable -> RestrictionsLookup.NotRequested
        }

    private fun replayOrThrowConflict(
        existingRequestHash: String,
        actualRequestHash: String,
        existingRecord: ProductCheckRecord,
    ): ProductCheckExecution {
        if (existingRequestHash != actualRequestHash) {
            throw IdempotencyConflictException()
        }

        return ProductCheckExecution(
            record = existingRecord,
            created = false,
        )
    }
}
