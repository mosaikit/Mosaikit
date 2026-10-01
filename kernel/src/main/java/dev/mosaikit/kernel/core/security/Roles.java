// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.security;

/** Roles defined by the kernel. Plugins define their own permissions on top of these. */
public final class Roles {

    /** Operator of the installation: installs plugins and manages organizations. */
    public static final String PLATFORM_ADMIN = "platform-admin";

    /** Manager of an organization: members, enabled plugins, data rules. */
    public static final String ORGANIZATION_ADMIN = "organization-admin";

    /** Member of an organization. */
    public static final String ORGANIZATION_USER = "organization-user";

    private Roles() {}
}
