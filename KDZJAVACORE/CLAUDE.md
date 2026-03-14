# AI Content Generator System

## Production Environment (rig1.lan)

Current production deployment is on `rig1.lan`:
- **Frontend**: http://rig1.lan:8088 (nginx serving SPA)
- **Auth Service**: http://rig1.lan:3002 (Node.js/Passport.js sidecar)
- **Backend**: http://rig1.lan:8443 (Spring Boot Java)
- **PostgreSQL**: rig1.lan:5432 (port 15432 externally exposed)
- **Redis**: rig1.lan:6379 (used for session storage)

### Critical Production Configuration (`.env.prod`)

```bash
# URLs must use rig1.lan, NOT localhost
AUTH_SERVICE_FRONTEND_URL=http://rig1.lan:8088
AUTH_SERVICE_PUBLIC_URL=http://rig1.lan:3002
FRONTEND_URL=http://rig1.lan:8088
SERVER_PROFILE=prod
AUTH_SERVICE_SESSION_SECRET=<strong-random-secret>
AUTH_SERVICE_HTTPD_PORT=3002
AUTH_SESSION_STORE=redis
AUTH_SERVICE_REDIS_URL=redis://redis:6379
AUTH_SERVICE_REDIS_PREFIX=aisystem:auth:sess:
```

### Production Container Status

```bash
# Check running containers
sudo ssh -o PasswordAuthentication=false -i /home/rig1_ubuntu16_root root@rig1.lan -c "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"

# View auth service logs
sudo ssh -o PasswordAuthentication=false -i /home/rig1_ubuntu16_root root@rig1.lan -c "docker logs -f aisystem-auth-sidecar-prod"

# Check Redis sessions
sudo ssh -o PasswordAuthentication=false -i /home/rig1_ubuntu16_root root@rig1.lan -c "docker exec aisystem-redis-prod redis-cli KEYS 'aisystem:auth:sess:*'"
```

## Beware
  * NEVER USE FALLBACK RDBMS, ALWAYS USE POSTGRESQL!!!
* NEVER RUN MULTIPLE COPIES OF BACKENDS TEST SCRIPT IN PARALLEL!!!
* RUN ALL MVN TASKS UNDER CURRENT USER, NOT UNDER ROOT!
* ALWAYS LIVESTREAM TESTS RUNTIME STDOUT AND STDERR OUTPUT.
* WHEN MONITORING LONG-RUNNING COMMANDS, DO NOT ONLY CALL `sleep`; USE `sleep` AND `tail -n 0 -f` IN PARALLEL TO AVOID DUPLICATE LINES.
* RESTART DEV & PROD ONLY VIA `tools/redeploy*` SCRIPTS.
* ALWAYS RUN `auth_service` AS A SIDECAR OF THE JAVA BACKEND; TEST IT IN BACKEND TEST SCRIPTS AND REDEPLOY IT TOGETHER WITH THE JAVA BACKEND IN BOTH `dev` AND `prod`.

## Project Overview
Content generator with post-editing workflow. AI-generated content goes through human review cycles, with potential for additional AI iterations after editing.

## Source Documentation Priority
1. **notion1.pdf** (most important) - LINA SEO articles AI agent specification
2. **log1.md** (less important) - Project logs and discussion
3. **stateflow1.jpg** (reference) - Workflow state diagram
4. **TOOLS.md** (operations reference) - Documentation for all scripts in `./tools`

---

## Task List

### Phase 1: Core AI Content Pipeline
1. **Create Topic Definition Module**
   - Monthly topic batch input (30 topics)
   - Topic categorization (psychology tests, art therapy, recommendations)
   - Topic approval workflow

2. **Create Deep Research Engine**
   - Claude AI/ChatGPT integration
   - Research data gathering from authoritative sources
   - Research output storage and retrieval
   - Citation tracking for E-E-A-T compliance

3. **Create Image Generation/Selection Module**
   - Pexels API integration for stock photo selection
   - AI image generation as fallback option
   - Image relevance scoring by topic
   - Image metadata management (alt text, captions)

4. **Create Article Assembly Engine**
   - Template-based assembly system
   - Support for: title, H2/H3 headings, lists, quotes, FAQ sections
   - Meta description generation (150 chars)
   - SEO slug generation (lowercase, keywords)
   - Internal/external link suggestions

5. **Create Human-in-the-Loop Review System**
   - Article approval/rejection workflow
   - Editor feedback capture
   - Revisions tracking
   - AI iteration trigger after edits

6. **Create Tilda Publishing Integration**
   - Tilda API integration
   - Scheduled publishing (1 article/day)
   - Block-based article formatting for Tilda

---

### Phase 2: Content Types Implementation
7. **Create Psychological Test Content Generator**
   - Test name and description generation
   - Test questions generation
   - Test results interpretation
   - Example: Dependent Personality Disorder Test

8. **Create Art Therapy Activities Generator**
   - Activity name generation
   - Step-by-step instructions
   - Materials needed list
   - Benefits explanation

9. **Create Recommendation List Generator**
   - List structure ("Best X for Y in 2025")
   - Product/service comparison
   - Pros/cons formatting
   - Call-to-action integration

---

### Phase 3: Multi-Platform Distribution
10. **Create Twitter/X Thread Transformer**
    - Article-to-thread conversion
    - Thread part counting and numbering
    - Engagement hooks

11. **Create Medium/LinkedIn Cross-Posting**
    - Platform-specific formatting
    - SEO cross-linking between platforms
    - Canonical URL management

---

