package helloworld

import javax.inject.Singleton

@Singleton
class GreetingService {

    fun sayHello(name: String) : String {
        return "Hello $name"
    }
}