// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.plugin;

import java.util.List;

/**
 * The data that a plugin keeps in the kernel without a backend of its own (ADR-0031): collections
 * of JSON documents of each organization, read and written with {@code /api/v1/data/<plugin
 * id>/<collection>/} under row-level security.
 *
 * @param collections names of the collections, lowercase letters, digits and '-'
 */
public record DataEntry(List<String> collections) {

    public DataEntry {
        collections = List.copyOf(collections);
    }

    /** Whether the plugin declares this collection. */
    public boolean declares(String collection) {
        return collections.contains(collection);
    }
}
