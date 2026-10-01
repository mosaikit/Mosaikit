// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.system;

import dev.mosaikit.kernel.core.plugin.PluginRegistry;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Public information used by the shell before sign-in. */
@Path("/api/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "System")
public class SystemResource {

    private static final String PRODUCT_NAME = "Mosaikit";

    private final KernelVersion kernelVersion;
    private final PluginRegistry plugins;

    public SystemResource(KernelVersion kernelVersion, PluginRegistry plugins) {
        this.kernelVersion = kernelVersion;
        this.plugins = plugins;
    }

    @GET
    @Path("/info")
    @PermitAll
    @Operation(summary = "Get product name, kernel version and number of active plugins")
    public SystemInfo info() {
        return new SystemInfo(
                PRODUCT_NAME, kernelVersion.get().toString(), plugins.active().size());
    }
}
