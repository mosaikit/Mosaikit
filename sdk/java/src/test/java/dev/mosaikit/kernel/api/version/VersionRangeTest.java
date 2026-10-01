// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("MK-004")
class VersionRangeTest {

    @ParameterizedTest(name = "{0} contains {1}: {2}")
    @CsvSource({
        "'>=1.4 <2', 1.4.0, true",
        "'>=1.4 <2', 1.99.3, true",
        "'>=1.4 <2', 2.0.0, false",
        "'>=1.4 <2', 2.0.0-rc.1, false",
        "'>= 1.0 < 2', 1.5.0, true",
        "^3.0, 3.9.1, true",
        "^3.0, 4.0.0, false",
        "^0.2.3, 0.2.9, true",
        "^0.2.3, 0.3.0, false",
        "^0.0.3, 0.0.3, true",
        "^0.0.3, 0.0.4, false",
        "~1.2.3, 1.2.9, true",
        "~1.2.3, 1.3.0, false",
        "~1, 1.9.0, true",
        "1.2, 1.2.7, true",
        "1.2, 1.3.0, false",
        "1.2.3, 1.2.3, true",
        "1.2.3, 1.2.4, false",
        "1.x, 1.8.0, true",
        "'<=1.2', 1.2.9, true",
        "'<=1.2', 1.3.0, false",
        "'<=1.2.3', 1.2.3, true",
        "'>1.2', 1.2.5, false",
        "'>1.2', 1.3.0, true",
        "'>1.2.3', 1.2.4, true",
        "'<1.4', 1.3.9, true",
        "'^1 || ^3', 3.1.0, true",
        "'^1 || ^3', 2.1.0, false",
        "*, 5.0.0, true",
        "*, 5.0.0-alpha, false",
        "'>=2.0.0-rc.1', 2.0.0-rc.2, true",
        "'>=2.0.0-rc.1', 2.1.0-rc.1, false",
        "'=1.2.3', 1.2.3, true",
        "'=1.2.3', 1.2.4, false",
        "X, 1.0.0, true",
        "'<1', 0.9.9, true",
        "'<1', 1.0.0-rc.1, false",
        "'>1', 1.9.0, false",
        "'>1', 2.0.0, true",
        "'>=1.2', 1.2.0, true",
        "'<=1', 1.9.9, true",
        "'<=1', 2.0.0, false",
        "^0, 0.9.0, true",
        "^0, 1.0.0, false",
        "^0.1, 0.1.9, true",
        "^0.1, 0.2.0, false",
        "^0.0, 0.0.9, true",
        "~1.2, 1.2.9, true",
        "~1.2, 1.3.0, false",
        "1.2.3+build.5, 1.2.3, true",
        "1-rc.1, 1.5.0, true"
    })
    void decidesMembership(String range, String version, boolean expected) {
        assertThat(VersionRange.parse(range).contains(Version.parse(version))).isEqualTo(expected);
    }

    @Test
    void emptyTextAndAnyAcceptEveryNormalVersion() {
        assertThat(VersionRange.parse("").contains(Version.of(9, 9, 9))).isTrue();
        assertThat(VersionRange.any().contains(Version.of(0, 0, 1))).isTrue();
    }

    @Test
    void keepsTheOriginalText() {
        VersionRange range = VersionRange.parse(" >=1.4 <2 ");

        assertThat(range)
                .hasToString(">=1.4 <2")
                .isEqualTo(VersionRange.parse(">=1.4 <2"))
                .hasSameHashCodeAs(VersionRange.parse(">=1.4 <2"))
                .isNotEqualTo(VersionRange.parse(">=1.4 <3"))
                .isNotEqualTo(">=1.4 <2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", ">=", "^x", "1.2.3.4", ">>1", "1..2", "1.2.3-", "1.2.3+", "1.2.3+b_1"})
    void rejectsInvalidExpressions(String text) {
        assertThatThrownBy(() -> VersionRange.parse(text)).isInstanceOf(InvalidVersionException.class);
    }
}
