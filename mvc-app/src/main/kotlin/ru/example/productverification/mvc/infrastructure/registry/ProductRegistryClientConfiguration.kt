package ru.example.productverification.mvc.infrastructure.registry

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import ru.example.productverification.mvc.application.ProductRegistryClient
import java.net.http.HttpClient

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductRegistryClientProperties::class)
class ProductRegistryClientConfiguration {

    @Bean
    @Profile("!test")
    fun productRegistryClient(
        restClientBuilder: RestClient.Builder,
        properties: ProductRegistryClientProperties,
    ): ProductRegistryClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.readTimeout)
        }
        val restClient = restClientBuilder
            .baseUrl(properties.baseUrl.toString())
            .requestFactory(requestFactory)
            .build()

        return HttpProductRegistryClient(
            restClient = restClient,
            properties = properties,
        )
    }
}
