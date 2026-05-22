# AICoherent Dialog Threads + RAG — Context Document

## Request

> now have a role of architect. analyse @docs/ApiLabThread.txt and create Quasar UI
> frontend with dialog threads on the left sidebar merged with current left sidebar
> elements intact; create a RAG system over all dialog threads that uses RAG when
> communicating with given system tenant from UI or otherwise; RAG must be done via
> Adapter pattern for future extensibility of RAG stuff.

## Analysis of `ApiLabThread.txt`

The document describes a concept called **AICoherent** — a cybernetic personal cognition substrate built around a user-owned conversational state forest. Key architectural principles from the document:

### Core Thesis
- Every user has a single, portable, fully addressable AI state graph
- The system is a closed-loop cybernetic system over a user-owned state graph
- All state transitions are observable, selectively auditable, and governed by bounded-loss compression rules
- Memory is hierarchical (hot/warm/cold/frozen) rather than flat
- Agents operate on compressed semantic projections of dialog ecology, not raw history

### Memory Tiers
- **HOT**: Recent dialogs, current working threads, active agents, live tasks (TTL-oriented)
- **WARM**: Summaries, topic abstractions, stable entities, distilled patterns (main RAG target)
- **COLD**: Raw imports, historical dialogs, old branches, audit trails (rarely retrieved)
- **FROZEN**: Externalized storage (IPFS, i2p, S3, Solid, NAS) — only indexed locally

### Multi-Tenant Isolation
- Each tenant is an autonomous cybernetic namespace with its own dialog forest, graph, policies
- Infrastructure may be shared; cognition substrates may not
- Agents NEVER get global omniscience or cross-tenant retrieval
- Retrieval is per-tenant: separate embedding collections, graph indexes, summaries, ranking
- Adapter isolation required for all external integrations

### RAG as Observation Mechanism
RAG is not a separate feature — it is the perception/observation mechanism of the cybernetic loop:
- Retrieval is perception
- Summarization is memory consolidation
- Embeddings are sensory indexes
- Adapters are external sensory organs

### Competitive Positioning
- Not "AI chat app" but "personal AI operating substrate"
- Infrastructure/platform positioning survives better than app wrappers in AI era
- Target first: developers, AI power users, self-hosters, privacy people, researchers
- Wedge product: "Universal AI memory that survives switching models"

### Probable Future Stack Mentioned
| Layer | Tech |
|-------|------|
| UI | Quasar |
| graph | SQLite/Postgres + edge tables |
| embeddings | Qdrant/pgvector |
| event streams | append-only log |
| adapters | MCP-ish connectors |
| agents | orchestrated runtimes |
| compression | scheduled summarizers |
| sync | CRDT/event hybrid |
| auth | user-owned identity |

## Derived User Intent

The user wants a **pragmatic first implementation** that captures the AICoherent vision with:

1. **Dialog threads in the Quasar sidebar** — merged alongside existing navigation, not replacing it
2. **RAG over all user dialog threads** — when chatting, retrieve relevant context from all previous conversations
3. **Adapter pattern for RAG** — starts simple (keyword/in-memory), swap-in future backends (pgvector, Pinecone) without changing callers
4. **Tenant isolation** — dialog threads belong to users, one user can't access another's threads
5. **Streaming AI responses** — chat interface should show tokens as they arrive

## Current System State (for context)

### Frontend (Quasar Vue 3.3)
- `MainLayout.vue` has a left drawer with static navigation items (Home, Users, Articles, Create Article, Generation Jobs, Settings, Logout)
- No chat/dialog components exist
- Single Pinia auth store with role-based access (admin, maintainer, logged-in)
- Raw `fetch()` for API calls

### Backend (Java 17, Spring Boot 2.7, PostgreSQL)
- Article generation pipeline: topics → research → articles → review → publish
- `LlmLoopEngine.java` already implements MCP tool calling (OpenSERP websearch + webpage_fetch)
- `BotWebSocketHandler.java` handles IRC/Telegram bot AI queries at `/ws/bot`
- No dialog/thread/message, tenant, or RAG code exists
- No vector database or embeddings

### Infrastructure
- Docker Compose with PostgreSQL, Redis, auth sidecar, OpenSERP MCP sidecar, readingplus MCP
- Production on rig1.lan with blue-green deployment
- Dev on localhost, PostgreSQL, no blue-green

## Key Decisions in the Plan

### RAG Adapter
- **Default**: `InMemoryRAGAdapter` — keyword/TF-IDF inverted index over dialog messages, zero external dependencies, thread-safe `ConcurrentHashMap`
- **Future**: `PgvectorRAGAdapter` — pgvector PostgreSQL extension, profiles-based activation
- **Why**: The user asked for Adapter pattern. The simplest correct implementation is keyword-based TF-IDF with an clean interface that a future embedding/ANN backend can drop into.

### Database Schema
- `dialog_threads(id, user_id, title, status, model_name, system_prompt, created_at, updated_at, last_message_at)`
- `dialog_messages(id, thread_id, role, content, tool_name, tool_result_jsonb, tokens_used, created_at)`
- Indexed for user-scoped queries, thread message ordering, last message for thread list sorting

### Sidebar Integration
- Dialog threads go below existing nav items, separated by a `q-separator`
- Thread list sorted by `last_message_at DESC`
- Each thread shows truncated title + last message timestamp
- "New Thread" button at top of the section

### Backend Architecture
- `DialogService` — thread lifecycle, message persistence, LLM calling
- `RAGService` — prompt enhancement via RAG adapter
- `DialogChatController` — SSE streaming (`Flux<ServerSentEvent>`) for real-time AI response

### Frontend Architecture
- `useDialogStore` (Pinia) — reactive thread list, current thread, messages
- `DialogThreadPage` — full-page chat with `q-chat-message`, `q-scroll-area`, message input
- `useDialogSSE` composable — EventSource for SSE token streaming
