# OpenSERP + Websearch MCP Integration Plan

## Goal
- Install an OpenSERP sidecar service with an API exposed to the internal Docker network.
- Add an `OpenSERPConnector` to the generic backend.
- Plan and integrate a websearch MCP tool provider for `DeepResearchService`.
- Move deep research from single-shot completion to iterative tool-assisted research.

## OpenSERP Sidecar Deployment
- Add an OpenSERP sidecar container to dev/prod compose definitions.
- Expose OpenSERP API on Docker network using a stable service hostname.
- Configure secrets via environment variables (API key, provider settings, optional region).
- Add health/readiness checks so backend only calls OpenSERP when sidecar is ready.
- Add restart policy and basic resource limits for operational stability.

## Generic Backend: OpenSERPConnector
- Create `OpenSERPConnector` under `com.localmesalevel.aisystemtakeone` in generic backend.
- Define typed request/response models for search query, filters, and normalized results.
- Implement timeout, retry with backoff, and bounded response-size handling.
- Add structured trace logs for request id, latency, result count, and failure reason.
- Add explicit error mapping so upstream services receive actionable failure messages.

## Websearch MCP Provider Plan
- Evaluate whether MCP provider should call OpenSERP directly or through `OpenSERPConnector`.
- Introduce `WebSearchProvider` interface as the stable abstraction for all search backends.
- Add provider implementations (for example `OpenSerpWebSearchProvider`) behind that interface.
- Add `WebSearchProviderFactory` that centralizes constructor calls for various provider implementations.
- Ensure factory can select provider by config/environment and wire provider-specific dependencies.
- Define tool contract for `websearch` (input schema, limits, output shape, citation fields).
- Ensure MCP tool response includes source URLs, titles, snippets, and timestamps.
- Add provider selection checklist: relevance quality, latency, quota behavior, cost, legal terms.
- Prefer maximally mature and preferably open-source components where feasible.

## DeepResearchService Integration Checkpoints
- Refactor `DeepResearchService` so each instance receives `WebSearchProvider` via constructor injection.
- Ensure `DeepResearchService` passes the injected `WebSearchProvider` to the underlying `LlmConnector`.
- Add websearch MCP server configuration to deep-research tool list.
- Ensure `LlmLoopEngine` advertises and invokes `websearch` in multi-step loops.
- Capture tool-call traces in logs: arguments, selected provider path, and summarized outputs.
- Populate research artifacts from tool evidence (citations, sources, statistics, quotes).
- Keep deterministic fallback behavior when websearch tool is temporarily unavailable.

## Operational and Validation Criteria
- Connectivity: backend can resolve and call OpenSERP sidecar by Docker hostname.
- Reliability: retries/backoff prevent transient failures from terminating jobs immediately.
- Quality: sampled outputs show fresher, better-cited evidence than single-shot baseline.
- Safety: logs avoid secret leakage while preserving observability for troubleshooting.
- Performance: enforce per-call timeout and max tool iterations to protect throughput.

## Rollout Plan
1. PoC in dev with OpenSERP sidecar and one MCP websearch tool path.
2. Staging validation against benchmark prompts and quality scoring.
3. Production rollout with monitoring, alerts, and documented rollback steps.
