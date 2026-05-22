# AICoherent Dialog Threads + RAG System Plan

## Context

Build on the existing AI Content Generator system to implement an **AICoherent-style dialog forest** — a user-owned conversational state graph. The system evolves from a simple article generation pipeline to a multi-tenant cybernetic substrate where each user owns autonomous dialog threads. RAG (Retrieval-Augmented Generation) is used transparently when the user interacts with the AI, with extensibility via the Adapter pattern for future RAG backends (pgvector, Pinecone, etc).

---

## Current State

### Frontend (Quasar Vue 3.3, Pinia)
- `MainLayout.vue`: Left drawer with static nav items (Home, Users, Articles, etc.)
- `auth.ts` Pinia store: Single reactive auth state, role-based access (admin/maintainer)
- Raw `fetch()` for API calls; no reusable API service layer
- No chat/messaging, dialog, or thread UI exists
- No tenant concept

### Backend (Java 17, Spring Boot 2.7, PostgreSQL, Flyway)
- Articles, topics, reviews, LLM endpoints, users tables exist
- `LlmLoopEngine.java`: MCP multi-step tool calling (OpenSERP websearch + webpage_fetch)
- `BotWebSocketHandler.java`: WebSocket `/ws/bot` for IRC/Telegram bot AI queries
- No dialog/thread/message entities, no vector DB, no embedding code

---

## Architecture Overview

### Backend: Dialog Thread & RAG Core

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        AICoherent Backend                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────┐   ┌──────────────────┐   ┌───────────────────┐  │
│  │ DialogThread      │   │ DialogMessage      │  │ LlmLoopEngine      │  │
│  │  - id, tenantId │   │  - id, threadId    │   │  - MCP tools       │  │
│  │  - title, status│   │  - role, content   │   │  - RAG Adapter     │  │
│  │  - createdAt    │   │  - createdAt       │   │  - streaming       │  │
│  └────────┬────────┘   └────────┬──────────┘   └────────┬──────────┘  │
│           │                    │                       │            │
│           └────────────────────┴───────────────────────┘            │
│                                        │                            │
│                              ┌─────────▼─────────┐                  │
│                              │ RAG Adapter System │                  │
│                              │  - InMemoryRAG     │                  │
│                              │  - PgvectorRAG     │                  │
│                              │  - PineconeRAG     │                  │
│                              └─────────┬─────────┘                  │
│                                        │                            │
│                                 ┌──────▼──────┐                     │
│                                 │ Embedding   │                     │
│                                 │ Service     │                     │
│                                 │ (extract)   │                     │
│                                 └─────────────┘                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Frontend: Dialog Threads in Sidebar

```
┌──────────────────────────────────────────────────────────────────┐
│  AI Content Generator             User (role)   ┃               │
├─────────────────────────────────────────────────┨               │
│  🏠 Home                                          ┃   Page View   │
│  👥 Users               ▼                       ┃               │
│  📄 Articles List                                 ┃               │
│  ✨ Create Article                               ┃               │
│  📋 Generation Jobs                               ┃               │
│  ───────────────────────────────────────────────  ┃               │
│  🗨️ Dialog Threads                   [+ New]      ┃               │
│     ▼ thread1 "Code review"                       ┃               │
│     ☐ thread3 "Architecture"                    ┃               │
│  ⚡ Settings                                      ┃               │
│  ⬅️ Logout                                        ┃               │
├───────────────────────────────────────────────────┤   ChatPage    │
│                                                   │               │
│  (dialog thread chat view                         │               │
│   with message list + input)                      └───────────────┘
│
```

---

## Implementation Phases

### Phase 1: Backend — Dialog Thread Entities & API

#### 1.1 Database Migration
**File:** `generic_backend/src/main/resources/db/migration/V12__dialog_threads.sql`

```sql
-- Dialog thread table
CREATE TABLE dialog_threads (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'active',
    model_name VARCHAR(255),
    system_prompt TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    last_message_at TIMESTAMP
);

-- Dialog message table
CREATE TABLE dialog_messages (
    id BIGSERIAL PRIMARY KEY,
    thread_id BIGINT NOT NULL REFERENCES dialog_threads(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL, -- 'user', 'assistant', 'system', 'tool'
    content TEXT NOT NULL,
    tool_name VARCHAR(255), -- for tool_call role
    tool_result JSONB, -- for tool response
    tokens_used INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dialog_threads_user ON dialog_threads(user_id);
CREATE INDEX idx_dialog_threads_last_msg ON dialog_threads(last_message_at DESC);
CREATE INDEX idx_dialog_messages_thread ON dialog_messages(thread_id, created_at);
```

