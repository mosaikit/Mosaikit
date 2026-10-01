// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mosaikit.kernel.api.version.Version;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("MK-005")
class PluginManifestsTest {

    private static Map<String, Object> validTree() {
        Map<String, Object> tree = new HashMap<>();
        tree.put("id", "dev.example.traffic");
        tree.put("version", "2.3.1");
        tree.put("name", "Traffic");
        tree.put("description", "Road traffic monitoring");
        tree.put("kind", List.of("app", "service"));
        tree.put("platform", ">=0.1 <1");
        tree.put("requires", Map.of("app.maps", "^3.0"));
        tree.put("frontend", Map.of("entry", "web/index.js", "isolation", "iframe"));
        tree.put("backend", Map.of("jar", "lib/traffic.jar", "api", "traffic"));
        tree.put("database", Map.of("schema", "p_traffic", "migrations", "db"));
        tree.put(
                "contributes",
                Map.of("launcher.app", List.of(Map.of("id", "traffic", "route", "/app/traffic", "icon", "road"))));
        return tree;
    }

    private static PluginManifest parseValid(Map<String, Object> tree) {
        ManifestParseResult result = PluginManifests.parse(tree);
        assertThat(result).isInstanceOf(ManifestParseResult.Valid.class);
        return ((ManifestParseResult.Valid) result).manifest();
    }

    private static List<ManifestViolation> parseInvalid(Map<String, Object> tree) {
        ManifestParseResult result = PluginManifests.parse(tree);
        assertThat(result).isInstanceOf(ManifestParseResult.Invalid.class);
        return ((ManifestParseResult.Invalid) result).violations();
    }

    @Test
    void readsACompleteManifest() {
        PluginManifest manifest = parseValid(validTree());

        assertThat(manifest.id()).isEqualTo("dev.example.traffic");
        assertThat(manifest.version()).isEqualTo(Version.of(2, 3, 1));
        assertThat(manifest.kinds()).containsExactlyInAnyOrder(PluginKind.APP, PluginKind.SERVICE);
        assertThat(manifest.supportsPlatform(Version.of(0, 4, 0))).isTrue();
        assertThat(manifest.supportsPlatform(Version.of(1, 0, 0))).isFalse();
        assertThat(manifest.requires()).containsKey("app.maps");
        assertThat(manifest.frontend()).contains(new FrontendEntry("web/index.js", FrontendIsolation.IFRAME));
        assertThat(manifest.backend()).contains(new BackendEntry("lib/traffic.jar", "traffic"));
        assertThat(manifest.database()).contains(new DatabaseEntry("p_traffic", "db"));
        assertThat(manifest.contributionsTo("launcher.app")).singleElement().satisfies(contribution -> {
            assertThat(contribution.id()).isEqualTo("traffic");
            assertThat(contribution.attributes())
                    .containsEntry("route", "/app/traffic")
                    .doesNotContainKey("id");
        });
        assertThat(manifest).hasToString("dev.example.traffic@2.3.1");
    }

    @Test
    void acceptsASingleKindAndDefaultsTheFrontendToModule() {
        Map<String, Object> tree = validTree();
        tree.put("kind", "extension");
        tree.put("frontend", Map.of("entry", "web/index.js"));
        tree.remove("database");
        tree.remove("backend");
        tree.remove("description");

        PluginManifest manifest = parseValid(tree);

        assertThat(manifest.kinds()).containsExactly(PluginKind.EXTENSION);
        assertThat(manifest.frontend()).map(FrontendEntry::isolation).contains(FrontendIsolation.MODULE);
        assertThat(manifest.database()).isEmpty();
        assertThat(manifest.backend()).isEmpty();
        assertThat(manifest.description()).isEmpty();
    }

