#!/usr/bin/env python3
"""
MCP server for web search.
Uses DuckDuckGo search directly (no external OpenSERP binary).
"""

import asyncio
import json
import os
import sys
from typing import Any, Dict, List, Optional

try:
    from ddgs import DDGS
    DDGS_AVAILABLE = True
except ImportError:
    try:
        # Fallback to old package name
        from duckduckgo_search import DDGS
        DDGS_AVAILABLE = True
    except ImportError:
        DDGS_AVAILABLE = False


def log(msg: str) -> None:
    """Log to stderr (not stdout which is used for MCP)."""
    print(f"[OpenSERP-MCP] {msg}", file=sys.stderr, flush=True)


class WebSearchClient:
    """Client for web search using DuckDuckGo."""

    def search(self, query: str, provider: str = "ddg", num_results: int = 5) -> List[Dict]:
        """Perform web search."""
        if not query or not query.strip():
            return [{"error": "Empty query"}]

        if not DDGS_AVAILABLE:
            log("WARNING: duckduckgo_search library not available")
            # Return simulated results for testing
            return [
                {
                    "rank": 1,
                    "title": f"Search for: {query}",
                    "url": "https://example.com",
                    "snippet": "DuckDuckGo search library not installed. This is a simulated result."
                }
            ]

        results = []
        try:
            log(f"Searching DuckDuckGo for: {query[:50]}...")
            with DDGS() as ddgs:
                search_results = ddgs.text(query, max_results=min(num_results, 10))

                for i, result in enumerate(search_results, 1):
                    results.append({
                        "rank": i,
                        "title": result.get("title", "No title"),
                        "url": result.get("href", ""),
                        "snippet": result.get("body", result.get("snippet", ""))
                    })
        except Exception as e:
            log(f"Search error: {e}")
            results.append({"error": str(e), "query": query})

        return results


class MCPServer:
    """MCP server using stdio transport."""

    def __init__(self):
        self.server_name = "openserp-websearch"
        self.version = "1.0.0"
        self.client = WebSearchClient()

    async def run(self) -> None:
        """Main server loop."""
        log(f"MCP server '{self.server_name}' starting (version {self.version})")
        log(f"DDGS available: {DDGS_AVAILABLE}")

        while True:
            try:
                line = await asyncio.get_event_loop().run_in_executor(
                    None, sys.stdin.readline
                )
                if not line:
                    log("EOF received, shutting down")
                    break

                line = line.strip()
                if not line:
                    continue

                try:
                    request = json.loads(line)
                    response = await self._handle_request(request)
                    if response:
                        output = json.dumps(response)
                        log(f"Sending: {output[:200]}...")
                        print(output, flush=True)
                except json.JSONDecodeError as e:
                    log(f"Invalid JSON: {e}")

            except Exception as e:
                log(f"Error in main loop: {e}")

    async def _handle_request(self, request: Dict) -> Optional[Dict]:
        """Handle MCP request."""
        method = request.get("method", "")
        request_id = request.get("id")

        log(f"Handling method: {method}")

        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "serverInfo": {
                        "name": self.server_name,
                        "version": self.version
                    },
                    "capabilities": {}
                }
            }

        if method == "notifications/initialized":
            return None

        if method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "tools": [
                        {
                            "name": "websearch",
                            "description": "Search the web using DuckDuckGo",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "query": {
                                        "type": "string",
                                        "description": "Search query"
                                    },
                                    "provider": {
                                        "type": "string",
                                        "description": "Search provider (ddg)",
                                        "default": "ddg"
                                    },
                                    "num_results": {
                                        "type": "integer",
                                        "description": "Number of results (max 10)",
                                        "default": 5
                                    }
                                },
                                "required": ["query"]
                            }
                        }
                    ]
                }
            }

        if method == "tools/call":
            params = request.get("params", {})
            tool_name = params.get("name")
            arguments = params.get("arguments", {})

            log(f"Tool call: {tool_name} with args: {arguments}")

            if tool_name == "websearch":
                results = self.client.search(
                    arguments.get("query", ""),
                    arguments.get("provider", "ddg"),
                    arguments.get("num_results", 5)
                )

                return {
                    "jsonrpc": "2.0",
                    "id": request_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": json.dumps({"results": results}, indent=2, ensure_ascii=False)
                            }
                        ],
                        "isError": False
                    }
                }

            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {
                    "code": -32602,
                    "message": f"Unknown tool: {tool_name}"
                }
            }

        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "error": {
                "code": -32601,
                "message": f"Unknown method: {method}"
            }
        }


if __name__ == "__main__":
    asyncio.run(MCPServer().run())
