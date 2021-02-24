package io.micronaut.grpc.server.security.jwt

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor
import spock.lang.Specification

class GrpcServerSecurityJwtConfigurationDisabledSpec extends Specification {

    def "beans are not loaded when security not enabled"() {
        given:
        def config = [
                "micronaut.security.enabled": false,
                "micronaut.security.token.enabled": true,
                "micronaut.security.token.jwt.enabled": true,
                "grpc.server.security.token.jwt.enabled": true
        ]
        def context = ApplicationContext.run(config)

        when:
        context.getBean(GrpcServerSecurityJwtConfiguration)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptorFactory)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptor)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }

    def "beans are not loaded when security token not enabled"() {
        given:
        def config = [
                "micronaut.security.enabled": true,
                "micronaut.security.token.enabled": false,
                "micronaut.security.token.jwt.enabled": true,
                "grpc.server.security.token.jwt.enabled": true
        ]
        def context = ApplicationContext.run(config)

        when:
        context.getBean(GrpcServerSecurityJwtConfiguration)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptorFactory)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptor)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }

    def "beans are not loaded when security token jwt not enabled"() {
        given:
        def config = [
                "micronaut.security.enabled": true,
                "micronaut.security.token.enabled": true,
                "micronaut.security.token.jwt.enabled": false,
                "grpc.server.security.token.jwt.enabled": true
        ]
        def context = ApplicationContext.run(config)

        when:
        context.getBean(GrpcServerSecurityJwtConfiguration)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptorFactory)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptor)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }

    def "beans are not loaded when grpc security jwt not enabled"() {
        given:
        def config = [
                "micronaut.security.enabled": true,
                "micronaut.security.token.enabled": true,
                "micronaut.security.token.jwt.enabled": true,
                "grpc.server.security.token.jwt.enabled": false
        ]
        def context = ApplicationContext.run(config)

        when:
        context.getBean(GrpcServerSecurityJwtConfiguration)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptorFactory)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(GrpcServerSecurityJwtInterceptor)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }

}