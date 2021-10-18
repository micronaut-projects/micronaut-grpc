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
package helloworld

// tag::imports[]
import groovy.transform.CompileStatic
import io.grpc.stub.StreamObserver
import jakarta.inject.Singleton
// end::imports[]


// tag::clazz[]
@CompileStatic
@Singleton
class GreetingEndpoint extends GreeterGrpc.GreeterImplBase { // <1>

    final GreetingService greetingService

    // <2>
    GreetingEndpoint(GreetingService greetingService) {
        this.greetingService = greetingService
    }

    @Override
    void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        // <3>
        HelloReply.newBuilder().with {
            message = greetingService.sayHello(request.name)
            responseObserver.onNext(build())
            responseObserver.onCompleted()
        }
    }
}
// end::clazz[]
