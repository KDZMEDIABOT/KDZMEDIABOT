# OpenSERP MCP Sidecar
# Uses Python duckduckgo-search library (no external Go binary needed)
FROM python:3.11-slim

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Python MCP library and DuckDuckGo search
RUN pip install --no-cache-dir mcp==1.1.0 ddgs

# Create MCP wrapper
COPY openmcp_server.py /opt/openmcp_server.py

ENV PYTHONUNBUFFERED="1"

WORKDIR /opt

CMD ["python", "-u", "openmcp_server.py"]
