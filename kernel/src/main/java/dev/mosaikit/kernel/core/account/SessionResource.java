// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.account;

import dev.mosaikit.kernel.core.identity.RequestOrganization;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The session of the shell for local accounts. {@code POST /api/v1/accounts/session} with the form
 * fields {@code username} and {@code password} is answered by the form authentication of Quarkus,
 * with the same {@link LocalIdentityProvider} as HTTP Basic: it sets an encrypted, HttpOnly cookie,
 * so that a reload of the page keeps the session and no script of the page, plugins included, ever
 * holds the password. API and MCP clients keep using HTTP Basic or bearer tokens.
 */
@Path("/api/v1/accounts/session")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Accounts")
public class SessionResource {

    /** The session cookie; {@code quarkus.http.auth.form.cookie-name} must have this value. */
    public static final String COOKIE = "mosaikit-session";

    private final AccountService service;
    private final SecurityIdentity identity;
    private final String cookie;

    public SessionResource(
            AccountService service,
            SecurityIdentity identity,
            @ConfigProperty(name = "quarkus.http.auth.form.cookie-name") String cookie) {
        this.service = service;
        this.identity = identity;
        this.cookie = cookie;
    }

    @GET
    @PermitAll
    @Operation(summary = "Get the account of the current session, if any")
    public Response current() {
        if (identity.isAnonymous()) {
            return Response.noContent().build();
        }
        return Response.ok(service.get(
                        identity.getPrincipal().getName(), identity.getRoles(), RequestOrganization.of(identity)))
                .build();
    }

    @DELETE
    @PermitAll
    @Operation(summary = "End the session of the shell")
    public Response end() {
        NewCookie expired = new NewCookie.Builder(cookie)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
        return Response.noContent().cookie(expired).build();
    }
}
