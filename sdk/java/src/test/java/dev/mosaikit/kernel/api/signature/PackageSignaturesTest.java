// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.signature;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("MK-013")
class PackageSignaturesTest {

    private static final KeyPair PUBLISHER = SigningKeys.generate();
    private static final KeyPair OTHER = SigningKeys.generate();

    @TempDir
    Path directory;

    private Path pkg;

    @BeforeEach
    void createPackage() throws IOException {
        pkg = directory.resolve("traffic-1.0.0.zip");
        Map<String, String> files = new LinkedHashMap<>();
        files.put("manifest.yaml", "id: dev.example.traffic\n");
        files.put("web/", null);
        files.put("web/index.js", "export default {};\n");
        files.put("lib/traffic.jar", "jar");
        write(pkg, files);
    }

    @Test
    void verifiesAPackageSignedWithATrustedKey() throws Exception {
        PackageSignatures.sign(pkg, PUBLISHER);

        assertThat(PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isEqualTo(new PackageVerification.Verified(SigningKeys.keyId(PUBLISHER.getPublic())));
        try (ZipFile zip = new ZipFile(pkg.toFile())) {
            assertThat(new String(
                            zip.getInputStream(zip.getEntry(PackageSignatures.DIGESTS_ENTRY))
                                    .readAllBytes(),
                            UTF_8))
                    .contains("  lib/traffic.jar\n", "  manifest.yaml\n", "  web/index.js\n")
                    .doesNotContain("web/\n");
        }
    }

    @Test
    void reportsUnsignedPackagesAndUnknownKeys() throws Exception {
        assertThat(PackageSignatures.verify(pkg, trusted(PUBLISHER))).isEqualTo(new PackageVerification.Unsigned());

        PackageSignatures.sign(pkg, OTHER);

        PackageVerification verification = PackageSignatures.verify(pkg, trusted(PUBLISHER));
        assertThat(verification).isEqualTo(new PackageVerification.UnknownKey(SigningKeys.keyId(OTHER.getPublic())));
        assertThat(verification.isVerified()).isFalse();
    }

    @Test
    void signingAgainReplacesTheSignature() throws Exception {
        PackageSignatures.sign(pkg, OTHER);
        PackageSignatures.sign(pkg, PUBLISHER);

        assertThat(PackageSignatures.verify(pkg, trusted(PUBLISHER)).isVerified())
                .isTrue();
    }

    @Test
    void refusesAChangedFile() throws Exception {
        PackageSignatures.sign(pkg, PUBLISHER);
        rewrite(name -> name.equals("web/index.js") ? "alert('changed');\n" : null, null);

        assertThatThrownBy(() -> PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isInstanceOf(PackageSignatureException.class)
                .hasMessageContaining("changed or added web/index.js");
    }

    @Test
    void refusesAnAddedOrRemovedFile() throws Exception {
        PackageSignatures.sign(pkg, PUBLISHER);
        Path signed = Files.copy(pkg, directory.resolve("signed.zip"));

        rewrite(name -> null, "lib/extra.jar");
        assertThatThrownBy(() -> PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isInstanceOf(PackageSignatureException.class)
                .hasMessageContaining("lib/extra.jar");

        Files.copy(signed, pkg, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        rewrite(name -> name.equals("lib/traffic.jar") ? REMOVE : null, null);
        assertThatThrownBy(() -> PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isInstanceOf(PackageSignatureException.class)
                .hasMessageContaining("missing lib/traffic.jar");
    }

    @Test
    void refusesASignatureThatDoesNotMatchTheKey() throws Exception {
        PackageSignatures.sign(pkg, PUBLISHER);
        String forged = "algorithm=Ed25519\nkey=" + SigningKeys.keyId(PUBLISHER.getPublic()) + "\nsignature=AAAA\n";
        rewrite(name -> name.equals(PackageSignatures.SIGNATURE_ENTRY) ? forged : null, null);

        assertThatThrownBy(() -> PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isInstanceOf(PackageSignatureException.class)
                .hasMessageContaining("does not match the key");
    }

    @Test
    void refusesIncompleteOrUnexpectedSignatureFiles() throws Exception {
        PackageSignatures.sign(pkg, PUBLISHER);
        Path signed = Files.copy(pkg, directory.resolve("signed.zip"));

        rewrite(name -> name.equals(PackageSignatures.SIGNATURE_ENTRY) ? REMOVE : null, null);
        assertThatThrownBy(() -> PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isInstanceOf(PackageSignatureException.class)
                .hasMessageContaining("Incomplete signature");

        Files.copy(signed, pkg, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        rewrite(name -> null, PackageSignatures.DIRECTORY + "other.txt");
        assertThatThrownBy(() -> PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isInstanceOf(PackageSignatureException.class)
                .hasMessageContaining("Unexpected entry");

        Files.copy(signed, pkg, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        rewrite(name -> name.equals(PackageSignatures.SIGNATURE_ENTRY) ? "algorithm=RSA\n" : null, null);
        assertThatThrownBy(() -> PackageSignatures.verify(pkg, trusted(PUBLISHER)))
                .isInstanceOf(PackageSignatureException.class)
                .hasMessageContaining("Unsupported signature algorithm");
    }

    @Test
    void readsAndWritesKeysAsPem() throws IOException {
        SigningKeys.write(PUBLISHER, directory.resolve("keys"), "acme");
        Files.writeString(directory.resolve("keys/README.txt"), "not a key");

        PublicKey read = SigningKeys.readPublic(directory.resolve("keys/acme.pub.pem"));
        assertThat(read.getEncoded()).isEqualTo(PUBLISHER.getPublic().getEncoded());
        assertThat(SigningKeys.readPrivate(directory.resolve("keys/acme.key.pem"))
                        .getEncoded())
                .isEqualTo(PUBLISHER.getPrivate().getEncoded());
        assertThat(SigningKeys.readTrusted(directory.resolve("keys")))
                .containsOnlyKeys(SigningKeys.keyId(PUBLISHER.getPublic()));
        assertThat(SigningKeys.readTrusted(directory.resolve("missing"))).isEmpty();
        assertThat(SigningKeys.keyId(PUBLISHER.getPublic()))
                .hasSize(16)
                .isNotEqualTo(SigningKeys.keyId(OTHER.getPublic()));
    }

    @Test
    void refusesFilesThatAreNotKeys() throws IOException {
        Path notAKey = Files.writeString(directory.resolve("x.pub.pem"), "hello");
        Path wrongKind = Files.writeString(
                directory.resolve("y.pub.pem"), "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----\n");
        Path malformed = Files.writeString(
                directory.resolve("z.key.pem"), "-----BEGIN PRIVATE KEY-----\n@@@\n-----END PRIVATE KEY-----\n");

        assertThatThrownBy(() -> SigningKeys.readPublic(notAKey)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> SigningKeys.readPublic(wrongKind)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> SigningKeys.readPrivate(malformed)).isInstanceOf(IOException.class);
    }

    private static final String REMOVE = "\u0000remove";

    private static Map<String, PublicKey> trusted(KeyPair pair) {
        return Map.of(SigningKeys.keyId(pair.getPublic()), pair.getPublic());
    }

    /** Rewrites the package: {@code change} gives new content, REMOVE, or null to keep an entry. */
    private void rewrite(UnaryOperator<String> change, String added) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(pkg.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String replacement = change.apply(entry.getName());
                if (REMOVE.equals(replacement)) {
                    continue;
                }
                if (replacement != null) {
                    files.put(entry.getName(), replacement);
                } else if (entry.isDirectory()) {
                    files.put(entry.getName(), null);
                } else {
                    try (InputStream in = zip.getInputStream(entry)) {
                        files.put(entry.getName(), new String(in.readAllBytes(), UTF_8));
                    }
                }
            }
        }
        if (added != null) {
            files.put(added, "added");
        }
        write(pkg, files);
    }

    private static void write(Path file, Map<String, String> files) throws IOException {
        try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : files.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() != null) {
                    out.write(entry.getValue().getBytes(UTF_8));
                }
                out.closeEntry();
            }
        }
    }
}
