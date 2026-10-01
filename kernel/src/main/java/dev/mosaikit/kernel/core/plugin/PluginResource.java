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
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Installed plugins: full view for the operator, frontend view for the shell. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Plugins")
public class PluginResource {

    private final PluginRegistry registry;
    private final boolean isolateUnverified;

    public PluginResource(PluginRegistry registry, KernelConfig config) {
        this.registry = registry;
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
}
