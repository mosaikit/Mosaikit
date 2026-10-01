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
class VersionTest {

    @Test
    void parsesAllParts() {
        Version version = Version.parse("2.1.3-rc.1+build.7");

        assertThat(version.major()).isEqualTo(2);
        assertThat(version.minor()).isEqualTo(1);
        assertThat(version.patch()).isEqualTo(3);
        assertThat(version.preRelease()).containsExactly("rc", "1");
        assertThat(version.build()).containsExactly("build", "7");
        assertThat(version).hasToString("2.1.3-rc.1+build.7");
    }

    @ParameterizedTest
    @CsvSource({
        "1.0.0-alpha, 1.0.0-alpha.1",
        "1.0.0-alpha.1, 1.0.0-alpha.beta",
        "1.0.0-alpha.beta, 1.0.0-beta",
        "1.0.0-beta.2, 1.0.0-beta.11",
        "1.0.0-rc.1, 1.0.0",
        "1.0.0, 1.0.1",
        "1.9.0, 1.10.0",
        "1.10.0, 2.0.0"
    })
    void ordersVersionsAsTheSpecificationRequires(String lower, String higher) {
        assertThat(Version.parse(lower)).isLessThan(Version.parse(higher));
    }

    @Test
    void ignoresBuildMetadataInEquality() {
        assertThat(Version.parse("1.0.0+a")).isEqualTo(Version.parse("1.0.0+b"));
        assertThat(Version.parse("1.0.0+a")).hasSameHashCodeAs(Version.parse("1.0.0+b"));
    }

    @Test
    void coreDropsPreReleaseAndBuild() {
        assertThat(Version.parse("3.2.1-rc.1+x").core()).isEqualTo(Version.of(3, 2, 1));
        assertThat(Version.parse("3.2.1-rc.1").isPreRelease()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1",
                "1.2",
                "01.2.3",
                "1.2.3-",
                "1.2.3+",
                "1.2.3-rc..1",
                "1.2.3+b_1",
                "v1.2.3",
                "1.2.3.4",
                "99999999999.0.0"
            })
    void rejectsInvalidText(String text) {
        assertThatThrownBy(() -> Version.parse(text)).isInstanceOf(InvalidVersionException.class);
    }

    @Test
    void rejectsNegativeNumbers() {
        assertThatThrownBy(() -> Version.of(-1, 0, 0)).isInstanceOf(InvalidVersionException.class);
        assertThatThrownBy(() -> Version.of(0, -1, 0)).isInstanceOf(InvalidVersionException.class);
        assertThatThrownBy(() -> Version.of(0, 0, -1)).isInstanceOf(InvalidVersionException.class);
    }

    @Test
    void keepsPreReleaseAndBuildWithHyphens() {
        Version version = Version.parse("1.0.0-alpha-1.2+build-7.x");

        assertThat(version.preRelease()).containsExactly("alpha-1", "2");
        assertThat(version.build()).containsExactly("build-7", "x");
    }

    @Test
    void isNeverEqualToAnotherType() {
        assertThat(Version.of(1, 0, 0)).isNotEqualTo("1.0.0");
    }
}