### Phase 4: Infrastructure (Technical Implementation)
12. **Create Generic Java Backend**
    - Package: `com.localmesalevel.aisystemtakeone`
    - Stack: Java + Hibernate + Spring + Flyway
    - Host: rig1.lan with blue-green load balancing
    - Feature: SSO single sign-on
    - PostgreSQL database
    - Spring profiles: dev (localhost), prod (rig1.lan)

13. **Create Task-Oriented Java Project**
    - Package: `com.customer1org.aisystem`
    - Depends on generic library
    - Content-specific business logic

14. **Create Quasar + TypeScript Frontend**
    - SSO Sign-In/Sign-Up
    - Vercel deployment
    - Article preview and approval UI
    - Topic management interface

15. **Create Migration Scripts**
    - Flyway migration scripts for PostgreSQL
    - Database schema versioning

---

### Phase 5: Deployment & Testing
16. **Create Environment Configuration**
    - `.env.dev` and `.env.prod` templates
    - Environment-specific database configs
    - Blue-green deployment scripts (prod only)
    - Dev scripts without blue-green

17. **Create Deployment Tools**
    - Backend dev deployment script
    - Backend prod deployment script (blue-green)
    - Frontend dev deployment script
    - Frontend prod deployment script
    - Python wrappers for all scripts

18. **Create Test Suite**
    - Unit tests (backend + frontend)
    - E2E tests for content pipeline
    - GUI tests for approval workflow
    - All tests use `dev` profile with PostgreSQL

---

## Workflow State Definition (from stateflow1.jpg)

| State | Description | Output |
|-------|-------------|--------|
| **TOPIC_DEFINITION** | User defines 30 monthly topics | Topic list with categories |
| **DEEP_RESEARCH** | AI researches each topic | Research data, citations |
| **IMAGE_SELECTION** | Select/generate relevant images | Image URLs, metadata |
| **ARTICLE_ASSEMBLY** | Generate article from template | Structured article draft |
| **HUMAN_REVIEW** | Editor approves/rejects + feedback | Approved article or revision notes |
| **AI_REVISION** | AI updates article based on feedback | Revised article draft |
| **PUBLICATION** | Publish to Tilda on schedule | Published article URL |
| **CROSS_POST** | Create Twitter/Medium/LinkedIn variants | Cross-posted content |

---

## Technical Requirements Summary

### Deployment Rules
- **Dev**: Localhost, PostgreSQL, NO blue-green
- **Prod**: rig1.lan, PostgreSQL, WITH blue-green
- **Tests**: Use `@ActiveProfiles("dev")` with PostgreSQL database
- **Auth Sidecar**: `auth_service` must run as a sidecar of the Java backend and must be redeployed together with the Java backend in both `dev` and `prod`

### Article Requirements
- Word count: 1500-3000 words
- Structure: Title, H2/H3, lists, quotes, FAQ
- SEO: Title (50-60 chars), Meta desc (150-160 chars)
- Tone: Warm, conversational, empathetic
- E-E-A-T: Experience, Expertise, Authority, Trust signals

### Content Types
1. Psychological tests
2. Art therapy activities
3. Recommendation lists

### Multi-Platform
- Primary: Tilda blog (linatherapy.app/blog)
- Secondary: Twitter/X threads, Medium, LinkedIn

---

## Risks & Considerations
- Tilda updates may break publishing integration
- Consider migrating blog to separate service if Tilda becomes unstable
- Content must pass plagiarism and accuracy checks

---

## Completed Tasks Status
- [x] Generic backend structure (com.localmesalevel.aisystemtakeone)
- [x] Customer project structure (com.customer1org.aisystem)
- [x] Quasar frontend scaffold with SSO pages
- [x] Environment configuration (.env templates)
- [x] Deployment scripts (dev/prod, shell + Python)
- [x] Spring profiles (dev/prod, test-scoped override)
- [x] Flyway database migration scripts
- [x] Deep research engine with E-E-A-T validation
- [x] Image generation/selection (Pexels + AI fallback)
- [x] Article assembly engine with SEO metadata
- [x] Human approval workflow with revision tracking
- [x] Tilda publishing integration with block formatting
- [x] Content type generators (Psychology, Art Therapy, Recommendations)
- [x] Workflow orchestrator (ContentWorkflowOrchestrator)
- [x] REST controllers (Topic, Article, Workflow)
- [x] Frontend Vue components (TopicList, ArticlePreview)
- [x] Frontend pages (TopicManagementPage, ReviewPage)
- [x] Router configuration with new routes
- [x] Unit tests for all services (Topic, Research, Image, Assembly, Review, Publishing, Workflow)
- [x] Frontend E2E tests with Playwright (Auth, Topic, Workflow)
- [x] Frontend unit tests (Component/GUI tests)

## Testing Setup

1. create ./tools/test_backend_et_frontend_all.sh referring to the following scripts:
1.1.1. create ./tools/test_backend_spawn_all_necessary_subshells.sh which spawns all necessary subshells;
1.1.2. at the start of ./tools/test_backend_spawn_all_necessary_subshells.sh , insert the code to restart dev profile's postgresql docker container in the background shell at the localhost and wait for clean startup of postgresql; if docker is missing, install it OS-wide with sudo; do perform all docker commands with sudo prefix;
1.1.3. in backend test scripts, ensure `auth_service` is started as a Java backend sidecar and covered by backend test execution;
1.2.1. create ./tools/test_frontend_spawn_all_necessary_subshells.sh which spawns all necessary subshells;
1.2.2. at the start of ./tools/test_frontend_spawn_all_necessary_subshells.sh, make sure that the backend for customer application is running and (with 5 minute timeout) wait for this customer application backend to have started;
2. run ./tools/test_backend_et_frontend_all.sh and fix all tests that fail without pause.

