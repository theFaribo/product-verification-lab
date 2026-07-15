package ru.example.productverification.stubs.registry

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/stub/v1/registry")
class RegistryStubController(
    private val registryStubService: RegistryStubService,
) {

    @GetMapping("/items/{codeHash}")
    suspend fun findItem(
        @PathVariable codeHash: String,
        @RequestParam(defaultValue = "SUCCESS") scenario: RegistryScenario,
        @RequestParam(required = false) delayMs: Long?,
    ): ResponseEntity<RegistryItemResponse> {
        val execution = registryStubService.findItem(
            codeHash = codeHash,
            scenario = scenario,
            requestedDelayMs = delayMs,
        )

        return ResponseEntity.ok()
            .header(STUB_SCENARIO_HEADER, scenario.name)
            .header(STUB_DELAY_HEADER, execution.effectiveDelayMs.toString())
            .body(execution.response)
    }

    private companion object {
        const val STUB_SCENARIO_HEADER = "X-Stub-Scenario"
        const val STUB_DELAY_HEADER = "X-Stub-Delay-Ms"
    }
}
