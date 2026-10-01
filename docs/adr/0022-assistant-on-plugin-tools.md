# 0022. An assistant on the tools of the plugins, with any OpenAI-compatible model

- Status: accepted
- Date: 2026-09-30
- Deciders: Massimo Antonini
- Extends [ADR-0017](0017-ai-tools-drafts-and-audit.md); prototype spike S10

## Context and problem statement

MK-015 made the actions of the plugins available as tools, to MCP clients such as Claude. People
without an MCP client, and installations that must keep data on the premises, need an assistant in
the shell, with a model the administration chooses: a local one (Ollama, vLLM) or a hosted one.

## Decision

- The kernel talks to a model through the OpenAI chat completions API with tool calling, the de
  facto standard of local and hosted models: `mosaikit.assistant.url`, `model`, `api-key`. No
  model is bundled; without a URL the assistant is off.
- `POST /api/v1/ai/assistant/replies` takes the conversation (user and assistant messages only,
  bounded in number and length) and runs a loop of at most `mosaikit.assistant.max-steps` rounds:
  the model receives a system prompt, the conversation and the tools of the active plugins; each
  tool call goes through `AiActions` like a call of an MCP client, with the rights of the person
  in the organization of the request. Read tools run; write and execute tools become drafts; errors
  go back to the model as tool results.
- The shell shows an assistant panel on the home page when an assistant is configured; drafts it
  creates appear under Pending actions.
- The conversation is not stored by the kernel; the tool runs and drafts are audited (ADR-0017).

## Consequences

- The same tools, drafts and audit serve the shell assistant and MCP clients.
- With a local model, no data of the organization leaves the installation; with a hosted one, the
  conversation and the results of read tools go to that service, which the administration must
  assess (C-36, C-45 of the compliance matrix).
- The quality of answers depends on the model: tools need clear descriptions and schemas.
- A system role, prompts of other people and tool definitions cannot be injected through the API:
  only user and assistant messages are accepted.

## Alternatives considered

- **An agent framework (LangChain4j, Quarkus LangChain4j).** More features, but an extension to
  add to the kernel of every installation and a second abstraction over the same tools.
- **MCP only.** Enough for developers and power users, not for the people who work in the apps.
