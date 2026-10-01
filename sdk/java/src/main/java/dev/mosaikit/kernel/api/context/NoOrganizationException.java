// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.context;

/** A request that needs an organization has none: the kernel answers it with {@code 403}. */
public final class NoOrganizationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoOrganizationException() {
        super("Choose an organization: this request acts on the data of one organization.");
    }
}
