// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.live;

import java.util.List;
import java.util.UUID;

/**
 * An event of the real-time channel (MK-031).
 *
 * @param topic what it is about, such as {@code documents.<plugin>.<collection>}
 * @param organization the organization whose pages may receive it
 * @param audience the accounts that receive it, or {@code null} for every subscriber of the organization
 * @param data the content, as a JSON object
 */
public record LiveEvent(String topic, UUID organization, List<UUID> audience, Object data) {

    /** Whether a page of an account in an organization receives the event. */
    public boolean reaches(UUID organization, UUID account) {
        return this.organization.equals(organization) && (audience == null || audience.contains(account));
    }
}
