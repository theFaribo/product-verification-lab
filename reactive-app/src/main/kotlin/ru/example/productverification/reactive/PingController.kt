package ru.example.productverification.reactive

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal")
class PingController {

    @GetMapping("/ping")
    suspend fun ping(): Map<String, String> =
        mapOf(
            "status" to "ok",
            "stack" to "webflux-r2dbc-coroutines",
        )
}
