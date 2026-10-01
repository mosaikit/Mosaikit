# 0014. Signed plugin packages with Ed25519

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0004](0004-plugin-model-restart-and-packages.md)

## Context and problem statement

Plugin packages (`<name>.zip`, ADR-0004) run code in the kernel and in the browsers of its users.
An installation must be able to tell a package from a trusted publisher from one that anybody
could have changed, and some installations (public administration, critical data) must accept
only the former (MK-013). The check must work offline, in the portable distribution as in a
container, and must not need a new format for the packages that exist today.

## Considered options

1. Sigstore/cosign signatures on OCI artifacts: strong, but checking them needs the registry or a
   transparency log, and plugins copied as files into `plugins/` would lose their signature.
2. JAR signing (`jarsigner`): made for JARs, with X.509 certificates, and it covers only the
   entries that the JAR format knows.
3. A signature inside the zip: a list of the SHA-256 digests of every file, signed with Ed25519
   (part of every Java platform), checked against a directory of trusted public keys.

## Decision

Option 3.

- A signed package carries `META-INF/mosaikit/digests.txt` (`<sha-256>  <entry>` for every file,
  sorted) and `META-INF/mosaikit/signature.properties` (`algorithm=Ed25519`, `key=<key id>`,
  `signature=<Base64>` of the digests file). The key identifier is the first 16 hexadecimal digits
  of the SHA-256 of the public key.
- `dev.mosaikit.kernel.api.signature` in the Java plugin API signs and verifies packages, and
  `PackageSigningTool` gives publishers `keygen`, `sign`, `verify` and `trust`. The kernel, the
  launcher and the tool use the same code.
- An installation trusts the public keys (`*.pub.pem`) of `mosaikit.plugins.trusted-keys-directory`
  (`config/trusted-keys`). With `mosaikit.plugins.signatures=optional` (the default) a package
  whose content does not match its signature is refused, and unsigned packages and plugin
  directories are accepted with an unverified publisher. With `required`, only packages signed
  with a trusted key are accepted.
- The build signs the sample packages with `-Dmosaikit.signing.key=<directory>/<name>`, or with a
  key generated for the build, and puts the public key into `config/trusted-keys` of the
  installation, so that every distribution trusts the plugins it ships.
- Packages keep the `.zip` extension. A registry stores them as OCI artifacts unchanged (for
  example with `oras push … <name>.zip:application/vnd.mosaikit.plugin.v1+zip`), and the signature
  travels inside the file, so a package pulled from a registry or copied by hand is checked the
  same way.

## Consequences

- Publishers must keep their private key secret; a leaked key is revoked by removing its public
  key from the installations. There is no revocation list or expiry yet.
- The verified publisher is part of the plugin status (`publisherKey` in `/api/v1/plugins`), which
  lets the shell run the frontends of unverified publishers in an isolated iframe (MK-014).
- Per-package SBOMs are not generated yet; the SBOM of the build covers the sample plugins.
