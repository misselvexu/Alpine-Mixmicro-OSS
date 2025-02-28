/*
 * This file is part of Alpine.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package alpine.server.filters;

import alpine.common.logging.Logger;
import alpine.model.ApiKey;
import alpine.model.LdapUser;
import alpine.model.ManagedUser;
import alpine.model.OidcUser;
import alpine.model.UserPrincipal;
import alpine.persistence.AlpineQueryManager;
import alpine.server.auth.PermissionRequired;
import org.glassfish.jersey.server.ContainerRequest;
import org.owasp.security.logging.SecurityMarkers;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.security.Principal;

/**
 * A filter that ensures that all principals making calls that are going
 * through this filter have the necessary permissions to do so.
 *
 * @see AuthorizationFeature
 * @author Steve Springett
 * @since 1.0.0
 */
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter {

    // Setup logging
    private static final Logger LOGGER = Logger.getLogger(AuthorizationFilter.class);

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (requestContext instanceof ContainerRequest) {

            final Principal principal = (Principal) requestContext.getProperty("Principal");
            if (principal == null) {
                LOGGER.info(SecurityMarkers.SECURITY_FAILURE, "A request was made without the assertion of a valid user principal");
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                return;
            }

            final PermissionRequired annotation = resourceInfo.getResourceMethod().getDeclaredAnnotation(PermissionRequired.class);
            final String[] permissions = annotation.value();

            try (AlpineQueryManager qm = new AlpineQueryManager()) {
                if (principal instanceof ApiKey) {
                    final ApiKey apiKey = (ApiKey)principal;
                    for (final String permission: permissions) {
                        if (qm.hasPermission(apiKey, permission)) {
                            return;
                        }
                    }
                    final String maskedApiKey = apiKey.getKey().replaceAll("\\w(?=\\w{4})", "*");
                    LOGGER.info(SecurityMarkers.SECURITY_FAILURE, "Unauthorized access attempt made by API Key "
                            + maskedApiKey + " to " + ((ContainerRequest) requestContext).getRequestUri().toString());
                } else {
                    UserPrincipal user = null;
                    if (principal instanceof ManagedUser) {
                        user = qm.getManagedUser(((ManagedUser) principal).getUsername());
                    } else if (principal instanceof LdapUser) {
                        user = qm.getLdapUser(((LdapUser) principal).getUsername());
                    } else if (principal instanceof OidcUser) {
                        user = qm.getOidcUser(((OidcUser) principal).getUsername());
                    }
                    if (user == null) {
                        LOGGER.info(SecurityMarkers.SECURITY_FAILURE, "A request was made but the system in unable to find the user principal");
                        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
                        return;
                    }
                    for (final String permission : permissions) {
                        if (qm.hasPermission(user, permission, true)) {
                            return;
                        }
                    }
                    LOGGER.info(SecurityMarkers.SECURITY_FAILURE, "Unauthorized access attempt made by "
                            + user.getUsername() + " to " + ((ContainerRequest) requestContext).getRequestUri().toString());
                }
            }
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
        }
    }

}
