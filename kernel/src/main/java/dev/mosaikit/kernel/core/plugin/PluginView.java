// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.plugin;

import dev.mosaikit.kernel.api.plugin.PluginKind;
import dev.mosaikit.kernel.api.plugin.PluginManifest;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Administrative view of an installed plugin.
 *
 * @param publisherKey identifier of the trusted key that signed the package, {@code null} when the
 *     publisher is not verified (MK-013)
 */
public record PluginView(
        String id,
        String version,
        String name,
        String description,
        Set<String> kinds,
        String platform,
        PluginStatus status,
        List<String> problems,
        String publisherKey) {

    static PluginView of(InstalledPlugin plugin) {
        return plugin.manifest()
                .map(manifest -> fromManifest(manifest, plugin))
                .orElseGet(() -> new PluginView(
                        plugin.key(),
                        null,
                        plugin.key(),
                        "",
                        Set.of(),
                        null,
                        plugin.status(),
                        plugin.problems(),
                        null));
    }

    private static PluginView fromManifest(PluginManifest manifest, InstalledPlugin plugin) {
        return new PluginView(
                manifest.id(),
                manifest.version().toString(),
                manifest.name(),
                manifest.description(),
                manifest.kinds().stream().map(PluginKind::value).collect(Collectors.toCollection(TreeSet::new)),
                manifest.platform().toString(),
                plugin.status(),
                plugin.problems(),
                plugin.publisherKey().orElse(null));
    }
}
