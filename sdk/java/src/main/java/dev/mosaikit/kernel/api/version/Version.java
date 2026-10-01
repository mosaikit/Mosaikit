// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.version;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An immutable semantic version.
 *
 * <p>Build metadata is kept for display but ignored when comparing, as required by the
 * specification. Pre-release versions have a lower precedence than the associated normal version.
 *
 * @param major major version, incremented for incompatible changes
 * @param minor minor version, incremented for backward compatible features
 * @param patch patch version, incremented for backward compatible fixes
 * @param preRelease dot-separated pre-release identifiers, empty for a normal version
 * @param build dot-separated build metadata identifiers, empty when absent
 */
public record Version(int major, int minor, int patch, List<String> preRelease, List<String> build)
        implements Comparable<Version> {

    private static final Pattern CORE = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");
    private static final Pattern NUMERIC = Pattern.compile("^(0|[1-9]\\d*)$");

    public Version {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new InvalidVersionException("Version numbers must not be negative");
        }
        preRelease = List.copyOf(Objects.requireNonNull(preRelease, "preRelease"));
        build = List.copyOf(Objects.requireNonNull(build, "build"));
    }

    /** Creates a normal version without pre-release or build metadata. */
    public static Version of(int major, int minor, int patch) {
        return new Version(major, minor, patch, List.of(), List.of());
    }

    /**
     * Parses a full semantic version such as {@code 1.4.2}, {@code 2.0.0-rc.1} or {@code
     * 1.0.0+build.7}.
     *
     * @throws InvalidVersionException if the text is not a valid semantic version
     */
    public static Version parse(String text) {
        Objects.requireNonNull(text, "text");
        var parts = SemanticVersionText.split(text.trim());
        var matcher = CORE.matcher(parts.core());
        if (!matcher.matches() || !parts.hasValidIdentifiers()) {
            throw new InvalidVersionException("Not a semantic version: '" + text + "'");
        }
        return new Version(
                parseNumber(matcher.group(1)),
                parseNumber(matcher.group(2)),
                parseNumber(matcher.group(3)),
                identifiers(parts.preRelease()),
                identifiers(parts.build()));
    }

    /** Returns {@code true} when this version carries pre-release identifiers. */
    public boolean isPreRelease() {
        return !preRelease.isEmpty();
    }

    /** Returns this version without pre-release and build metadata. */
    public Version core() {
        return of(major, minor, patch);
    }

    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) {
            result = Integer.compare(minor, other.minor);
        }
        if (result == 0) {
            result = Integer.compare(patch, other.patch);
        }
        return result != 0 ? result : comparePreRelease(preRelease, other.preRelease);
    }

    /** Equality ignores build metadata, consistently with {@link #compareTo(Version)}. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Version version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        var text = new StringBuilder()
                .append(major)
                .append('.')
                .append(minor)
                .append('.')
                .append(patch);
        if (!preRelease.isEmpty()) {
            text.append('-').append(String.join(".", preRelease));
        }
        if (!build.isEmpty()) {
            text.append('+').append(String.join(".", build));
        }
        return text.toString();
    }

    private static int comparePreRelease(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            // A normal version has a higher precedence than any pre-release of it.
            return Boolean.compare(left.isEmpty(), right.isEmpty());
        }
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int result = compareIdentifier(left.get(i), right.get(i));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = NUMERIC.matcher(left).matches();
        boolean rightNumeric = NUMERIC.matcher(right).matches();
        if (leftNumeric && rightNumeric) {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        }
        if (leftNumeric != rightNumeric) {
            // Numeric identifiers always have a lower precedence than alphanumeric ones.
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    private static List<String> identifiers(String group) {
        return group == null ? List.of() : List.of(group.split("\\."));
    }

    private static int parseNumber(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException _) {
            throw new InvalidVersionException("Version number too large: " + digits);
        }
    }
}
