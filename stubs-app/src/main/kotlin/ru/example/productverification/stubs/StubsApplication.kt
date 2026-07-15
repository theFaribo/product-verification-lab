package ru.example.productverification.stubs

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StubsApplication

fun main(args: Array<String>) {
    runApplication<StubsApplication>(*args)
}
