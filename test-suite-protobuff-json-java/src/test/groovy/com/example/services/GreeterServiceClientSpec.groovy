package com.example.services

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import spock.lang.Specification
import spock.lang.Unroll

@MicronautTest
class GreeterServiceClientSpec extends Specification {

    @Inject
    GreeterGrpc.GreeterBlockingStub client

    @Inject
    @Client("/")
    HttpClient httpClient

    @Unroll
    def "client should connect to running container service and say hello for #serviceName"() {
        given:
        def request = HelloRequest.newBuilder().setName("Micronaut").build()

        when:
        def response = client.sayHello(request)

        then:
        response.getGreeting().contains("Hello, Micronaut")

        where:
        serviceName << ["GreeterService"]
        //serviceName << ["GreeterService", "GreeterBlocking"]
    }

    @Unroll
    def "REST endpoint should successfully call #serviceName"() {
        given:
        def jsonRequest = '{"name": "Micronaut"}'

        when:
        def response = httpClient.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/${serviceName}/sayHello", jsonRequest)
                        .header("Content-Type", MediaType.APPLICATION_JSON)
                        .header("Accept", MediaType.APPLICATION_JSON),
                String
        )

        then:
        response.status() == HttpStatus.OK
        response.body().contains("Hello, Micronaut")

        where:
        serviceName << ["GreeterService"]
    }

    @Unroll
    def "REST endpoint should return 404 for non-existent service with #serviceName"() {
        given:
        def jsonRequest = '{"name": "Micronaut"}'

        when:
        httpClient.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/NonExistentService/sayHello", jsonRequest)
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                String
        )

        then:
        def e = thrown(Exception)
        e.response.status() == HttpStatus.NOT_FOUND

        where:
        serviceName << ["GreeterService"]
    }

    @Unroll
    def "REST endpoint should return 404 for non-existent method with #serviceName"() {
        given:
        def jsonRequest = '{"name": "Micronaut"}'

        when:
        httpClient.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/${serviceName}/nonExistentMethod", jsonRequest)
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                String
        )

        then:
        def e = thrown(Exception)
        e.response.status() == HttpStatus.NOT_FOUND

        where:
        serviceName << ["GreeterService"]
    }

    @Unroll
    def "REST endpoint should return 400 for invalid request format with #serviceName"() {
        given:
        def invalidJsonRequest = '{"invalid_field": "value"}'

        when:
        httpClient.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/${serviceName}/sayHello", invalidJsonRequest)
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                String
        )

        then:
        def e = thrown(Exception)
        e.response.status() == HttpStatus.BAD_REQUEST

        where:
        serviceName << ["GreeterService"]
    }
}
