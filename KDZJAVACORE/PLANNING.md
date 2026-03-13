# Deep Research Upgrade: Websearch MCP

## Goal
- Select an appropriate web-search service provider.
- Install a self-hosted MCP server that exposes a `websearch` tool.
- Provide this tool to the LLM loop so deep research runs as iterative tool-assisted reasoning rather than single-shot final answers.

## Provider Selection Criteria
- Relevance quality for mental-health and evidence-oriented queries.
- Latency and reliability under expected concurrent load.
- Cost model (per-request, per-result, burst pricing) and monthly cap predictability.
- API limits, quota behavior, and rate-limit headers for robust retry/backoff logic.
- Compliance and legal terms (usage rights, caching allowances, citation requirements, privacy controls).
- Selected provider and MCP components must be maximally mature for production reliability.
- Prefer open-source solutions whenever a mature option is available.

## Self-Hosted MCP Websearch Deployment
- Run a containerized MCP websearch server in dev/prod alongside backend services.
- Define environment variables and secrets handling for provider keys and region settings.
- Add health checks and startup readiness checks to avoid tool-call hangs.
- Add operational logs for request IDs, timing, failures, and provider fallback events.

## Integration Checkpoints
- Configure `DeepResearchService` to include the websearch MCP server in the tool list.
- Ensure `LlmLoopEngine` exposes the `websearch` tool to the model and supports multi-step calls.
- Keep trace logs showing tool selection, input arguments, and returned evidence snippets.
- Ensure resulting article research fields are populated from cited web evidence, not only model prior knowledge.

## Validation Criteria
- Citations include fresher sources and higher authority domains.
- Reduced hallucinated facts in sampled deep-research outputs.
- Improved evidence completeness (quotes, statistics, and source lists).
- Trace logs confirm iterative `websearch` tool usage before final answer.

## Rollout Phases
1. PoC in dev with one provider and one MCP websearch server.
2. Staging evaluation with fixed benchmark prompts and quality scoring.
3. Production rollout with monitoring, alerting, and rollback path.