#### 1.2 Java Entities
**File:** `generic_backend/src/main/java/.../dialog/model/DialogThread.java`
- JPA `@Entity` mapping to `dialog_threads`
- Fields: `id`, `userId`, `title`, `status`, `modelName`, `systemPrompt`, `createdAt`, `updatedAt`, `lastMessageAt`
- `@OneToMany` to `DialogMessage`

**File:** `generic_backend/src/main/java/.../dialog/model/DialogMessage.java`
- JPA `@Entity` mapping to `dialog_messages`
- Fields: `id`, `threadId`, `role`, `content`, `toolName`, `toolResult` (JSONB via `JsonNode`), `tokensUsed`, `createdAt`
- `@ManyToOne` to `DialogThread`

#### 1.3 Repositories
- `DialogThreadRepository.java` - extends `JpaRepository`, add `findByUserIdOrderByLastMessageAtDesc()`
- `DialogMessageRepository.java` - `findByThreadIdOrderByCreatedAtAsc()`, `countByThreadId()`

#### 1.4 Service Layer
**File:** `generic_backend/src/main/java/.../dialog/service/DialogService.java`

```java
@Service
public class DialogService {
    // Create thread with title auto-generated from first user message
    DialogThread createThread(Long userId, String title, String systemPrompt);
    
    // Add message (user/assistant/tool), update thread timestamp
    DialogMessage addMessage(Long threadId, DialogMessageRequest request);
    
    // Get thread messages for display
    List<DialogMessage> getMessages(Long threadId);
    
    // Delete thread
    void deleteThread(Long threadId, Long userId);
    
    // List threads for user
    List<DialogThread> listThreads(Long userId);
}
```

#### 1.5 REST Controllers
**File:** `generic_backend/src/main/java/.../dialog/controller/DialogController.java`

```java
@/api/dialogs
  GET    /                → list user threads  (auth)
  POST   /                → create new thread    (auth)
  DELETE /{id}            → delete thread        (own thread)
  GET    /{id}/messages   → fetch messages       (own thread)
  POST   /{id}/messages   → send message         (own thread) → returns streaming AI response
```

#### 1.6 Streaming AI Chat Endpoint
**File:** `generic_backend/src/main/java/.../dialog/controller/DialogChatController.java`
- `POST /api/dialogs/{id}/chat` with SSE (Server-Sent Events) for streaming AI responses
- Uses existing `LlmLoopEngine.run()` with MCP tool calling
- On user message: persist → call LLM with MCP + RAG context → stream response tokens → store full response as `DialogMessage` with `role='assistant'`
- Build message history from `DialogMessage` table for context

---

### Phase 2: Backend — RAG Adapter System

#### 2.1 Adapter Interface
**File:** `generic_backend/src/main/java/.../rag/adapter/RAGAdapter.java`

```java
public interface RAGAdapter {
    String getName();
    boolean isAvailable();
    void indexDialogMessage(DialogMessage message);
    void indexAllThreadMessages(Long threadId);
    List<RAGResult> retrieveRelevantContext(String query, long userId, int maxResults);
    void deleteThreadIndex(Long threadId);
}
```

**File:** `generic_backend/src/main/java/.../rag/model/RAGResult.java`
```java
public class RAGResult {
    private String content;      // Retrieved text
    private String sourceType;   // "dialog", "article", "document"
    private long sourceId;       // e.g., dialog_message_id
    private double relevance;    // 0.0 - 1.0
    private String citation;     // Formatted citation
}
```

#### 2.2 In-Memory RAG Adapter (Default)
**File:** `generic_backend/src/main/java/.../rag/adapter/InMemoryRAGAdapter.java`

- Simple inverted index over all dialog messages
- Uses Apache Commons Text for TF-IDF scoring (lightweight, no external vector DB)
- Indexes: `Map<Long, Map<String, Double>>` per user (threadId → term → score)
- No embedding model needed — keyword + frequency based
- Fast to implement, zero additional dependencies

```java
@Component
public class InMemoryRAGAdapter implements RAGAdapter {
    // ConcurrentHashMap for thread-safety
    // No external services required
}
```

#### 2.3 Pgvector RAG Adapter (Future)
**File:** `generic_backend/src/main/java/.../rag/adapter/PgvectorRAGAdapter.java`

- Requires: `pgvector` PostgreSQL extension
- Stores embeddings in `dialog_message_embeddings` table with `vector(1536)`
- Uses `pg_similarity` or `pgvector`'s `<->` operator for ANN
- **Not part of MVP** — mark as `@Profile("pgvector")`

#### 2.4 Adapter Registry / Factory
**File:** `generic_backend/src/main/java/.../rag/RAGAdapterFactory.java`

```java
@Service
public class RAGAdapterFactory {
    public RAGAdapter getPrimaryAdapter() {
        // Returns the first available adapter
        // Priority: config-driven, defaults to InMemory
    }
}
```

#### 2.5 RAG Service Integration
**File:** `generic_backend/src/main/java/.../rag/service/RAGService.java`

