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
package io.micronaut

import groovy.transform.CompileStatic
import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.web.router.DefaultRouteBuilder

import jakarta.inject.Inject
import jakarta.inject.Singleton

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
