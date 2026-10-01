# Compliance

What Mosaikit does, and does not yet do, for the rules that apply to software for the Italian
public administration: the AgID guidelines (acquisition and reuse, secure development,
accessibility, interoperability), the ACN rules for cloud services at level QC2, and the GDPR.

| Document | Content |
|---|---|
| [Compliance matrix](matrix.md) | Every measure, its status and the evidence that shows it |
| [Non-conformities](non-conformities.md) | What is missing, with severity, action and owner: the work list |
| [Evidence](evidence.md) | How to collect the evidence for a tender or an audit |
| [Secure development](secure-development.md) | The secure development life cycle of the project |

## Scope

- **The software.** The matrix covers what the code, the build and the documentation of this
  repository provide. It is the technical annex of a tender or of a qualification request.
- **The service.** The ACN qualification (determina ACN n. 21007/24, in force since 1 August
  2024) is granted to a cloud service, not to a software product: it is requested by the provider
  that runs Mosaikit as SaaS for public administrations, on a qualified infrastructure of the same
  level, and it needs organisational measures (ISO/IEC 27001 with 27017 and 27018, incident
  management, notification to the CSIRT) that are outside this repository. Those rows are marked
  *Provider* in the matrix.
- **On-premise and reuse.** An administration that installs Mosaikit itself, or reuses it, does
  not need the cloud qualification; the AgID guidelines on acquisition and reuse apply.

Level QC2 is the one for *critical* data. The individual measures must be read in the annex of
the regulation in force before answering a tender; the matrix quotes them in short and must be
checked against that text.

## Status values

| Status | Meaning |
|---|---|
| Compliant | Implemented, and verified by a test, a CI job or a document of this repository |
| Partial | Implemented in part; the non-conformity says what is missing |
| Non-compliant | Not implemented; a non-conformity describes the work |
| Provider | An obligation of whoever runs the service, outside the software |

## Keeping it current

The matrix is part of the definition of done: a merge request that implements or changes a
measure updates its row and closes or opens a non-conformity. The documents are in English, like
all technical documentation (ADR-0009); tender documents in Italian are derived from them.
