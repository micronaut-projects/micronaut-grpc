package com.example.services;

import io.micronaut.http.annotation.*;

@Controller("/demo")
public class DemoController {

    @Get(produces = "text/plain")
    public String index() {
        return "Example Response";
    }
}
