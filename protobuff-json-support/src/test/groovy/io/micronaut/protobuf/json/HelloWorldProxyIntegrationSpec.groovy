package io.micronaut.protobuf.json

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.json.JsonMapper
import spock.lang.Specification

class HelloWorldProxyIntegrationSpec extends Specification {

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
    def "should handle null name field gracefully"() {
        given: "A JSON request payload with null name"
        def requestBody = [name: null]

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

    def "should return error for invalid service name"() {
        given: "A valid JSON request payload but invalid service name"
        def requestBody = [name: "Alice"]

        and: "The client is configured to throw an error"
        blockingClient.exchange(_ as HttpRequest, String) >> {
            throw new HttpClientResponseException("Not Found", HttpResponse.notFound())
        }

        when: "Making a JSON request to the gRPC proxy endpoint with wrong service"
        blockingClient.exchange(
                HttpRequest.POST("/grpc/helloworld.WrongService/sayHello", requestBody),
                String
        )

        then: "Should receive a not found response"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    def "should return error for invalid method name"() {
        given: "A valid JSON request payload but invalid method name"
        def requestBody = [name: "Alice"]

        and: "The client is configured to throw an error"
        blockingClient.exchange(_ as HttpRequest, String) >> {
            throw new HttpClientResponseException("Not Found", HttpResponse.notFound())
        }

        when: "Making a JSON request to the gRPC proxy endpoint with wrong method"
        blockingClient.exchange(
                HttpRequest.POST("/grpc/helloworld.Greeter/wrongMethod", requestBody),
                String
        )

        then: "Should receive a not found response"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    def "should handle special characters in name"() {
        given: "A JSON request payload with special characters"
        def requestBody = [name: "Alice & Bob <> !@#\$%^"]

        when: "Making a JSON request to the gRPC proxy endpoint"
        blockingClient.exchange(_ as HttpRequest, String) >> HttpResponse.ok("{'greeting': 'Hello Alice & Bob <> !@#\$%^'}")
        jsonMapper.readValue(_ as String, Map) >> [greeting: "Hello Alice & Bob <> !@#\$%^"]
        def response = blockingClient.exchange(
                HttpRequest.POST("/grpc/helloworld.Greeter/sayHello", requestBody),
                String
        )

        then: "Response should be successful"
        response.status() == HttpStatus.OK

        and: "Response should contain the greeting with special characters"
        def json = jsonMapper.readValue(response.body(), Map)
        json.greeting == "Hello Alice & Bob <> !@#\$%^"
    }

    def "should return error for malformed JSON"() {
        given: "A malformed JSON request"
        def requestBody = "{ invalid json }"

        and: "The client is configured to throw an error"
        blockingClient.exchange(_ as HttpRequest, String) >> {
            throw new HttpClientResponseException("Bad Request", HttpResponse.badRequest())
        }

        when: "Making a request with malformed JSON"
        blockingClient.exchange(
                HttpRequest.POST("/grpc/helloworld.Greeter/sayHello", requestBody),
                String
        )

        then: "Should receive a bad request response"
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }
}
