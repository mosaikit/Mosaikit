// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.plugin;

import dev.mosaikit.kernel.api.version.InvalidVersionException;
import dev.mosaikit.kernel.api.version.Version;
import dev.mosaikit.kernel.api.version.VersionRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Reads and validates plugin manifests.
 *
 * <p>The input is the manifest as a tree of maps, lists and scalars, as produced by any YAML or
 * JSON parser. All violations are collected, so that a plugin author can fix them in one pass.
 */
public final class PluginManifests {

    /** Reverse-DNS identifier with at least two segments. */
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*+(?:\\.[a-z][a-z0-9-]*+)++$");

    /** PostgreSQL identifier reserved to plugin schemas. */
    public static final Pattern SCHEMA_PATTERN = Pattern.compile("^p_[a-z][a-z0-9_]{0,59}$");

    /** Name of an action of a plugin (MK-015). */
    public static final Pattern ACTION_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{0,47}$");

    /** HTTP methods an action can call. */
    private static final String ACTIONS = "actions";

    /** Prefixes of the extension points of the kernel, which plugins may not declare. */
    private static final List<String> KERNEL_POINTS = List.of("launcher.", "shell.", "kernel.");

    private static final String MUST_BE_OBJECT = "must be an object";

    public static final List<String> ACTION_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    /** Name of the API of a plugin: one path segment under {@code /api/v1/p/}. */
    /** Name of a collection of documents of a plugin (ADR-0031). */
    public static final Pattern COLLECTION_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");

