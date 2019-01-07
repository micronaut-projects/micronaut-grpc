package io.micronaut

import groovy.transform.CompileStatic
import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.web.router.DefaultRouteBuilder

import javax.inject.Inject
import javax.inject.Singleton

@CompileStatic
@Singleton
class ProgramaticControllerCreator extends DefaultRouteBuilder {
    ProgramaticControllerCreator(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        super(executionHandleLocator, uriNamingStrategy)
    }

    @Inject
    void issuesRoutes(ProgramaticController controller) {
        GET("/town", controller, "city")
    }
}
