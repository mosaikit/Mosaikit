// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.account;

import dev.mosaikit.kernel.core.identity.RequestOrganization;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Self-registration and information about the signed-in account. */
@Path("/api/v1/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Accounts")
public class AccountResource {

    private final AccountService service;
    private final SecurityIdentity identity;

    public AccountResource(AccountService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @POST
    @Path("/registrations")
    @PermitAll
    @Operation(summary = "Register in an organization that allows self-registration")
    public Response register(@Valid @NotNull RegistrationRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(service.register(request))
                .build();
    }

    @GET
    @Path("/me")
    @Authenticated
    @Operation(summary = "Get the signed-in account")
    public AccountView me() {
        return service.get(identity.getPrincipal().getName(), identity.getRoles(), RequestOrganization.of(identity));
    }
}
