package helloworld

import groovy.transform.CompileStatic

import jakarta.inject.Singleton

@Singleton
@CompileStatic
class GreetingService {

    String sayHello(String name) {
        return "Hello $name"
    }
}
