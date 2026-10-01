// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.signature;

/** A signed package whose content does not match its signature: it must not be installed. */
public final class PackageSignatureException extends Exception {

    private static final long serialVersionUID = 1L;

    public PackageSignatureException(String message) {
        super(message);
    }

    public PackageSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
