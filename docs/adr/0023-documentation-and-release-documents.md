# 0023. Documentation checked at every push, release documents generated from it

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0008](0008-requirements-and-documentation-as-code.md) and
  [ADR-0009](0009-english-for-code-and-technical-documents.md)

## Context and problem statement

The documentation is Markdown next to the code (ADR-0008). Documentation that nobody checks
drifts from the product; and tenders, audits and administrations ask for documents they can file
and read without GitLab: Word, PDF, Excel and slides, one set for each release.

## Decision

- **Checked at every push.** Tests fail when the documentation no longer matches the repository:
  - `DocumentationTest` (backend job): every endpoint of the OpenAPI of the kernel is in
    `docs/developer/api.md` and every documented one exists; every setting of `KernelConfig` is in
    `docs/user/configuration.md`;
  - `docs/test` (frontend job): every relative link of every Markdown file resolves; every ADR is
    in the index; the tests, requirements and non-conformities named as evidence in the compliance
    documents exist; the changelog has an `Unreleased` section;
  - the requirements tests of ADR-0008 (schema, and a test for every requirement marked done);
  - on merge requests, the `changelog` job refuses a change of the product without a
    `CHANGELOG.md` entry, unless the label `no-changelog` says users do not see it.
- **Documents generated, never edited.** `docs/build/build.py` (pandoc, xelatex, openpyxl) builds
  from the Markdown and the YAML, for every commit (job `docs`, artifact for a week) and for every
  release (uploaded and linked from the release):
  - the user, developer and compliance guides in Word and PDF, and the architecture decisions in
    Word;
  - an Excel workbook with the compliance matrix, the non-conformities and the requirements with
    the tests that verify them;
  - a PowerPoint overview of the release: what changed, requirements, compliance, open
    non-conformities;
  - release notes extended with the state of requirements and non-conformities;
  - `SHA256SUMS` of all of them, in `mosaikit-docs-<version>.zip`.
- **Signed releases.** The `sign` job signs the container image and the checksums of the portable
  archives and documents with cosign, keyless with the OIDC identity of the pipeline (Sigstore),
  and the release links the signatures.

## Consequences

- A change of the API, of a setting or of the evidence of a measure without its documentation
  fails the pipeline where it is made.
- Word and PDF follow the Markdown exactly, with the default styles of pandoc; a corporate
  template (`--reference-doc`) can be added without changing the sources. Documents in Italian for
  tenders are still derived from these (ADR-0009).
- Verifying a release needs cosign and the identity of the project pipeline, documented in the
  release guide.

## Alternatives considered

- **A documentation site (Docusaurus, MkDocs) only.** Good to read online, but tenders and audits
  need files; a site can still be generated from the same Markdown.
- **Documents kept by hand in Word.** They drift at the first release and duplicate the work.
