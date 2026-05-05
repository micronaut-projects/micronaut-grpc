/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.grpc.server.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Evaluates Micronaut security intercept-url-map rules against gRPC method names.
 *
 * @since 5.0.0
 */
@Singleton
public final class InterceptUrlMapGrpcServerSecurityRule extends AbstractGrpcServerSecurityRule {

    private final List<CompiledInterceptUrlMapPattern> compiledPatterns;

    /**
     * @param securityConfiguration The Micronaut security configuration
     * @param rolesFinder The roles finder
     */
    public InterceptUrlMapGrpcServerSecurityRule(SecurityConfiguration securityConfiguration, RolesFinder rolesFinder) {
        super(rolesFinder);
        this.compiledPatterns = securityConfiguration.getInterceptUrlMap().stream()
            .map(CompiledInterceptUrlMapPattern::new)
            .toList();
    }

    @Override
    public <T, S> SecurityRuleResult check(ServerCall<T, S> serverCall,
                                           Metadata metadata,
                                           @Nullable Authentication authentication) {
        Optional<CompiledInterceptUrlMapPattern> matchedPattern = compiledPatterns.stream()
            .filter(interceptUrlMapPattern -> interceptUrlMapPattern.matches(serverCall.getMethodDescriptor().getFullMethodName()))
            .findFirst();
        if (matchedPattern.isEmpty()) {
            return SecurityRuleResult.UNKNOWN;
        }
        return compareRoles(matchedPattern.get().interceptUrlMapPattern().getAccess(), getRoles(authentication));
    }

    private record CompiledInterceptUrlMapPattern(InterceptUrlMapPattern interceptUrlMapPattern, Pattern pattern) {

        private CompiledInterceptUrlMapPattern(InterceptUrlMapPattern interceptUrlMapPattern) {
            this(interceptUrlMapPattern, Pattern.compile(interceptUrlMapPattern.getPattern()));
        }

        private boolean matches(String methodName) {
            return pattern.matcher(methodName).matches();
        }
    }
}
