package helloworld

import groovy.transform.CompileStatic

import javax.inject.Singleton

@Singleton
@CompileStatic
class GreetingService {

    String sayHello(String name) {
        return "Hello $name"
    }
}
