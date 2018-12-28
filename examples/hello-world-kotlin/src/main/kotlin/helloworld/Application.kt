package helloworld

import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build()
                .packages("helloworld")
                .mainClass(Application.javaClass)
                .start()
    }
}