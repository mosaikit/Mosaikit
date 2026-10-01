// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.plugin;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What a plugin brings to the platform. A plugin can declare more than one kind. */
public enum PluginKind {
    /** Owns a route, an entry in the launcher and a full screen; may offer extension points. */
    APP,
    /** Contributes to extension points offered by the kernel or by other plugins. */
    EXTENSION,
    /** Backend only: connectors, jobs, APIs. */
    SERVICE,
    /** Theme tokens and fonts. */
    THEME,
    /** Additional translations. */
    LOCALE,
    /** Identity providers and sign-in flows. */
    AUTH;

    /** The value used in manifests, for example {@code "app"}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Resolves a manifest value, ignoring case. */
    public static Optional<PluginKind> fromValue(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.value().equalsIgnoreCase(value))
                .findFirst();
    }
}
