// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.version;

import java.util.regex.Pattern;

/**
 * A version text split into {@code core}, pre-release and build metadata ({@code
 * core[-preRelease][+build]}), with the dot-separated identifiers checked one by one: small
 * patterns without repeated groups, which never backtrack much nor recurse deeply.
 *
 * @param core the numbers, for example {@code 1.2.3} or {@code 1.x}
 * @param preRelease the pre-release identifiers, or {@code null} when absent
 * @param build the build metadata, or {@code null} when absent
 */
record SemanticVersionText(String core, String preRelease, String build) {

    private static final Pattern IDENTIFIER = Pattern.compile("[0-9A-Za-z-]+");

    /** Splits the text at the first {@code +} and then at the first {@code -}. */
    static SemanticVersionText split(String text) {
        int plus = text.indexOf('+');
        String build = plus < 0 ? null : text.substring(plus + 1);
        String rest = plus < 0 ? text : text.substring(0, plus);
        int dash = rest.indexOf('-');
        String preRelease = dash < 0 ? null : rest.substring(dash + 1);
        String core = dash < 0 ? rest : rest.substring(0, dash);
        return new SemanticVersionText(core, preRelease, build);
    }

    /** {@code true} if pre-release and build are absent or made of valid identifiers. */
    boolean hasValidIdentifiers() {
        return hasValidPreRelease() && isValid(build);
    }

    /** {@code true} if the pre-release is absent or made of valid identifiers. */
    boolean hasValidPreRelease() {
        return isValid(preRelease);
    }

    private static boolean isValid(String identifiers) {
        if (identifiers == null) {
            return true;
        }
        for (String identifier : identifiers.split("\\.", -1)) {
            if (!IDENTIFIER.matcher(identifier).matches()) {
                return false;
            }
        }
        return true;
    }
}
