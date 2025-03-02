package com.example.services;

import io.micronaut.http.annotation.*;
import io.micronaut.protobuf.json.GrpcProxyController;
import jakarta.inject.Inject;

@Controller("/demo")
public class DemoController {

    @Get(uri = "/", produces = "text/plain")
    public String index() {
        return "Example Response";
    }
}
