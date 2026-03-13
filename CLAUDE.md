# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is a combined repository containing two distinct projects:

1. **KDZMEDIABOT** (root level): Python-based IRC and Telegram bot for cryptocurrency price tracking
2. **KDZJAVACORE** (subdirectory): Java/Spring Boot + Vue.js AI Content Generation System (see `KDZJAVACORE/CLAUDE.md` for detailed guidance)

## KDZMEDIABOT (Python Bot)

A multi-process bot supporting both IRC and Telegram protocols. Features cryptocurrency price queries (CoinMarketCap API), market reports, and Krakozhian text translation.

### Architecture

| File | Purpose |
|------|---------|
| `abstractbich.py` | Base `BichBot` class with shared IRC/Telegram logic including crypto price fetching, voting system, and command handling |
| `ircbich.py` | IRC protocol implementation using raw sockets, supports SOCKS5 proxy, NickServ authentication |
| `tgbich.py` | Telegram bot implementation using aiogram 2.x |
| `helpers.py` | Utility functions (`shell()`, JSON formatting, Gostcoin price fetching) |
| `settings.py` | Configuration loader from `local.json` |
| `launch_all.py` | Entry point that spawns processes for each configured connection |

**Key Pattern**: The bot uses multiprocessing (`multiprocessing.Process`) to launch separate processes for each IRC/Telegram connection defined in `local.json`. Base functionality is in `BichBot` class extended by `IrcBich` and `TgBich`.

### Configuration

Requires `local.json` in project root (see `local.json.example` for structure):
```json
{
  "connections": {
    "tg": { "LibreLifeBotTelegram": { "BOT_TOKEN": "...", "onlycmc": true } },
    "irc": { "servername": { "irc_server_hostname": "...", "port": 6667, ... } }
  },
  "coinmarketcap_apikey": "...",
  "rapidapi_appkey": "...",
  "master_secret": "..."
}
```

### Commands

```bash
# Install dependencies
pip install -r requirements.txt

# Run the bot (launches all configured connections)
python3 launch_all.py

# Or via main module
python3 main.py
```

### Key Bot Commands

| Command | Description |
|---------|-------------|
| `!курс` / `!markets` | Cryptocurrency market report |
| `!price <symbol>` / `!price <symbol>/<symbol>` | Query specific crypto price (e.g., `!price BTC/USD`) |
| `!k <text>` | Translate Krakozhian text |
| `!hextoip <hex>` | Convert hexadecimal to IP address |
| `/calc <formula>` | Calculator with `price()` function support |
| `!опрос <seconds> <question>` | Start a poll/voting (IRC only) |

### Python Dependencies

Key packages from `requirements.txt`:
- `aiogram==2.17.1` - Telegram bot framework
- `aiohttp==3.8.1` - Async HTTP client
- `requests==2.23.0` - HTTP requests
- `Pillow==8.3.0`, `pandas==1.1.5`, `numpy==1.19.5` - Data processing

## KDZJAVACORE (AI Content Generation System)

A multi-module Java/Spring Boot + TypeScript/Vue.js application for AI-powered content generation with human-in-the-loop review.

**Important**: See `KDZJAVACORE/CLAUDE.md` for detailed guidance on the Java project.

### Quick Reference for KDZJAVACORE

| Component | Technology | Location |
|-----------|------------|----------|
| Generic Backend | Java 17, Spring Boot 2.7.12, Hibernate, Flyway | `KDZJAVACORE/generic_backend/` |
| Customer Project | Java 17, Spring Boot, depends on generic | `KDZJAVACORE/customer_project/` |
| Frontend | Vue 3, Quasar, TypeScript, Vite | `KDZJAVACORE/frontend/` |
| Auth Service | Node.js, Passport.js (sidecar pattern) | `KDZJAVACORE/auth_service/` |

### Key Commands (from KDZJAVACORE directory)

```bash
# Run all tests (backend + frontend)
cd KDZJAVACORE
bash tools/test_backend_et_frontend_all.sh

# Dev environment (Docker Compose with PostgreSQL, Redis, auth_sidecar)
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

# Backend only (dev)
bash tools/redeploy_backend_dev.sh

# Backend (prod with blue-green deployment awareness)
bash tools/redeploy_backend_prod.sh

# Single backend test
cd generic_backend && mvn test
cd customer_project && mvn test -Dspring.profiles.active=dev

# Frontend tests
cd frontend
npm install
npm run test:unit -- --run --silent
npx playwright test
npx tsc --noEmit
```

### Critical Rules for KDZJAVACORE

- **Always use PostgreSQL** - never use fallback H2 in production
- **Never run multiple copies of test scripts in parallel**
- **Run all Maven tasks under current user, not root**
- **Restart dev/prod only via `tools/redeploy*` scripts**
- **Always run `auth_service` as a sidecar** of the Java backend; redeploy together
- **Blue-green deployment** only applies to prod (rig1.lan), not dev
