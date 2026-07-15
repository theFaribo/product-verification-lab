package ru.example.productverification.mvc

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal")
class PingController {

    @GetMapping("/ping")
    fun ping(): Map<String, String> =
        mapOf(
            "status" to "ok",
            "stack" to "spring-mvc-jpa",
        )
}
