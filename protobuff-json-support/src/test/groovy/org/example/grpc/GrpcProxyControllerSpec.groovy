package org.example.grpc

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(propertySources = ["grpc.rest.json.exposed=true"])
class GrpcProxyControllerSpec extends Specification {

    @Inject
    @Client("/")  // This instructs Micronaut to use the embedded server's base URL.
    HttpClient httpClient

    void "test sayHello via JSON proxy"() {
        given: "a JSON request with a name"
        def jsonRequest = '{"name": "Test"}'
        HttpRequest request = HttpRequest.POST("/grpc-json/greeter/sayHello", jsonRequest)
                .contentType(MediaType.APPLICATION_JSON)

        when: "the client sends the request"
        HttpResponse<String> response = httpClient.toBlocking().exchange(request, String)

        then: "response is OK with the expected greeting"
        response.status == HttpStatus.OK
        response.body().contains("Hello, Test")
    }
}
