# 0008. Requirements and documentation as code

- Status: accepted
- Date: 2026-09-29
- Deciders: Massimo Antonini

## Decision

- Requirements live in `requirements/` as YAML files with a stable id (`MK-xxx`), acceptance
  criteria and status. Issues and merge requests reference the id.
- Tests carry the requirement they verify (`@Tag("MK-xxx")` in Java), so that CI can report
  coverage by requirement.
- Documentation is Markdown in `docs/`. The published site will use Docusaurus 3, because
  MkDocs Material reaches end of life on 5 November 2026 and its successor is not stable yet.
