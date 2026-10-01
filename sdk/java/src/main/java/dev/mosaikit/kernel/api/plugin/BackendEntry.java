// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.plugin;

import java.util.Objects;

/**
 * The Java code of a plugin. The launcher adds the JAR to the kernel and rebuilds it at the next
 * start (see ADR-0004); code that is not declared here is never loaded.
 *
 * @param jar path of the JAR, relative to the plugin root
 * @param api name of the plugin API: its REST resources live under {@code /api/v1/p/<api>/},
 *     which the kernel serves only while the plugin is active
 */
public record BackendEntry(String jar, String api) {

    public BackendEntry {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(api, "api");
    }
}
