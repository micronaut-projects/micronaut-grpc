/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * gRPC Method Security Rule which uses {@link SecurityConfiguration#getInterceptUrlMap()} to check rules.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
@Singleton
public class InterceptUrlMapGrpcServerSecurityRule extends AbstractGrpcServerSecurityRule {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptUrlMapGrpcServerSecurityRule.class);
    private final SecurityConfiguration securityConfiguration;

    /**
     * Constructs an instance of this rule with the provided configuration and roles finder.
     *
     * @param securityConfiguration the security configuration
     * @param rolesFinder the roles finder
     */
    @Inject
    public InterceptUrlMapGrpcServerSecurityRule(final SecurityConfiguration securityConfiguration, final RolesFinder rolesFinder) {
        super(rolesFinder);
        this.securityConfiguration = securityConfiguration;
    }

    /**
     * Run the security rule check.
     *
     * @param serverCall the server call
     * @param metadata the metadata
     * @param claims the claims
     * @param <T> the type of the server call request
     * @param <S> the type of the server call response
     * @return the security rule result.
     */
    @Override
    public <T, S> SecurityRuleResult check(final ServerCall<T, S> serverCall, final Metadata metadata, @Nullable final Map<String, Object> claims) {
        if (CollectionUtils.isEmpty(securityConfiguration.getInterceptUrlMap())) {
            return SecurityRuleResult.UNKNOWN;
        }
        final String fullMethodName = serverCall.getMethodDescriptor().getFullMethodName();
        final Optional<InterceptUrlMapPattern> matchedPattern = securityConfiguration.getInterceptUrlMap().stream()
                .filter(interceptUrlMapPattern -> serverCall.getMethodDescriptor().getFullMethodName().matches(interceptUrlMapPattern.getPattern()))
                .findFirst();
        if (matchedPattern.isPresent()) {
            return compareRoles(matchedPattern.get().getAccess(), getRoles(claims));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("No intercept map patterns match for method [{}]. Returning UNKNOWN.", fullMethodName);
        }
        return SecurityRuleResult.UNKNOWN;
    }

}