    public static final Pattern API_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,39}$");

    /** Extension point or requirement name, for example {@code launcher.app}. */
    public static final Pattern POINT_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9-]*+(?:\\.[a-z][a-zA-Z0-9-]*+)++$");

    private static final int MAX_NAME_LENGTH = 80;

    private PluginManifests() {}

    /** Parses a manifest tree. */
    public static ManifestParseResult parse(Map<String, ?> tree) {
        Objects.requireNonNull(tree, "tree");
        return new Reader(tree).read();
    }

    /** Collects values and violations while walking the manifest tree. */
    private static final class Reader {

        private final Map<String, ?> tree;
        private final List<ManifestViolation> violations = new ArrayList<>();

        Reader(Map<String, ?> tree) {
            this.tree = tree;
        }

        ManifestParseResult read() {
            String id = requiredText(tree, "id");
            if (id != null && !ID_PATTERN.matcher(id).matches()) {
                violation("id", "must be a lowercase reverse-DNS name, for example dev.example.traffic");
            }
            Version version = convert("version", requiredText(tree, "version"), Version::parse);
            String name = requiredText(tree, "name");
            if (name != null && name.length() > MAX_NAME_LENGTH) {
                violation("name", "must be at most " + MAX_NAME_LENGTH + " characters");
            }
            String description = optionalText(tree, "description");
            Set<PluginKind> kinds = readKinds();
            VersionRange platform = convert("platform", requiredText(tree, "platform"), VersionRange::parse);
            Map<String, VersionRange> requires = readRequires();
            FrontendEntry frontend = readFrontend();
            BackendEntry backend = readBackend();
            DatabaseEntry database = readDatabase();
            DataEntry data = readData();
            List<Contribution> contributions = readContributions();
            List<ActionEntry> actions = readActions(backend);

            if (!violations.isEmpty()) {
                return new ManifestParseResult.Invalid(violations);
            }
            return new ManifestParseResult.Valid(new PluginManifest(
                    id,
                    version,
                    name,
                    description,
                    kinds,
                    platform,
                    requires,
                    frontend,
                    backend,
                    database,
                    data,
                    contributions,
                    actions));
        }

        private List<ActionEntry> readActions(BackendEntry backend) {
            Object raw = tree.get(ACTIONS);
            if (raw == null) {
                return List.of();
            }
            if (!(raw instanceof List<?> list)) {
                violation(ACTIONS, "must be a list of actions");
                return List.of();
            }
            if (backend == null && !list.isEmpty()) {
                violation(ACTIONS, "need a backend: an action calls the backend API of the plugin");
            }
            List<ActionEntry> actions = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                readAction("actions[" + i + "]", list.get(i)).ifPresent(action -> {
                    if (!names.add(action.name())) {
                        violation(ACTIONS, "the action '" + action.name() + "' is declared twice");
                    }
                    actions.add(action);
                });
            }
            return actions;
        }

        private Optional<ActionEntry> readAction(String field, Object item) {
            if (!(item instanceof Map<?, ?> raw)) {
                violation(field, MUST_BE_OBJECT);
                return Optional.empty();
            }
            Map<String, Object> action = new LinkedHashMap<>();
            raw.forEach((key, value) -> action.put(String.valueOf(key), value));
            int before = violations.size();
            String name = requiredText(action, "name", field + ".name");
            if (name != null && !ACTION_PATTERN.matcher(name).matches()) {
                violation(field + ".name", "must be lowercase letters, digits and '-', for example create-note");
            }
            String title = requiredText(action, "title", field + ".title");
            String description = optionalText(action, "description");
            String riskText = requiredText(action, "risk", field + ".risk");
            ActionRisk risk =
                    riskText == null ? null : ActionRisk.fromValue(riskText).orElse(null);
            if (riskText != null && risk == null) {
                violation(field + ".risk", "must be read, write or execute");
            }
            InputSchema input = InputSchema.parse(
                    action.getOrDefault("input", Map.of("type", "object")), field + ".input", this::violation);
            Map<String, ?> call = optionalMap(action, "call", field + ".call");
            String method = Objects.requireNonNullElse(optionalText(call, "method"), "POST")
                    .toUpperCase(Locale.ROOT);
            if (!ACTION_METHODS.contains(method)) {
                violation(field + ".call.method", "must be one of " + ACTION_METHODS);
            }
            if (risk == ActionRisk.READ && !method.equals("GET")) {
                violation(field + ".call.method", "a read action calls GET");
            }
            String path = requiredText(call, "path", field + ".call.path");
            if (path != null && (!isSafeRelativePath(path) || path.contains("?") || path.contains("#"))) {
                violation(field + ".call.path", "must be a path relative to the API of the plugin, without '..'");
            }
            if (violations.size() > before) {
                return Optional.empty();
            }
            return Optional.of(new ActionEntry(name, title, description, risk, input, method, path));
        }

        private Set<PluginKind> readKinds() {
            Object raw = tree.get("kind");
            List<?> values;
            if (raw instanceof List<?> list) {
                values = list;
            } else {
                values = raw == null ? List.of() : List.of(raw);
            }
            if (values.isEmpty()) {
                violation("kind", "is required: one or more of app, extension, service, theme, locale, auth");
                return Set.of();
            }
            Set<PluginKind> kinds = EnumSet.noneOf(PluginKind.class);
            for (Object value : values) {
                PluginKind.fromValue(String.valueOf(value))
                        .ifPresentOrElse(kinds::add, () -> violation("kind", "unknown kind '" + value + "'"));
            }
            return kinds;
        }

        private Map<String, VersionRange> readRequires() {
            Map<String, ?> requires = optionalMap(tree, "requires");
            Map<String, VersionRange> result = new LinkedHashMap<>();
            requires.forEach((point, range) -> {
                String field = "requires." + point;
                if (!POINT_PATTERN.matcher(point).matches()) {
                    violation(field, "must be a dotted extension point or plugin name");
                }
                VersionRange parsed = convert(field, String.valueOf(range), VersionRange::parse);
                if (parsed != null) {
                    result.put(point, parsed);
                }
            });
            return result;
        }

        private FrontendEntry readFrontend() {
            Map<String, ?> frontend = optionalMap(tree, "frontend");
            if (frontend.isEmpty()) {
                return null;
            }
            String entry = requiredText(frontend, "entry", "frontend.entry");
            if (entry != null && !isSafeRelativePath(entry)) {
                violation("frontend.entry", "must be a relative path inside the plugin, without '..'");
            }
            String isolationText = Objects.requireNonNullElse(optionalText(frontend, "isolation"), "module");
            FrontendIsolation isolation =
                    FrontendIsolation.fromValue(isolationText).orElse(null);
            if (isolation == null) {
                violation("frontend.isolation", "must be 'module' or 'iframe'");
            }
            FrontendBridge bridge = readBridge(frontend);
            List<String> points = readPoints(frontend);
            return entry == null || isolation == null ? null : new FrontendEntry(entry, isolation, bridge, points);
        }

        private List<String> readPoints(Map<String, ?> frontend) {
            List<String> points = readList(frontend, "points", "frontend.points");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < points.size(); i++) {
                String point = points.get(i);
                String field = "frontend.points[" + i + "]";
                if (!POINT_PATTERN.matcher(point).matches()) {
                    violation(field, "must be a dotted extension point name, such as activities.detail");
                } else if (KERNEL_POINTS.stream().anyMatch(point::startsWith)) {
                    violation(field, "belongs to the kernel: use a name of the plugin, such as activities.detail");
                } else if (!seen.add(point)) {
                    violation(field, "is declared twice");
                }
            }
            return points;
        }

        private FrontendBridge readBridge(Map<String, ?> frontend) {
            Map<String, ?> bridge = optionalMap(frontend, "bridge", "frontend.bridge");
            List<String> publishes = readTopics(bridge, "publishes");
            List<String> subscribes = readTopics(bridge, "subscribes");
            List<String> services = readList(bridge, "services");
            for (int i = 0; i < services.size(); i++) {
                if (!FrontendBridge.KNOWN_SERVICES.contains(services.get(i))) {
                    violation(
                            "frontend.bridge.services[" + i + "]",
                            "unknown service '" + services.get(i) + "': use one of " + FrontendBridge.KNOWN_SERVICES);
                }
            }
            return new FrontendBridge(publishes, subscribes, services);
        }

        private List<String> readTopics(Map<String, ?> bridge, String key) {
            List<String> topics = readList(bridge, key);
            for (int i = 0; i < topics.size(); i++) {
                if (!FrontendBridge.isTopic(topics.get(i))) {
                    violation(
                            "frontend.bridge." + key + "[" + i + "]",
                            "must be a dotted topic such as maps.selection.changed, or a prefix ending with .*");
                }
            }
            return topics;
        }

        private List<String> readList(Map<String, ?> map, String key) {
            return readList(map, key, "frontend.bridge." + key);
        }

        private List<String> readList(Map<String, ?> map, String key, String field) {
            Object raw = map.get(key);
            if (raw == null) {
                return List.of();
            }
            if (!(raw instanceof List<?> list)) {
                violation(field, "must be a list");
                return List.of();
            }
            return list.stream().map(String::valueOf).toList();
        }

        private BackendEntry readBackend() {
            Map<String, ?> backend = optionalMap(tree, "backend");
            if (backend.isEmpty()) {
                return null;
            }
            String jar = requiredText(backend, "jar", "backend.jar");
            if (jar != null && (!isSafeRelativePath(jar) || !jar.endsWith(".jar"))) {
                violation("backend.jar", "must be the relative path of a .jar file inside the plugin, without '..'");
                jar = null;
            }
            String api = requiredText(backend, "api", "backend.api");
            if (api != null && !API_PATTERN.matcher(api).matches()) {
                violation("backend.api", "must be lowercase letters, digits and '-', for example traffic-lights");
                api = null;
            }
            return jar == null || api == null ? null : new BackendEntry(jar, api);
        }

        private DatabaseEntry readDatabase() {
            Map<String, ?> database = optionalMap(tree, "database");
            if (database.isEmpty()) {
                return null;
            }
            String schema = requiredText(database, "schema", "database.schema");
            if (schema != null && !SCHEMA_PATTERN.matcher(schema).matches()) {
                violation("database.schema", "must start with 'p_' and contain only lowercase letters, digits, '_'");
            }
            String migrations = Objects.requireNonNullElse(optionalText(database, "migrations"), "db");
            if (!isSafeRelativePath(migrations)) {
                violation("database.migrations", "must be a relative path inside the plugin, without '..'");
            }
            return schema == null ? null : new DatabaseEntry(schema, migrations);
        }

        private DataEntry readData() {
            Map<String, ?> data = optionalMap(tree, "data");
            if (data.isEmpty()) {
                return null;
            }
            if (!(data.get("collections") instanceof List<?> list) || list.isEmpty()) {
                violation("data.collections", "must be a list of at least one collection name");
                return null;
            }
            List<String> collections = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                String field = "data.collections[" + i + "]";
                if (!(list.get(i) instanceof String name)
                        || !COLLECTION_PATTERN.matcher(name).matches()) {
                    violation(field, "must be lowercase letters, digits and '-', for example items");
                } else if (collections.contains(name)) {
                    violation(field, "the collection '" + name + "' is declared twice");
                } else {
                    collections.add(name);
                }
            }
            return new DataEntry(collections);
        }

        private List<Contribution> readContributions() {
            Map<String, ?> contributes = optionalMap(tree, "contributes");
            List<Contribution> result = new ArrayList<>();
            contributes.forEach((point, items) -> {
                String field = "contributes." + point;
                if (!POINT_PATTERN.matcher(point).matches()) {
                    violation(field, "must be a dotted extension point name");
                    return;
                }
                if (!(items instanceof List<?> list)) {
                    violation(field, "must be a list of contributions");
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    readContribution(point, field + "[" + i + "]", list.get(i)).ifPresent(result::add);
                }
            });
            return result;
        }

        private Optional<Contribution> readContribution(String point, String field, Object item) {
            if (!(item instanceof Map<?, ?> raw)) {
                violation(field, MUST_BE_OBJECT);
                return Optional.empty();
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            raw.forEach((key, value) -> attributes.put(String.valueOf(key), value));
            Object id = attributes.remove("id");
            if (!(id instanceof String text) || text.isBlank()) {
                violation(field + ".id", "is required");
                return Optional.empty();
            }
            attributes.values().removeIf(Objects::isNull);
            return Optional.of(new Contribution(point, text, attributes));
        }

        private String requiredText(Map<String, ?> map, String key) {
            return requiredText(map, key, key);
        }

        private String requiredText(Map<String, ?> map, String key, String field) {
            String value = optionalText(map, key);
            if (value == null || value.isBlank()) {
                violation(field, "is required");
                return null;
            }
            return value;
        }

        private static String optionalText(Map<String, ?> map, String key) {
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value).trim();
        }

        private Map<String, ?> optionalMap(Map<String, ?> map, String key) {
            return optionalMap(map, key, key);
        }

        private Map<String, ?> optionalMap(Map<String, ?> map, String key, String field) {
            Object value = map.get(key);
            if (value == null) {
                return Map.of();
            }
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> result = new LinkedHashMap<>();
                nested.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            violation(field, MUST_BE_OBJECT);
            return Map.of();
        }

        private <T> T convert(String field, String text, Function<String, T> parser) {
            if (text == null) {
                return null;
            }
            try {
                return parser.apply(text);
            } catch (InvalidVersionException e) {
                violation(field, e.getMessage());
                return null;
            }
        }

        private void violation(String field, String message) {
            violations.add(new ManifestViolation(field, message));
        }

        private static boolean isSafeRelativePath(String path) {
            return !path.isBlank()
                    && !path.startsWith("/")
                    && !path.contains("\\")
                    && !path.contains(":")
                    && Arrays.stream(path.split("/")).noneMatch(".."::equals);
        }
    }
}
