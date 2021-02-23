package io.micronaut.grpc.server.security.jwt

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject


@MicronautTest
class GrpcServerSecurityJwtConfigurationDisabledSpec extends Specification {

    @Inject
    private ApplicationContext applicationContext

    def "beans are not loaded"() {
        when:
        applicationContext.getBean(GrpcServerSecurityJwtConfiguration)

        then:
        thrown(NoSuchBeanException)

        when:
        applicationContext.getBean(GrpcServerSecurityJwtInterceptorFactory)

        then:
        thrown(NoSuchBeanException)

        when:
        applicationContext.getBean(GrpcServerSecurityJwtInterceptor)

        then:
        thrown(NoSuchBeanException)
    }

}