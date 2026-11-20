from typing import Annotated

from jakarta.inject import Inject
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class GreeterServiceTest:

    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_json_over_http(self):
        response = self.client.toBlocking().retrieve(
            HttpRequest.POST("/grpc-json/GreeterService/sayHello", '{"name": "YourName"}')
                .contentType(MediaType.APPLICATION_JSON)
        )
        assert '"greeting": "Hello, YourName"' in response
