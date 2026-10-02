// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.plugin;

import dev.mosaikit.kernel.core.config.KernelConfig;
import dev.mosaikit.kernel.core.security.Roles;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Installed plugins: full view for the operator, frontend view for the shell. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Plugins")
public class PluginResource {

    private final PluginRegistry registry;
    private final boolean isolateUnverified;
    private final boolean watched;

    public PluginResource(PluginRegistry registry, KernelConfig config) {
        this.registry = registry;
        this.watched = config.plugins().watch();
        this.isolateUnverified =
                FrontendPluginView.isolatesUnverified(config.plugins().unverifiedFrontends());
    }

    @GET
    @Path("/plugins")
    @RolesAllowed(Roles.PLATFORM_ADMIN)
    @Operation(summary = "List installed plugins with their status")
    public List<PluginView> list() {
        return registry.all().stream().map(PluginView::of).toList();
    }

    @GET
    @Path("/shell/plugins")
    @Authenticated
    @Operation(summary = "List the frontends the shell must load")
    public List<FrontendPluginView> shellPlugins() {
        return registry.active().stream()
                .flatMap(plugin -> plugin.manifest().stream()
                        .flatMap(manifest -> manifest.frontend().stream()
                                .map(frontend -> FrontendPluginView.of(
                                        manifest, frontend, isolateUnverified && !plugin.isVerified()))))
                .toList();
    }

    @GET
    @Path("/shell/plugins/revision")
    @Authenticated
    @Operation(
            summary = "Get the revision of the installed plugins, when the plugins directory is watched",
            description = "404 when it is not watched (mosaikit.plugins.watch). The shell reloads its page when"
                    + " the revision changes.")
    public Response revision() {
        if (!watched) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Map.of("revision", registry.revision())).build();
    }
}
