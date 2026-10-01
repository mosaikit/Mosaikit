// SPDX-FileCopyrightText: 2026 Massimo Antonini
// SPDX-License-Identifier: MPL-2.0
package dev.mosaikit.kernel.api.signature;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("MK-013")
class PackageSigningToolTest {

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void generatesAKeySignsAndVerifiesPackages() throws IOException {
        Path pkg = zip("notes-1.0.0.zip");
        Path keys = directory.resolve("keys");
        Path trusted = directory.resolve("trusted");

        assertThat(run("keygen", keys.toString(), "acme")).isZero();
        assertThat(run("verify", trusted.toString(), pkg.toString())).isEqualTo(1);
        assertThat(run("sign", keys.resolve("acme").toString(), pkg.toString())).isZero();
        assertThat(run("verify", trusted.toString(), pkg.toString())).isEqualTo(1);
        Files.createDirectories(trusted);
        Files.copy(keys.resolve("acme.pub.pem"), trusted.resolve("acme.pub.pem"));
        assertThat(run("verify", trusted.toString(), pkg.toString())).isZero();

        assertThat(out.toString(UTF_8))
                .contains("keep acme.key.pem secret")
                .contains("not signed")
                .contains("signed with the unknown key")
                .contains("verified, key");
    }

    @Test
    void reportsRefusedPackagesAndErrors() throws IOException {
        Path pkg = zip("notes-1.0.0.zip");
        Files.writeString(directory.resolve("broken.zip"), "not a zip");
        run("keygen", directory.toString(), "acme");
        run("sign", directory.resolve("acme").toString(), pkg.toString());

        assertThat(run(
                        "verify",
                        directory.toString(),
                        directory.resolve("broken.zip").toString()))
                .isEqualTo(1);
        assertThat(run("sign", directory.resolve("missing").toString(), pkg.toString()))
                .isEqualTo(1);
        assertThat(err.toString(UTF_8)).contains("Error:");
    }

    @Test
    void refusesATamperedPackage() throws IOException {
        Path pkg = zip("notes-1.0.0.zip");
        run("keygen", directory.toString(), "acme");
        run("sign", directory.resolve("acme").toString(), pkg.toString());
        Path copy = directory.resolve("copy.zip");
        try (var in = new java.util.zip.ZipFile(pkg.toFile());
                var zip = new ZipOutputStream(Files.newOutputStream(copy))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zip.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals("manifest.yaml")) {
                    zip.write("id: changed\n".getBytes(UTF_8));
                } else {
                    in.getInputStream(entry).transferTo(zip);
                }
                zip.closeEntry();
            }
        }

        assertThat(run("verify", directory.toString(), copy.toString())).isEqualTo(1);
        assertThat(out.toString(UTF_8)).contains("REFUSED");
    }

    @Test
    void signsEveryPackageOfADirectoryAndTrustsTheKey() throws IOException {
        Path first = zip("a-1.0.0.zip");
        Path second = zip("b-1.0.0.zip");
        Path trusted = directory.resolve("config/trusted-keys");
        run("keygen", directory.resolve("keys").toString(), "build");

        assertThat(run("sign", directory.resolve("keys/build").toString(), directory.toString()))
                .isZero();
        assertThat(run("trust", directory.resolve("keys/build").toString(), trusted.toString()))
                .isZero();

        assertThat(trusted.resolve("build.pub.pem")).exists();
        assertThat(run("verify", trusted.toString(), first.toString(), second.toString()))
                .isZero();
    }

    @Test
    void explainsItsUsage() {
        assertThat(run()).isEqualTo(2);
        assertThat(run("publish", "a", "b")).isEqualTo(2);
        assertThat(err.toString(UTF_8)).contains("usage");
    }

    private int run(String... args) {
        return PackageSigningTool.run(args, new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8));
    }

    private Path zip(String name) throws IOException {
        Path file = directory.resolve(name);
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("manifest.yaml"));
            zip.write("id: dev.example.notes\n".getBytes(UTF_8));
            zip.closeEntry();
        }
        return file;
    }
}
