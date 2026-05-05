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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Base support for gRPC server security rules.
 *
 * @since 5.0.0
 */
public abstract class AbstractGrpcServerSecurityRule implements GrpcServerSecurityRule {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGrpcServerSecurityRule.class);

    private final RolesFinder rolesFinder;

    /**
     * @param rolesFinder The roles finder
     */
    protected AbstractGrpcServerSecurityRule(RolesFinder rolesFinder) {
        this.rolesFinder = rolesFinder;
    }

    /**
     * Resolves the granted roles for the current authentication.
     *
     * @param authentication The current authentication
     * @return The granted roles
     */
    protected List<String> getRoles(@Nullable Authentication authentication) {
        List<String> roles = new ArrayList<>();
        if (authentication == null) {
            roles.add(SecurityRule.IS_ANONYMOUS);
        } else {
            roles.addAll(rolesFinder.resolveRoles(authentication.getAttributes()));
            roles.add(SecurityRule.IS_ANONYMOUS);
            roles.add(SecurityRule.IS_AUTHENTICATED);
        }
        return roles;
    }

    /**
     * Compares required and granted roles.
     *
     * @param requiredRoles The required roles
     * @param grantedRoles The granted roles
     * @return The security rule result
     */
    protected SecurityRuleResult compareRoles(List<String> requiredRoles, List<String> grantedRoles) {
        if (rolesFinder.hasAnyRequiredRoles(requiredRoles, grantedRoles)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("The given roles [{}] matched one or more of the required roles [{}]. Allowing the request.", grantedRoles, requiredRoles);
            }
            return SecurityRuleResult.ALLOWED;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("None of the given roles [{}] matched the required roles [{}]. Rejecting the request.", grantedRoles, requiredRoles);
        }
        return SecurityRuleResult.REJECTED;
    }
}
