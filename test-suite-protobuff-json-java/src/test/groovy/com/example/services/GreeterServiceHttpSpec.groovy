package com.example.services

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

@MicronautTest
class GreeterServiceHttpSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "test REST JSON endpoint for gRPC service"() {
        given: "a JSON request body"
        def requestBody = [name: "YourName"]

        when: "we call the REST endpoint"
        def response = client.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/GreeterService/sayHello", requestBody)
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                Map
        )

        then: "the response status is OK"
        response.status().code == 200
        response.body().greeting == "Hello, YourName"
    }

    void "test custom endpoint override"() {
        given: "a JSON request body"
        def requestBody = [name: "CustomEndpoint"]

        when: "we call the custom endpoint"
        def response = client.toBlocking().exchange(
                HttpRequest.POST("/custom-endpoint/GreeterService/sayHello", requestBody)
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                Map
        )

        then: "the request should fail with 404"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }

    void "test non-JSON request fails"() {
        when: "we send a non-JSON request"
        client.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/GreeterService/sayHello", "plain text content")
                        .header("Content-Type", MediaType.TEXT_PLAIN),
                Map
        )

        then: "request should fail with unsupported media type"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE
    }

    void "test malformed JSON request"() {
        given: "an invalid JSON request body"
        def requestBody = [wrongField: "value"]

        when: "we call the endpoint with invalid JSON"
        client.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/GreeterService/sayHello", requestBody)
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                Map
        )

        then: "request should fail with bad request"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    @Unroll
    void "test invalid URL patterns: #scenario"() {
        when: "we call with invalid URL"
        client.toBlocking().exchange(
                HttpRequest.POST(url, [name: "test"])
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                Map
        )

        then: "request should fail with not found"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND

        where:
        scenario           | url
        "empty service"    | "/grpc-json//sayHello"
        "empty method"     | "/grpc-json/GreeterService/"
        "missing both"     | "/grpc-json/"
    }

    void "test empty POST body"() {
        when: "we send an empty POST body"
        client.toBlocking().exchange(
                HttpRequest.POST("/grpc-json/GreeterService/sayHello", "")
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                Map
        )

        then: "request should fail with bad request"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    void "test GET method not allowed"() {
        when: "we try to use GET instead of POST"
        client.toBlocking().exchange(
                HttpRequest.GET("/grpc-json/GreeterService/sayHello"),
                Map
        )

        then: "request should fail with method not allowed"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.METHOD_NOT_ALLOWED
    }
}
