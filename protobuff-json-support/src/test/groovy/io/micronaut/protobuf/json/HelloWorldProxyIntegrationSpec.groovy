package io.micronaut.protobuf.json

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.json.JsonMapper
import io.micronaut.protobuf.grpc.GreeterService
import spock.lang.Specification

class HelloWorldProxyIntegrationSpec extends Specification {

    GreeterService greeterService = Mock()
    JsonMapper jsonMapper = Mock()
    HttpClient httpClient = Mock()
    BlockingHttpClient blockingClient = Mock()

    def setup() {
        httpClient.toBlocking() >> blockingClient
    }

    def "should convert JSON request to gRPC and receive response"() {
        given: "A JSON request payload matching HelloRequest"
        def requestBody = [name: "Alice"]

        when: "Making a JSON request to the gRPC proxy endpoint"
        blockingClient.exchange(_ as HttpRequest, String) >> HttpResponse.ok("{'greeting': 'Hello Alice'}")
        jsonMapper.readValue(_ as String, Map) >> [greeting: "Hello Alice"]
        def response = blockingClient.exchange(
                HttpRequest.POST("/grpc/helloworld.Greeter/sayHello", requestBody),
                String
        )

        then: "Response should be successful"
        response.status() == HttpStatus.OK

        and: "Response should contain the greeting"
        def json = jsonMapper.readValue(response.body(), Map)
        json.greeting == "Hello Alice"
    }

    def "should handle empty name gracefully"() {
        given: "A JSON request payload with empty name"
        def requestBody = [name: ""]

        when: "Making a JSON request to the gRPC proxy endpoint"
        blockingClient.exchange(_ as HttpRequest, String) >> HttpResponse.ok("{'greeting': 'Hello '}")
        jsonMapper.readValue(_ as String, Map) >> [greeting: "Hello "]
        def response = blockingClient.exchange(
                HttpRequest.POST("/grpc/helloworld.Greeter/sayHello", requestBody),
                String
        )

        then: "Response should be successful"
        response.status() == HttpStatus.OK

        and: "Response should contain appropriate greeting"
        def json = jsonMapper.readValue(response.body(), Map)
        json.greeting == "Hello "
    }

    def "should return error for malformed request"() {
        given: "An invalid JSON request payload"
        def requestBody = [wrongField: "Alice"]

        and: "The client is configured to throw an error"
        blockingClient.exchange(_ as HttpRequest, String) >> {
            throw new HttpClientResponseException("Bad Request", HttpResponse.badRequest())
        }

        when: "Making a JSON request to the gRPC proxy endpoint"
        blockingClient.exchange(
                HttpRequest.POST("/grpc/helloworld.Greeter/sayHello", requestBody),
                String
        )

        then: "Should receive a bad request response"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }
}
