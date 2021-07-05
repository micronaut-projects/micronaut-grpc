package helloworld

import jakarta.inject.Singleton

@Singleton
class GreetingService {

    fun sayHello(name: String) : String {
        return "Hello $name"
    }
}