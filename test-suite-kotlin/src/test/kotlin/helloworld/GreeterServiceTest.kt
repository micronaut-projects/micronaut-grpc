package helloworld

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest
class GreeterServiceTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun testJsonOverHttp() {
        val response = client.toBlocking().retrieve(
            HttpRequest.POST("/grpc-json/GreeterService/sayHello", """{"name": "YourName"}""")
                .contentType(MediaType.APPLICATION_JSON)
        )
        assertTrue(response.contains("\"greeting\": \"Hello, YourName\""), response)
    }
}
