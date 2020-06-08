/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.grpc.server.tracing

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.grpc.HelloWordGrpcSpec
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

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
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)
        testBean.sayHello("Fred") == "Hello Fred"

        expect:
        conditions.eventually {
            myInterceptor.intercepted
            tracer.finishedSpans().size()  == 2
            tracer.finishedSpans()[0].operationName() == 'helloworld.Greeter/SayHello'
            tracer.finishedSpans()[1].operationName() == 'helloworld.Greeter/SayHello'
            tracer.finishedSpans().find { it.tags().get('span.kind') == 'client' }
            tracer.finishedSpans().find { it.tags().get('span.kind') == 'server' }
        }
    }


    @MockBean
    @Requires(property = "mock.tracer", value = "true")
    Tracer tracer() {
        return new MockTracer()
    }
}
