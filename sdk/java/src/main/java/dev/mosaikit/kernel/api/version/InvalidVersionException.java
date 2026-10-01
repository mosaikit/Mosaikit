// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.version;

/** Thrown when a version or a version range cannot be parsed. */
public final class InvalidVersionException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidVersionException(String message) {
        super(message);
    }
}
