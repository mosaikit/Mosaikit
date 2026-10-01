// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("MK-017")
class CurrentOrganizationTest {

    private record Fixed(Optional<UUID> id, Optional<String> slug) implements CurrentOrganization {}

    @Test
    void requiresAnOrganization() {
        UUID acme = UUID.randomUUID();

        assertThat(new Fixed(Optional.of(acme), Optional.of("acme")).require()).isEqualTo(acme);
        CurrentOrganization none = new Fixed(Optional.empty(), Optional.empty());
        assertThatThrownBy(none::require)
                .isInstanceOf(NoOrganizationException.class)
                .hasMessageContaining("Choose an organization");
    }
}
