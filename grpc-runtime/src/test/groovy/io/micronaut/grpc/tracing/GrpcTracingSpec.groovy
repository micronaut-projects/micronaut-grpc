package io.micronaut.grpc.tracing

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.grpc.HelloWordGrpcSpec
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = 'mock.tracer', value = 'true')
class GrpcTracingSpec extends Specification {

    @Inject
    HelloWordGrpcSpec.TestBean testBean

    @Inject
    HelloWordGrpcSpec.MyInterceptor myInterceptor

    @Inject
    Tracer tracer

    void "test hello world grpc with tracing enabled"() {
        given:
        MockTracer tracer = tracer

        expect:
        testBean.sayHello("Fred") == "Hello Fred"
        myInterceptor.intercepted
        tracer.finishedSpans().size()  == 2
        tracer.finishedSpans()[0].operationName() == 'helloworld.Greeter/SayHello'
        tracer.finishedSpans()[1].operationName() == 'helloworld.Greeter/SayHello'
        tracer.finishedSpans().find { it.tags().get('span.kind') == 'client' }
        tracer.finishedSpans().find { it.tags().get('span.kind') == 'server' }
    }


    @MockBean
    @Requires(property = "mock.tracer", value = "true")
    Tracer tracer() {
        return new MockTracer()
    }
}
