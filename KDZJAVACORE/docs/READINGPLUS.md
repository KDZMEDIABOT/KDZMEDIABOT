# Reading Plus MCP Sidecar

## Overview

The backend stack includes a dedicated sidecar for Reading Plus AI's MCP data exploration server.

- **Service name (compose):** `readingplus_mcp_sidecar`
- **Dev container:** `aisystem-readingplus-mcp-sidecar-dev`
- **Prod container:** `aisystem-readingplus-mcp-sidecar-prod`
- **Runtime model:** stdio MCP server (`mcp-server-ds`)

This sidecar is managed together with backend redeploy scripts:

- `tools/redeploy_backend_dev.sh`
- `tools/redeploy_backend_prod.sh`

## Why a sidecar

The MCP server is isolated from Java backend runtime concerns and can be restarted independently while staying in the same compose stack.

## Image and package versions

The sidecar currently runs from `python:3.11-slim` and installs:

- `mcp-server-ds==0.1.5`
- `mcp==1.1.0`

The `mcp` version is pinned for compatibility because newer releases remove symbols expected by `mcp-server-ds`.

## Environment variables

Configured in `.env.dev`, `.env.prod`, and corresponding templates:

- `READINGPLUS_MCP_SERVER_VERSION` (default `0.1.5`)
- `READINGPLUS_MCP_PROTOCOL_VERSION` (default `1.1.0`)

## Compose behavior

The service command installs dependencies and starts the MCP server:

```sh
pip install --no-cache-dir mcp==${READINGPLUS_MCP_PROTOCOL_VERSION} mcp-server-ds==${READINGPLUS_MCP_SERVER_VERSION} && exec mcp-server-ds
```

Because `mcp-server-ds` is a stdio server, the container sets:

- `stdin_open: true`
- `tty: true`

This prevents immediate clean exit (`code 0`) loops caused by closed stdin.

## Redeploy integration

Backend redeploy scripts now include this sidecar in `up` and `restart` steps.

If startup fails, logs include the sidecar:

```bash
docker compose -f docker-compose.dev.yml --env-file .env.dev logs --tail=200 readingplus_mcp_sidecar
```

## Troubleshooting

### `ImportError: cannot import name 'McpError' from mcp.server`

Cause: incompatible `mcp` version (too new).

Fix: keep `READINGPLUS_MCP_PROTOCOL_VERSION=1.1.0`.

### `exited with code 0 (restarting)`

Cause: stdio process exits when stdin is not kept open.

Fix: keep `stdin_open: true` and `tty: true` in compose.

### `No matching distribution found for mcp-server-ds==0.1.6`

Cause: requested version does not exist on PyPI for current environment.

Fix: use `READINGPLUS_MCP_SERVER_VERSION=0.1.5`.