    @Test
    void reportsEveryMissingRequiredField() {
        List<ManifestViolation> violations = parseInvalid(new HashMap<>());

        assertThat(violations)
                .extracting(ManifestViolation::field)
                .containsExactlyInAnyOrder("id", "version", "name", "kind", "platform");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Traffic", "traffic", "dev..traffic", "dev.Traffic", "1dev.traffic"})
    void rejectsIdentifiersThatAreNotReverseDns(String id) {
        Map<String, Object> tree = validTree();
        tree.put("id", id);

        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.js", "/etc/passwd", "web\\index.js", "c:/index.js", "web/../../x.js"})
    void rejectsFrontendPathsOutsideThePlugin(String entry) {
        Map<String, Object> tree = validTree();
        tree.put("frontend", Map.of("entry", entry));

        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("frontend.entry");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.jar", "/opt/lib/code.jar", "lib/code.zip", "lib/code"})
    @Tag("MK-011")
    void rejectsBackendJarsThatAreNotJarsInsideThePlugin(String jar) {
        Map<String, Object> tree = validTree();
        tree.put("backend", Map.of("jar", jar, "api", "traffic"));

        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("backend.jar");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Traffic", "traffic/lights", "../x", "t", "9traffic"})
    @Tag("MK-011")
    void rejectsBackendApiNamesThatAreNotPathSegments(String api) {
        Map<String, Object> tree = validTree();
        tree.put("backend", Map.of("jar", "lib/traffic.jar", "api", api));

        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("backend.api");
    }

    @Test
    @Tag("MK-011")
    void requiresBothTheJarAndTheApiOfABackend() {
        Map<String, Object> tree = validTree();
        tree.put("backend", Map.of("other", "x"));

        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .containsExactlyInAnyOrder("backend.jar", "backend.api");
    }

    @Test
    void rejectsSchemasOutsideThePluginNamespace() {
        Map<String, Object> tree = validTree();
        tree.put("database", Map.of("schema", "public"));

        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("database.schema");
    }

    @Test
    void reportsInvalidVersionsAndRanges() {
        Map<String, Object> tree = validTree();
        tree.put("version", "2.3");
        tree.put("platform", "not a range");
        tree.put("requires", Map.of("Maps", "^3"));

        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .containsExactlyInAnyOrder("version", "platform", "requires.Maps");
    }

    @Test
    void reportsMalformedContributions() {
        Map<String, Object> tree = validTree();
        tree.put(
                "contributes",
                Map.of("launcher.app", List.of(Map.of("route", "/x"), "text"), "shell.command", "not a list"));

        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .containsExactlyInAnyOrder(
                        "contributes.launcher.app[0].id", "contributes.launcher.app[1]", "contributes.shell.command");
    }

    @Test
    void reportsUnknownKindsAndIsolation() {
        Map<String, Object> tree = validTree();
        tree.put("kind", List.of("app", "widget"));
        tree.put("frontend", Map.of("entry", "web/index.js", "isolation", "worker"));

        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .containsExactlyInAnyOrder("kind", "frontend.isolation");
    }

    @Test
    void reportsSectionsThatAreNotObjects() {
        Map<String, Object> tree = validTree();
        tree.put("frontend", "web/index.js");

        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("frontend");
    }

    @Test
    void reportsLongNamesUnsafeMigrationsAndInvalidPoints() {
        Map<String, Object> tree = validTree();
        tree.put("name", "n".repeat(81));
        tree.put("database", Map.of("schema", "p_traffic", "migrations", "../db"));
        tree.put("contributes", Map.of("Launcher App", List.of(Map.of("id", "x"))));

        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .containsExactlyInAnyOrder("name", "database.migrations", "contributes.Launcher App");
    }

    @Test
    void exposesTheNamePlatformAndContributions() {
        PluginManifest manifest = parseValid(validTree());

        assertThat(manifest.name()).isNotBlank();
        assertThat(manifest.platform().contains(Version.of(0, 1, 0))).isTrue();
        assertThat(manifest.contributions()).extracting(Contribution::id).containsExactly("traffic");
    }

    @Test
    void anInvalidResultNeedsAViolation() {
        List<ManifestViolation> none = List.of();
        assertThatThrownBy(() -> new ManifestParseResult.Invalid(none)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("MK-014")
    void readsTheBridgeOfAnIsolatedFrontend() {
        Map<String, Object> tree = validTree();
        tree.put(
                "frontend",
                Map.of(
                        "entry",
                        "web/index.js",
                        "isolation",
                        "iframe",
                        "bridge",
                        Map.of(
                                "publishes", List.of("traffic.selected"),
                                "subscribes", List.of("maps.*"),
                                "services", List.of("api"))));

        FrontendEntry frontend = parseValid(tree).frontend().orElseThrow();

        assertThat(frontend.isolation()).isEqualTo(FrontendIsolation.IFRAME);
        assertThat(frontend.bridge())
                .isEqualTo(new FrontendBridge(List.of("traffic.selected"), List.of("maps.*"), List.of("api")));
        assertThat(parseValid(validTree()).frontend().orElseThrow().bridge()).isEqualTo(FrontendBridge.NONE);
    }

    @Test
    @Tag("MK-014")
    void reportsInvalidBridges() {
        Map<String, Object> tree = validTree();
        tree.put(
                "frontend",
                Map.of(
                        "entry",
                        "web/index.js",
                        "bridge",
                        Map.of(
                                "publishes",
                                List.of("Not a topic"),
                                "subscribes",
                                "maps.*",
                                "services",
                                List.of("dom"))));

        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .containsExactlyInAnyOrder(
                        "frontend.bridge.publishes[0]", "frontend.bridge.subscribes", "frontend.bridge.services[0]");

        tree.put("frontend", Map.of("entry", "web/index.js", "bridge", "everything"));
        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("frontend.bridge");
    }

    @Test
    @Tag("MK-015")
    void readsTheActionsOfAPlugin() {
        Map<String, Object> tree = validTree();
        tree.put(
                "actions",
                List.of(
                        Map.of(
                                "name", "list-traffic",
                                "title", "List traffic",
                                "risk", "read",
                                "call", Map.of("method", "GET", "path", "traffic")),
                        Map.of(
                                "name", "close-road",
                                "title", "Close a road",
                                "description", "Closes a road to traffic.",
                                "risk", "execute",
                                "input",
                                        Map.of(
                                                "type", "object",
                                                "properties", Map.of("road", Map.of("type", "string")),
                                                "required", List.of("road")),
                                "call", Map.of("method", "post", "path", "roads/{road}/closure"))));

        List<ActionEntry> actions = parseValid(tree).actions();

        assertThat(actions).extracting(ActionEntry::name).containsExactly("list-traffic", "close-road");
        assertThat(actions.get(0).input().tree()).isEqualTo(Map.of("type", "object"));
        assertThat(actions.get(1))
                .extracting(ActionEntry::risk, ActionEntry::method, ActionEntry::path, ActionEntry::description)
                .containsExactly(ActionRisk.EXECUTE, "POST", "roads/{road}/closure", "Closes a road to traffic.");
        assertThat(actions.get(1).input().validate(Map.of())).containsExactly("input.road is required");
        assertThat(ActionRisk.EXECUTE.needsConfirmation()).isTrue();
        assertThat(ActionRisk.READ.needsConfirmation()).isFalse();
        assertThat(ActionRisk.fromValue("write")).contains(ActionRisk.WRITE);
    }

    @Test
    @Tag("MK-015")
    void reportsInvalidActions() {
        Map<String, Object> tree = validTree();
        tree.put(
                "actions",
                List.of(
                        Map.of("name", "Bad Name", "title", "x", "risk", "maybe", "call", Map.of("path", "../x")),
                        Map.of(
                                "name",
                                "read-it",
                                "title",
                                "x",
                                "risk",
                                "read",
                                "call",
                                Map.of("method", "POST", "path", "x")),
                        Map.of(
                                "name",
                                "twice",
                                "title",
                                "x",
                                "risk",
                                "write",
                                "call",
                                Map.of("method", "TRACE", "path", "x?y")),
                        Map.of("name", "twice", "title", "x", "risk", "write", "call", Map.of("path", "x")),
                        Map.of("name", "twice", "title", "x", "risk", "write", "call", Map.of("path", "y")),
                        "not an object"));

        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .contains(
                        "actions[0].name",
                        "actions[0].risk",
                        "actions[0].call.path",
                        "actions[1].call.method",
                        "actions[2].call.method",
                        "actions[2].call.path",
                        "actions",
                        "actions[5]");

        tree.put("actions", "all of them");
        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("actions");

        tree.remove("backend");
        tree.put(
                "actions",
                List.of(Map.of(
                        "name", "x", "title", "x", "risk", "read", "call", Map.of("method", "GET", "path", "x"))));
        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::message)
                .anySatisfy(message -> assertThat(message).contains("need a backend"));
    }

    @Test
    @Tag("MK-020")
    void readsTheExtensionPointsOfAFrontend() {
        Map<String, Object> tree = validTree();
        tree.put(
                "frontend",
                Map.of("entry", "web/index.js", "points", List.of("activities.detail", "activities.toolbar")));

        assertThat(parseValid(tree).frontend().orElseThrow().points())
                .containsExactly("activities.detail", "activities.toolbar");

        tree.put(
                "frontend", Map.of("entry", "web/index.js", "points", List.of("launcher.app", "nodots", "a.b", "a.b")));
        assertThat(parseInvalid(tree))
                .extracting(ManifestViolation::field)
                .containsExactly("frontend.points[0]", "frontend.points[1]", "frontend.points[3]");

        tree.put("frontend", Map.of("entry", "web/index.js", "points", "activities.detail"));
        assertThat(parseInvalid(tree)).extracting(ManifestViolation::field).containsExactly("frontend.points");
    }
}
