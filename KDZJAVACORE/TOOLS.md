# Tools Scripts Documentation

This document describes all scripts under `./tools` and `./tools/internal`.

## Directory overview

- `tools/`: primary shell entrypoints for test and deploy workflows.
- `tools/internal/`: Python wrappers/variants for selected redeploy operations.

## Script dependency map

- `tools/test_backend_et_frontend_all.sh` orchestrates:
  - `tools/test_backend_spawn_all_necessary_subshells.sh`
  - `tools/test_frontend_spawn_all_necessary_subshells.sh`
- `tools/testall.sh` currently contains the same master orchestration logic as `tools/test_backend_et_frontend_all.sh`.
- `tools/testback.sh` currently contains the same backend test logic as `tools/test_backend_spawn_all_necessary_subshells.sh`.
- `tools/testfront.sh` currently contains the same frontend test logic as `tools/test_frontend_spawn_all_necessary_subshells.sh`.

## `tools/` scripts

### `tools/redeploy_backend_dev.sh`

- Purpose: backend redeploy flow for `dev` profile (no blue-green strategy).
- Behavior:
  - builds `generic_backend` with Maven using `SPRING_PROFILES_ACTIVE=dev`,
  - expects Flyway migrations to run on app startup,
  - prints instructions for manual app start (`java -jar ... --spring.profiles.active=dev`).
- Notes:
  - script currently prints completion guidance but does not actually start a long-running JVM itself.
  - relies on existing Maven and Java installation.

### `tools/redeploy_backend_prod.sh`

- Purpose: backend redeploy flow for `prod` profile with blue-green semantics.
- Behavior:
  - builds `generic_backend` with Maven using `SPRING_PROFILES_ACTIVE=prod`,
  - infers active/idle color from `BLUE_GREEN_ACTIVE_COLOR` (default `blue`),
  - performs a health-check poll on `https://rig1.lan:8443/api/articles/health`,
  - if healthy, simulates traffic switch and exports new active color.
- Notes:
  - blue-green infra steps are documented in comments and partially simulated.
  - health check timeout is 60 seconds.

### `tools/redeploy_frontend_dev.sh`

- Purpose: placeholder for dev frontend redeploy.
- Behavior: prints "not yet implemented."

### `tools/redeploy_frontend_prod.sh`

- Purpose: placeholder for prod frontend redeploy.
- Behavior: prints "not yet implemented."

### `tools/test_backend_et_frontend_all.sh`

- Purpose: top-level end-to-end test orchestrator.
- Behavior:
  - runs backend tests first,
  - runs frontend tests only if backend succeeds,
  - reports individual exit statuses and final combined result.
- Called scripts:
  - `test_backend_spawn_all_necessary_subshells.sh`,
  - `test_frontend_spawn_all_necessary_subshells.sh`.

### `tools/test_backend_spawn_all_necessary_subshells.sh`

- Purpose: backend test preparation and execution pipeline.
- Behavior:
  - checks Docker availability; installs Docker via apt + official repo when missing/unavailable,
  - checks Java and Maven; installs when missing,
  - restarts or creates `postgres-dev` container (`postgres:15-alpine`),
  - waits for PostgreSQL readiness using `pg_isready`,
  - builds/installs `generic_backend`,
  - runs tests for `generic_backend` then `customer_project`,
  - performs additional Maven compile check.
- Execution model:
  - despite the name, major steps are executed sequentially (foreground),
  - exits early on failures in prerequisite stages.
- Important dependencies:
  - `sudo`, `docker`, `apt-get`, `mvn`, network access for package installation.

### `tools/test_frontend_spawn_all_necessary_subshells.sh`

- Purpose: frontend test preparation and execution with backend startup prerequisite.
- Behavior:
  - checks Java and Maven; installs when missing,
  - installs `generic_backend` in local Maven repository,
  - starts `customer_project` backend (`mvn spring-boot:run`) in background,
  - waits up to 5 minutes for backend health endpoint `http://localhost:8080/api/workflow/health`,
  - runs frontend tasks:
    - `npm install`,
    - `npm run test:unit -- --run --silent`,
    - `npx playwright test`,
    - `npx tsc --noEmit`.
- Execution model:
  - dependency install step is started as a background subshell and awaited,
  - test commands are launched in parallel background subshells and awaited,
  - aggregates failures via `FAILED_SUBSHELLS`.
- Important dependencies:
  - `npm`/Node.js, `npx`, Playwright tooling, backend service startup.

### `tools/testall.sh`

- Purpose: alias/duplicate of master combined test orchestration logic.
- Current content: equivalent logic to `test_backend_et_frontend_all.sh`.

### `tools/testback.sh`

- Purpose: alias/duplicate of backend test pipeline.
- Current content: equivalent logic to `test_backend_spawn_all_necessary_subshells.sh`.

### `tools/testfront.sh`

- Purpose: alias/duplicate of frontend test pipeline.
- Current content: equivalent logic to `test_frontend_spawn_all_necessary_subshells.sh`.

## `tools/internal/` scripts

### `tools/internal/redeploy_backend_dev.py`

- Purpose: Python wrapper for dev backend redeploy flow.
- Behavior:
  - runs Maven package (`-DskipTests`) in `generic_backend`,
  - prints deploy guidance for running jar with dev profile.
- Notes:
  - defines `env = {"SPRING_PROFILES_ACTIVE": "dev"}` but does not currently pass it into `subprocess.run`.

### `tools/internal/redeploy_backend_prod.py`

- Purpose: Python wrapper for prod backend redeploy flow with blue-green-style steps.
- Behavior:
  - builds `generic_backend`,
  - infers active/idle color from `BLUE_GREEN_ACTIVE_COLOR`,
  - performs health polling against `https://rig1.lan:8443/api/articles/health`,
  - prints simulated switch/cleanup actions.
- Notes:
  - blue-green operational actions are placeholders/commented.

### `tools/internal/redeploy_frontend_dev.py`

- Purpose: placeholder Python wrapper for dev frontend redeploy.
- Behavior: prints "not yet implemented" and exits success.

### `tools/internal/redeploy_frontend_prod.py`

- Purpose: placeholder Python wrapper for prod frontend redeploy.
- Behavior: prints "not yet implemented" and exits success.

## Typical entrypoints

- Run all tests:
  - `bash tools/test_backend_et_frontend_all.sh`
  - or currently equivalent `bash tools/testall.sh`
- Run backend-only tests:
  - `bash tools/test_backend_spawn_all_necessary_subshells.sh`
  - or currently equivalent `bash tools/testback.sh`
- Run frontend-only tests:
  - `bash tools/test_frontend_spawn_all_necessary_subshells.sh`
  - or currently equivalent `bash tools/testfront.sh`
- Run backend deploy helpers:
  - shell: `bash tools/redeploy_backend_dev.sh`, `bash tools/redeploy_backend_prod.sh`
  - python: `python3 tools/internal/redeploy_backend_dev.py`, `python3 tools/internal/redeploy_backend_prod.py`