- When user sends message in dialog: `RAGService.enhancePrompt(query, userId)` → retrieves relevant context from all user dialog threads
- Prepends RAG context to system prompt:
  ```
  [Relevant context from previous conversations:
   - "...extracted text..." (from thread "X")
   - "...extracted text..." (from thread "Y")
  ]
  
  User query: {actual_user_query}
  ```
- This context is transient — not stored, only used for the current LLM call

---

### Phase 3: Frontend — Dialog Threads UI

#### 3.1 Store: `useDialogStore.ts`
**File:** `frontend/src/stores/dialog.ts`

```typescript
export const useDialogStore = defineStore('dialog', () => {
  const threads = ref<DialogThread[]>([]);
  const currentThread = ref<DialogThread | null>(null);
  const messages = ref<DialogMessage[]>([]);
  const isLoading = ref(false);
  
  const fetchThreads = async () => { ... };
  const createThread = async (title: string) => { ... };
  const deleteThread = async (id: number) => { ... };
  const sendMessage = async (content: string) => { ... };
  const selectThread = async (id: number) => { ... };
  
  return { threads, currentThread, messages, isLoading, 
           fetchThreads, createThread, deleteThread, sendMessage, selectThread };
});
```

#### 3.2 Modify `MainLayout.vue` — Add Dialog Threads to Sidebar
**File:** `frontend/src/layouts/MainLayout.vue`

```vue
<q-drawer v-model="leftDrawerOpen" show-if-above bordered>
  <q-list>
    <!-- Existing navigation items -->
    <q-item ... to="/" ...>Home</q-item>
    <q-item ... to="/admin/users" ...>Users</q-item>
    <!-- ... keep all existing -->

    <!-- Divider -->
    <q-separator spaced />
    <q-item-label header class="text-weight-bold text-uppercase text-caption">
      Dialog Threads
    </q-item-label>

    <!-- New Thread Button -->
    <q-item clickable v-ripple @click="onNewThread">
      <q-item-section avatar><q-icon name="add" color="primary" /></q-item-section>
      <q-item-section class="text-primary">New Thread</q-item-section>
    </q-item>

    <!-- Thread List -->
    <q-item 
      v-for="thread in dialogStore.threads" 
      :key="thread.id"
      clickable v-ripple
      clickable v-ripple
      @click="onSelectThread(thread.id)">
      <q-item-section avatar>
        <q-icon name="chat_bubble" />
      </q-item-section>
      <q-item-section>
        {{ thread.title }}
        <q-item-label caption>{{ thread.lastMessageAt ? formatDate(thread.lastMessageAt) : 'New' }}</q-item-label>
      </q-item-section>
      <q-item-section side>
        <q-btn flat round dense icon="close" size="sm" @click.stop="onDeleteThread(thread.id)" />
      </q-item-section>
    </q-item>
  </q-list>
</q-drawer>
```

#### 3.3 New Page: `DialogThreadPage.vue`
**File:** `frontend/src/pages/DialogThreadPage.vue`

Full-page chat interface:

```vue
<template>
  <q-page class="column">
    <!-- Thread header -->
    <q-bar class="bg-primary text-white">
      <div class="q-px-md q-py-sm text-weight-bold">
        {{ dialogStore.currentThread?.title || 'New Thread' }}
      </div>
    </q-bar>
    
    <!-- Messages -->
    <q-scroll-area class="col">
      <div v-for="msg in dialogStore.messages" :key="msg.id" 
           :class="['q-pa-sm', msg.role === 'user' ? 'text-right bg-blue-1' : 'text-left']">
        <q-chat-message
          :name="msg.role"
          :text="[msg.content]"
          :sent="msg.role === 'user'"
          :bg-color="msg.role === 'user' ? 'primary' : 'grey-3'"
          text-color="white" />
      </div>
      <q-spinner v-if="dialogStore.isLoading" color="primary" size="2em" />
    </q-scroll-area>
    
    <!-- Input -->
    <q-input v-model="newMessage" @keyup.enter="send"
             label="Type a message..." outlined>
      <template v-slot:append>
        <q-btn icon="send" @click="send" :disable="!newMessage.trim()" />
      </template>
    </q-input>
  </q-page>
</template>
```

#### 3.4 Update Router
**File:** `frontend/src/router/index.ts` — Add:

```typescript
{
  path: 'dialogs',
  component: () => import('pages/DialogThreadPage.vue'),
  meta: { roles: ['admin', 'maintainer'] },
},
{
  path: 'dialogs/:id',
  component: () => import('pages/DialogThreadPage.vue'),
  meta: { roles: ['admin', 'maintainer'] },
}
```

---

### Phase 4: Backend Integration — RAG into LLM Loop

#### 4.1 Modify `DialogService.java` — Chat Flow

