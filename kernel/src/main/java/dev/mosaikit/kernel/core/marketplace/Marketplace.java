// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.core.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mosaikit.kernel.api.signature.PackageSignatureException;
import dev.mosaikit.kernel.api.signature.PackageSignatures;
import dev.mosaikit.kernel.api.signature.PackageVerification;
import dev.mosaikit.kernel.api.signature.PluginIndex;
import dev.mosaikit.kernel.api.version.Version;
import dev.mosaikit.kernel.core.config.KernelConfig;
import dev.mosaikit.kernel.core.error.ConflictException;
import dev.mosaikit.kernel.core.error.ForbiddenOperationException;
import dev.mosaikit.kernel.core.error.InvalidInputException;
import dev.mosaikit.kernel.core.error.ResourceNotFoundException;
import dev.mosaikit.kernel.core.error.ServiceUnavailableException;
import dev.mosaikit.kernel.core.plugin.BackendCheck;
import dev.mosaikit.kernel.core.plugin.InstalledPlugin;
import dev.mosaikit.kernel.core.plugin.PackageTrust;
import dev.mosaikit.kernel.core.plugin.PluginCatalog;
import dev.mosaikit.kernel.core.plugin.PluginRegistry;
import dev.mosaikit.kernel.core.plugin.PluginStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * The marketplace of the installation (MK-022, ADR-0021): the catalogs it reads, whose index must
 * be signed by a trusted key, and the installation of their packages, or of a package uploaded by
 * an administrator, into the plugins directory. Every package must be signed by a trusted
 * publisher; it takes effect at the next start.
 */
@ApplicationScoped
public class Marketplace {

    private static final Logger LOG = Logger.getLogger(Marketplace.class);

    /** Where replaced packages are kept, ignored by the scan of plugins. */
    static final String PREVIOUS = ".previous";

    /**
     * Prefix of the directory where a package is inspected: in the plugins directory, which belongs
     * to the kernel, rather than in the temporary directory that other users share; ignored by the
     * scan of plugins.
     */
    private static final String INSTALLING = ".installing-";

    private static final Pattern SAFE_NAME = Pattern.compile("[^A-Za-z0-9._-]");

    private final PluginRegistry registry;
    private final CatalogSources sources;
    private final ObjectMapper json;
    private final List<URI> catalogs;
    private final long maxBytes;

    public Marketplace(PluginRegistry registry, CatalogSources sources, ObjectMapper json, KernelConfig config) {
        this.registry = registry;
        this.sources = sources;
        this.json = json;
        this.catalogs = config.marketplace().sources().orElse(List.of());
        this.maxBytes = config.marketplace().maxPackageBytes();
    }

    /** A catalog whose index was read, verified or not. */
    private record Catalog(CatalogView.Source source, List<PluginIndex.Entry> entries) {}

    /** What the catalogs offer, compared with what is installed. */
    public CatalogView catalog() {
        Map<String, String> installed = installedVersions();
        List<CatalogView.Source> views = new ArrayList<>();
        List<CatalogView.Offer> offers = new ArrayList<>();
        for (URI uri : catalogs) {
            Catalog catalog = read(uri);
            views.add(catalog.source());
            for (PluginIndex.Entry entry : catalog.entries()) {
                String current = installed.get(entry.id());
                offers.add(new CatalogView.Offer(
                        uri.toString(),
                        entry.id(),
                        entry.version(),
                        entry.name(),
                        entry.size(),
                        entry.publisherKey(),
                        current,
                        state(entry.version(), current)));
            }
        }
        offers.sort(Comparator.comparing(CatalogView.Offer::id).thenComparing(CatalogView.Offer::source));
        return new CatalogView(views, offers);
    }

