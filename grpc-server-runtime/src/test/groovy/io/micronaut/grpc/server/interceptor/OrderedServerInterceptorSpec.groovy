package io.micronaut.grpc.server.interceptor

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import spock.lang.Specification

class OrderedServerInterceptorSpec extends Specification {

    def "test interceptCall"() {
        given:
        ServerInterceptor mockDelegate = Mock()
        OrderedServerInterceptor orderedServerInterceptor = new OrderedServerInterceptor(mockDelegate, 1)

        ServerCall mockServerCall = Mock()
        Metadata metadata = new Metadata()
        ServerCallHandler mockServerCallHandler = Mock()

        ServerCall.Listener returnedListener = new ServerCall.Listener() {}

        when:
        ServerCall.Listener listener = orderedServerInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler)

        then:
        1 * mockDelegate.interceptCall(mockServerCall, metadata, mockServerCallHandler) >> returnedListener
        0 * _

        and:
        listener == returnedListener
    }

    def "test getOrder"() {
        given:
        int order = 10
        OrderedServerInterceptor orderedServerInterceptor = new OrderedServerInterceptor(Mock(ServerInterceptor), 10)

        expect:
        orderedServerInterceptor.order == order
    }

}
