package ru.example.productverification.reactive.infrastructure.registry

import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import ru.example.productverification.reactive.application.ProductRegistryClient

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductRegistryClientProperties::class)
class ProductRegistryClientConfiguration {

    @Bean
    @Profile("!test")
    fun productRegistryClient(
        webClientBuilder: WebClient.Builder,
        properties: ProductRegistryClientProperties,
    ): ProductRegistryClient {
        val connectTimeoutMillis = properties.connectTimeout
            .toMillis()
            .coerceIn(1, Int.MAX_VALUE.toLong())
            .toInt()
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
            .responseTimeout(properties.readTimeout)
        val webClient = webClientBuilder
            .baseUrl(properties.baseUrl.toString())
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()

        return HttpProductRegistryClient(
            webClient = webClient,
            properties = properties,
        )
    }
}
