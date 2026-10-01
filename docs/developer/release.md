# Release process

Mosaikit follows [Semantic Versioning](https://semver.org/). Every artifact of the repository
(Maven modules, npm workspaces, Helm chart) carries the same version. Between releases the
version ends with `-SNAPSHOT`.

## What a release publishes

A signed tag `vX.Y.Z` (or `vX.Y.Z-rc.N` for a pre-release) starts the release pipeline:

| Job | Publishes to | What |
|---|---|---|
| `release-check` | — | Fails unless every artifact is at `X.Y.Z` and `CHANGELOG.md` has a `## [X.Y.Z]` section |
| `container-image` | Container Registry | `$CI_REGISTRY_IMAGE:vX.Y.Z` built from `distributions/docker/Containerfile` |
| `portable` | Generic package registry | `mosaikit-portable-vX.Y.Z-<platform>.zip` / `.tar.gz` for Windows, Linux and macOS, tested on Linux by `portable-test` |
| `publish-maven` | Maven registry of the project | `mosaikit-parent` and `mosaikit-kernel-api`, the API that Java plugins compile against |
| `publish-npm` | npm registry of the project | `@mosaikit/sdk` (dist-tag `latest`, or `next` for pre-releases) |
| `publish-helm` | Helm registry of the project, channel `stable` | Chart `mosaikit-X.Y.Z.tgz` |
| `docs` | Generic package registry | `mosaikit-docs-vX.Y.Z.zip`: guides in Word and PDF, compliance workbook (Excel), release overview (PowerPoint), extended release notes, `SHA256SUMS` |
| `sign` | Generic package registry | cosign signature of the container image; `SHA256SUMS` of the portable archives and documents with its Sigstore bundle |
| `release` | Releases page | GitLab release with the `CHANGELOG.md` section and links to image, packages and SBOM |

All the verification, quality and security jobs of a normal pipeline run first: a release is
never published from a commit that does not pass them.

## Steps

The scripts run in Bash (Git Bash on Windows).

1. Make sure `main` is green and `CHANGELOG.md` lists the changes under `## [Unreleased]`.
2. Set the release version and date the changelog section:

   ```bash
   distributions/release/prepare.sh 0.2.0
   git diff                                # review
   git commit -s -am "chore(release): 0.2.0"
   ```

3. Tag and push. Tags are signed, so that the release is traceable to its author:

   ```bash
   git tag -s v0.2.0 -m "Mosaikit 0.2.0"
   git push origin main v0.2.0
   ```

4. Move back to a development version:

   ```bash
   distributions/release/prepare.sh 0.3.0-SNAPSHOT
   git commit -s -am "chore: start 0.3.0 development"
   git push origin main
   ```

`distributions/release/check-version.sh <version>` runs the same check as the pipeline, for
example before tagging.

## Documentation at every push

The documentation is part of the change ([ADR-0023](../adr/0023-documentation-and-release-documents.md)).
The pipeline fails when it no longer matches the code:

| Check | Job | Fails when |
|---|---|---|
| `DocumentationTest` | `backend` | an endpoint is missing from `api.md`, or documented but gone; a setting is missing from `configuration.md` |
| `docs/test` | `frontend` | a relative link is broken; an ADR is not in the index; the compliance documents name a test, requirement or non-conformity that does not exist |
| `docs/requirements/test` | `frontend` | a requirement breaks the schema, or is done without a test |
| `changelog` | merge requests | the product changes without a `CHANGELOG.md` entry (label `no-changelog` to skip) |
| `docs` | every pipeline | a document cannot be generated |

To build the documents on a workstation (pandoc, xelatex, `pip install openpyxl pyyaml`):

```bash
python3 docs/build/build.py --version 0.2.0-SNAPSHOT --output target/docs   # --no-pdf without LaTeX
```

## Verifying a release

```bash
# The container image, signed by the release pipeline of the project
cosign verify registry.gitlab.com/mosaikit/mosaikit:v0.2.0 \
  --certificate-identity-regexp 'https://gitlab.com/mosaikit/mosaikit//.gitlab-ci.yml@refs/tags/v.*' \
  --certificate-oidc-issuer https://gitlab.com
# The checksums of the archives and documents, then the files themselves
cosign verify-blob SHA256SUMS --bundle SHA256SUMS.sigstore.json \
  --certificate-identity-regexp 'https://gitlab.com/mosaikit/mosaikit//.gitlab-ci.yml@refs/tags/v.*' \
  --certificate-oidc-issuer https://gitlab.com
sha256sum --check --ignore-missing SHA256SUMS
```

## Consuming the published artifacts

Maven, in the `pom.xml` of a plugin:

```xml
<repositories>
    <repository>
        <id>mosaikit</id>
        <url>https://gitlab.com/api/v4/projects/PROJECT_ID/packages/maven</url>
    </repository>
</repositories>
```

npm, in the `.npmrc` of a plugin frontend:

```
@mosaikit:registry=https://gitlab.com/api/v4/projects/PROJECT_ID/packages/npm/
```

Helm:

```bash
helm repo add mosaikit https://gitlab.com/api/v4/projects/PROJECT_ID/packages/helm/stable
helm install mosaikit mosaikit/mosaikit --version 0.2.0
```

Private projects need a token: a deploy token or a personal access token with `read_api`.
