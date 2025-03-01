package com.example.services

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

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
                HttpRequest.POST("/grpc-json/GreeterService/sayhello", requestBody)
                        .header("Content-Type", MediaType.APPLICATION_JSON),
                Map
        )

        then: "the response status is OK"
        response.status().code == 200

        and: "we get the expected JSON response"
        response.body().greeting == "Hello, YourName"
    }
}
