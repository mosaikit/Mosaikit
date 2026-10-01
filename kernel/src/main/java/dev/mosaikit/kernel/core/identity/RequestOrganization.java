// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.identity;

import dev.mosaikit.kernel.api.context.CurrentOrganization;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import java.util.Optional;
import java.util.UUID;

/**
 * The organization of the current request, for the kernel and for the Java code of plugins
 * (MK-017): the one chosen at authentication by {@link OrganizationAccess} or by the realm.
 */
@RequestScoped
public class RequestOrganization implements CurrentOrganization {

    private final SecurityIdentity identity;

    public RequestOrganization(SecurityIdentity identity) {
        this.identity = identity;
    }

    /** The organization of a security identity, as set at authentication. */
    public static Optional<UUID> of(SecurityIdentity identity) {
        String value = identity == null ? null : identity.getAttribute(OrganizationAccess.ORGANIZATION_ATTRIBUTE);
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    @Override
    public Optional<UUID> id() {
        return of(identity);
    }

    @Override
    public Optional<String> slug() {
        return Optional.ofNullable(identity.getAttribute(OrganizationAccess.SLUG_ATTRIBUTE));
    }
}
