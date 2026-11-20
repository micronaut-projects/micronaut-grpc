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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates the {@code grpc.server.security.intercept-method-map} rules against fully qualified gRPC method names.
 *
 * <p>The HTTP {@code micronaut.security.intercept-url-map} is intentionally not used, because its entries are
 * Ant-style HTTP path patterns that do not apply to gRPC method names.</p>
 *
 * @since 5.3.0
 */
@Singleton
@Requires(beans = GrpcServerSecurityConfiguration.class)
public final class InterceptMethodMapGrpcServerSecurityRule extends AbstractGrpcServerSecurityRule {

    private final List<CompiledPattern> compiledPatterns;

    /**
     * @param grpcServerSecurityConfiguration The gRPC security configuration
     * @param rolesFinder The roles finder
     */
    public InterceptMethodMapGrpcServerSecurityRule(GrpcServerSecurityConfiguration grpcServerSecurityConfiguration,
                                                    RolesFinder rolesFinder) {
        super(rolesFinder);
        List<InterceptMethodMapPattern> patterns = grpcServerSecurityConfiguration.getInterceptMethodMap();
        this.compiledPatterns = patterns == null ? List.of() : patterns.stream()
            .map(CompiledPattern::compile)
            .toList();
    }

    @Override
    public <T, S> SecurityRuleResult check(ServerCall<T, S> serverCall,
                                           Metadata metadata,
                                           @Nullable Authentication authentication) {
        String methodName = serverCall.getMethodDescriptor().getFullMethodName();
        for (CompiledPattern compiledPattern : compiledPatterns) {
            if (compiledPattern.matches(methodName)) {
                return compareRoles(compiledPattern.access(), getRoles(authentication));
            }
        }
        return SecurityRuleResult.UNKNOWN;
    }

    private record CompiledPattern(Pattern pattern, List<String> access) {

        private static CompiledPattern compile(InterceptMethodMapPattern interceptMethodMapPattern) {
            try {
                return new CompiledPattern(Pattern.compile(interceptMethodMapPattern.pattern()), interceptMethodMapPattern.access());
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException("Invalid regular expression [" + interceptMethodMapPattern.pattern()
                    + "] in " + GrpcServerSecurityConfiguration.PREFIX + ".intercept-method-map: " + e.getDescription(), e);
            }
        }

        private boolean matches(String methodName) {
            return pattern.matcher(methodName).matches();
        }
    }
}