    /**
     * Downloads a package of a catalog and places it in the plugins directory.
     *
     * @throws ResourceNotFoundException when the catalog does not offer that version
     */
    public Installation install(String source, String id, String version) {
        URI uri = catalogs.stream()
                .filter(catalog -> catalog.toString().equals(source))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No catalog " + source + " is configured."));
        Catalog catalog = read(uri);
        if (catalog.source().keyId() == null) {
            throw new ServiceUnavailableException("The catalog " + source + " cannot be used: "
                    + catalog.source().error());
        }
        PluginIndex.Entry entry = catalog.entries().stream()
                .filter(candidate ->
                        candidate.id().equals(id) && candidate.version().equals(version))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "The catalog " + source + " does not offer " + id + " " + version + "."));
        byte[] bytes;
        try {
            bytes = sources.read(uri, entry.file(), maxBytes);
        } catch (IOException e) {
            throw new ServiceUnavailableException("The package cannot be downloaded: " + e.getMessage());
        }
        if (bytes.length != entry.size() || !sha256(bytes).equals(entry.sha256())) {
            throw new ForbiddenOperationException(
                    "The package of " + id + " " + version + " does not match the signed index of " + source + ".");
        }
        Installation installation = place(bytes, Optional.of(entry));
        LOG.infof("Plugin %s %s installed from %s; restart to use it", id, version, source);
        return installation;
    }

    /** Places a package uploaded by an administrator, for an installation without network. */
    public Installation upload(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidInputException(List.of("package is empty"));
        }
        if (bytes.length > maxBytes) {
            throw new InvalidInputException(List.of("package is larger than " + maxBytes + " bytes"));
        }
        return place(bytes, Optional.empty());
    }

    private Installation place(byte[] bytes, Optional<PluginIndex.Entry> expected) {
        Path work = null;
        try {
            work = Files.createTempDirectory(registry.directory(), INSTALLING);
            Path candidate = Files.write(work.resolve("candidate.zip"), bytes);
            PackageTrust trust = registry.trust();
            String keyId = verified(candidate, trust);
            InstalledPlugin plugin = inspect(candidate, work, trust);
            String version = plugin.manifest().orElseThrow().version().toString();
            expected.ifPresent(entry -> {
                if (!entry.id().equals(plugin.key()) || !entry.version().equals(version)) {
                    throw new ForbiddenOperationException("The package is " + plugin.key() + " " + version + ", not "
                            + entry.id() + " " + entry.version() + " as the index says.");
                }
            });
            String replaced = setAside(plugin.key());
            String file = SAFE_NAME.matcher(plugin.key() + "-" + version).replaceAll("_") + ".zip";
            Files.copy(candidate, registry.directory().resolve(file), StandardCopyOption.REPLACE_EXISTING);
            List<String> problems = plugin.status() == PluginStatus.ACTIVE ? List.of() : plugin.problems();
            return new Installation(plugin.key(), version, file, replaced, keyId, problems, true);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot install the package", e);
        } finally {
            delete(work);
        }
    }

    private static String verified(Path candidate, PackageTrust trust) throws IOException {
        try {
            PackageVerification verification = PackageSignatures.verify(candidate, trust.trustedKeys());
            if (verification instanceof PackageVerification.Verified(String keyId)) {
                return keyId;
            }
            throw new ForbiddenOperationException(
                    "Only packages signed by a trusted publisher can be installed from the marketplace: "
                            + (verification instanceof PackageVerification.UnknownKey(String keyId)
                                    ? "the key " + keyId + " is not trusted"
                                    : "the package is not signed")
                            + ".");
        } catch (PackageSignatureException e) {
            throw new ForbiddenOperationException("Refused package: " + e.getMessage());
        }
    }

    /** Reads the package like the kernel at start, alone in a scratch directory. */
    private InstalledPlugin inspect(Path candidate, Path work, PackageTrust trust) throws IOException {
        Path plugins = Files.createDirectories(work.resolve("plugins"));
        Files.copy(candidate, plugins.resolve("candidate.zip"));
        List<InstalledPlugin> found = new PluginCatalog(registry.kernelVersion(), BackendCheck.jarExists(), trust)
                .scan(plugins, work.resolve("packages"));
        InstalledPlugin plugin = found.getFirst();
        if (plugin.status() == PluginStatus.INVALID || plugin.manifest().isEmpty()) {
            throw new InvalidInputException(plugin.problems().stream()
                    .map(problem -> "package " + problem)
                    .toList());
        }
        return plugin;
    }

    /** Moves the package of an installed plugin to {@code plugins/.previous}; returns its name. */
    private String setAside(String id) throws IOException {
        Optional<InstalledPlugin> current = registry.all().stream()
                .filter(plugin -> plugin.key().equals(id))
                .findFirst();
        if (current.isEmpty()) {
            return null;
        }
        Path directory = current.get().directory();
        if (!directory.startsWith(registry.packagesDirectory())) {
            throw new ConflictException(id
                    + " is installed as a directory, not as a package: replace it by hand in the plugins directory.");
        }
        Path zip = registry.directory().resolve(directory.getFileName() + ".zip");
        if (!Files.isRegularFile(zip)) {
            return null;
        }
        Path previous = Files.createDirectories(registry.directory().resolve(PREVIOUS));
        Files.move(zip, previous.resolve(zip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        return zip.getFileName().toString();
    }

    private Catalog read(URI uri) {
        try {
            byte[] index = sources.read(uri, PluginIndex.INDEX_FILE, maxBytes);
            byte[] signature = sources.read(uri, PluginIndex.SIGNATURE_FILE, 64L * 1024);
            String keyId = PluginIndex.verify(index, signature, registry.trust().trustedKeys());
            return new Catalog(new CatalogView.Source(uri.toString(), "verified", keyId, null), entries(index));
        } catch (IOException | PackageSignatureException | IllegalArgumentException e) {
            return new Catalog(new CatalogView.Source(uri.toString(), "refused", null, e.getMessage()), List.of());
        }
    }

    private List<PluginIndex.Entry> entries(byte[] index) throws IOException {
        JsonNode tree = json.readTree(index);
        if (tree.path("format").asInt() != PluginIndex.FORMAT) {
            throw new IOException("Unsupported index format " + tree.path("format"));
        }
        return tree.path("plugins")
                .valueStream()
                .map(plugin -> new PluginIndex.Entry(
                        plugin.path("id").asText(),
                        plugin.path("version").asText(),
                        plugin.path("name").asText(null),
                        plugin.path("file").asText(),
                        plugin.path("sha256").asText(),
                        plugin.path("size").asLong(),
                        plugin.path("publisherKey").asText("")))
                .toList();
    }

    private Map<String, String> installedVersions() {
        return registry.all().stream()
                .filter(plugin -> plugin.manifest().isPresent())
                .collect(Collectors.toMap(
                        InstalledPlugin::key,
                        plugin -> plugin.manifest().orElseThrow().version().toString(),
                        (first, second) -> first));
    }

    /** How an offered version compares with the installed one. */
    static String state(String offered, String installed) {
        if (installed == null) {
            return "available";
        }
        try {
            int comparison = Version.parse(offered).compareTo(Version.parse(installed));
            if (comparison == 0) {
                return "installed";
            }
            return comparison > 0 ? "update" : "older";
        } catch (IllegalArgumentException _) {
            return offered.equals(installed) ? "installed" : "update";
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java platform", e);
        }
    }

    private static void delete(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(Marketplace::deleteFile);
        } catch (IOException e) {
            LOG.debugf("Cannot delete %s: %s", directory, e.getMessage());
        }
    }

    private static void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            LOG.debugf("Cannot delete %s: %s", path, e.getMessage());
        }
    }
}
