// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.signature;

import java.util.Objects;

/** What the signature of a package says about its publisher. */
public sealed interface PackageVerification {

    /** Whether a key trusted by the installation signed the package. */
    default boolean isVerified() {
        return this instanceof Verified;
    }

    /** The package carries no signature. */
    record Unsigned() implements PackageVerification {}

    /**
     * The package is signed and intact, but with a key the installation does not trust: its
     * publisher is unknown.
     *
     * @param keyId identifier of the signing key
     */
    record UnknownKey(String keyId) implements PackageVerification {
        public UnknownKey {
            Objects.requireNonNull(keyId, "keyId");
        }
    }

    /**
     * The package is intact and signed with a trusted key.
     *
     * @param keyId identifier of the signing key, see {@link SigningKeys#keyId}
     */
    record Verified(String keyId) implements PackageVerification {
        public Verified {
            Objects.requireNonNull(keyId, "keyId");
        }
    }
}
