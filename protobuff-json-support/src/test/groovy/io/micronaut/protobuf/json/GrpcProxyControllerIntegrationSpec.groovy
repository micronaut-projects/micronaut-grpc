package io.micronaut.protobuf.json

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(environments = ["test"])
class GrpcProxyControllerIntegrationSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    def "Invoke GrpcProxyController via JSON HTTP request and verify correct JSON response"() {
        given: "JSON request payload and correct service & method names"
        String jsonPayload = '{"name":"Micronaut Integration"}'

        String serviceName = "GreeterService"
        String methodName = "sayHello"

        String requestUri = "/grpc-json/$serviceName/$methodName"

        when: "HTTP POST request to GrpcProxyController endpoint"
        String jsonResponse = httpClient.toBlocking()
                .retrieve(HttpRequest.POST(requestUri, jsonPayload))

        then: "we receive correct and properly formatted JSON greeting response"
        jsonResponse == '{\n' +
                '  "greeting": "TEST Hello, Micronaut Integration"\n' +
                '}'
    }
}
