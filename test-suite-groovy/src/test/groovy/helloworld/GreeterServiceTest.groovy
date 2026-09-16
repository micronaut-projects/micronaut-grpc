package helloworld

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class GreeterServiceTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "test JSON over HTTP"() {
        when:
        String response = client.toBlocking().retrieve(
            HttpRequest.POST("/grpc-json/GreeterService/sayHello", '{"name": "YourName"}')
                .contentType(MediaType.APPLICATION_JSON)
        )

        then:
        response.contains('"greeting": "Hello, YourName"')
    }
}
