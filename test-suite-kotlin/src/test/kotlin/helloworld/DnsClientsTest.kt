package helloworld

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "DnsClientsTest")
@MicronautTest
class DnsClientsTest {

    @Inject
    lateinit var greeterStub: GreeterGrpc.GreeterStub

    @Test
    fun testDnsTarget() {
        assertEquals("greeter", greeterStub.channel.authority())
    }
}