```java
public class DialogService {
    
    public DialogMessage sendChatMessage(Long threadId, String userContent) {
        // 1. Persist user message
        DialogMessage userMsg = addMessage(threadId, "user", userContent);
        
        // 2. Retrieve RAG context for this user
        List<DialogMessage> history = getMessages(threadId);
        List<RAGResult> ragContext = ragService.retrieveRelevantContext(
            userContent, currentUserId, 5
        );
        
        // 3. Build system prompt with RAG context
        String systemPrompt = buildSystemPromptWithRAG(ragContext, thread);
        
        // 4. Call LLM with MCP tools + history + RAG
        List<DialogMessage> messageHistory = getMessagesForLLM(threadId);
        String aiResponse = llmLoopEngine.run(
            credentials, model, systemPrompt, userContent, mcpServers, maxSteps
        );
        
        // 5. Persist assistant response
        DialogMessage assistantMsg = addMessage(threadId, "assistant", aiResponse);
        
        // 6. Index new messages for RAG
        ragService.indexMessage(userMsg);
        ragService.indexMessage(assistantMsg);
        
        return assistantMsg;
    }
}
```

#### 4.2 SSE Streaming Chat Controller
**File:** `DialogChatController.java` — `POST /api/dialogs/{id}/chat/stream`

```java
@PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamChat(...) {
    return dialogService.streamChatResponse(threadId, request)
        .log()
        .onErrorResume(e -> Flux.just(...));
}
```

---

### Phase 5: Testing

#### Backend Tests
- `DialogRepositoryTest` — CRUD for threads and messages
- `DialogServiceTest` — Thread lifecycle, message ordering, RAG integration
- `DialogControllerTest` — Auth boundaries, thread ownership
- `InMemoryRAGAdapterTest` — TF-IDF retrieval accuracy
- `RAGServiceTest` — Prompt enhancement with RAG context
- `DialogChatIntegrationTest` — Full flow: create thread → send message → AI response → RAG retrieval → streaming tokens

#### Frontend Tests
- `DialogThreadPage.spec.ts` — Chat UI interactions, message rendering
- `useDialogStore.spec.ts` — Store actions and reactivity
- E2E with Playwright: Thread creation → messaging → RAG-augmented responses

---

## Critical Files to Modify/Create

### Backend (Java)
**New:**
1. `migration/V12__dialog_threads.sql` — DB tables
2. `dialog/model/DialogThread.java` — Entity
3. `dialog/model/DialogMessage.java` — Entity
4. `dialog/repository/DialogThreadRepository.java`
5. `dialog/repository/DialogMessageRepository.java`
6. `dialog/service/DialogService.java` — Core business logic
7. `dialog/controller/DialogController.java` — REST API
8. `dialog/controller/DialogChatController.java` — SSE streaming
9. `rag/adapter/RAGAdapter.java` — Interface
10. `rag/adapter/InMemoryRAGAdapter.java` — Default implementation
11. `rag/model/RAGResult.java`
12. `rag/service/RAGService.java` — Retrieval & prompt enhancement
13. `rag/RAGAdapterFactory.java`

### Frontend (Vue 3/Quasar/TypeScript)
**New:**
1. `src/stores/dialog.ts` — Pinia store for dialog threads
2. `src/pages/DialogThreadPage.vue` — Chat thread page
3. `src/composables/useDialogSSE.ts` — SSE streaming composable

**Modified:**
1. `src/layouts/MainLayout.vue` — Add dialog threads section to sidebar
2. `src/router/index.ts` — Add dialog routes with role guards

---

## Dependencies

### Backend
- No new major dependencies required for InMemory RAG
- Optional for pgvector: `pgvector` PostgreSQL extension + `pgvector` spring integration
- Optional for embeddings: OpenAI or Anthropic embedding API

### Frontend
- No new dependencies (Quasar already provides all needed components)

---

## Estimated Effort
- **Phase 1** (Backend entities + API): 4-6 hours
- **Phase 2** (RAG adapter system): 4-6 hours  
- **Phase 3** (Frontend sidebar + chat): 4-6 hours
- **Phase 4** (Integration + streaming): 3-4 hours
- **Phase 5** (Testing): 3-4 hours
- **Total**: ~18-26 hours (3-4 dev days)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| InMemoryRAG doesn't scale | Adapter pattern allows swapping to Pgvector/Pinecone later |
| SSE streaming complexity | Use Spring's `Flux<ServerSentEvent>` + Quasar EventSource polyfill |
| RAG context blowing token budget | Limit max retrieved chunks, summarize if needed |
| Thread title generation | Use LLM on first message, or first 50 chars |
| Multi-tenant data isolation | All queries scoped by `user_id`; FK + WHERE clause |
| No embedding model for MVP | Keyword-based TF-IDF is sufficient for demo |
