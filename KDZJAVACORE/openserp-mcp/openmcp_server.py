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


class WebFetchClient:
    """Client for fetching webpage content."""

    def __init__(self):
        self.session = None
        try:
            import requests
            self.session = requests.Session()
            self.session.headers.update({
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
            })
            self.available = True
        except ImportError:
            self.available = False
            log("WARNING: requests library not available")

    def fetch(self, url: str, timeout: int = 30) -> Dict:
        """Fetch webpage content."""
        if not self.available:
            return {"error": "requests library not installed"}

        if not url or not url.strip():
            return {"error": "Empty URL"}

        url = url.strip()
        if not url.startswith(('http://', 'https://')):
            url = 'https://' + url

        try:
            log(f"Fetching URL: {url[:60]}...")
            response = self.session.get(url, timeout=timeout, allow_redirects=True)
            response.raise_for_status()

            content_type = response.headers.get('content-type', '').lower()

            # Handle HTML content
            if 'text/html' in content_type:
                return self._extract_text_from_html(response.text, url)

            # Handle plain text
            if 'text/' in content_type:
                return {
                    "url": url,
                    "content_type": content_type,
                    "text": response.text[:50000],  # Limit to 50K chars
                    "status_code": response.status_code
                }

            # For other types, return metadata
            return {
                "url": url,
                "content_type": content_type,
                "text": f"Binary content ({len(response.content)} bytes). Content type: {content_type}",
                "status_code": response.status_code
            }

        except Exception as e:
            log(f"Fetch error: {e}")
            return {"error": str(e), "url": url}

    def _extract_text_from_html(self, html: str, url: str) -> Dict:
        """Extract readable text from HTML using BeautifulSoup."""
        try:
            from bs4 import BeautifulSoup

            soup = BeautifulSoup(html, 'html.parser')

            # Remove script and style elements
            for script in soup(['script', 'style', 'nav', 'footer', 'header']):
                script.decompose()

            # Get title
            title = ""
            title_tag = soup.find('title')
            if title_tag:
                title = title_tag.get_text(strip=True)

            # Get meta description
            description = ""
            meta_desc = soup.find('meta', attrs={'name': 'description'})
            if meta_desc:
                description = meta_desc.get('content', '')

            # Get main content
            # Try to find main content area
            main_content = None
            for selector in ['main', 'article', '[role="main"]', '.content', '#content', '.article']:
                main_content = soup.select_one(selector)
                if main_content:
                    break

            if not main_content:
                main_content = soup.find('body') or soup

            # Extract text
            text = main_content.get_text(separator='\n', strip=True)

            # Clean up excessive whitespace
            lines = [line.strip() for line in text.split('\n') if line.strip()]
            text = '\n'.join(lines)

            # Truncate if too long
            if len(text) > 50000:
                text = text[:50000] + "..."

            return {
                "url": url,
                "title": title,
                "description": description,
                "text": text,
                "content_type": "text/html",
                "extracted_lines": len(lines)
            }

        except ImportError:
            # Fallback: simple text extraction
            text = html.replace('<script', '\x00').replace('</script>', '\x00')
            text = text.replace('<style', '\x00').replace('</style>', '\x00')
            # Simple strip tags
            import re
            text = re.sub(r'<[^>]+>', ' ', text)
            text = re.sub(r'\s+', ' ', text).strip()

            if len(text) > 50000:
                text = text[:50000] + "..."

            return {
                "url": url,
                "text": text,
                "content_type": "text/html",
                "note": "BeautifulSoup not available, using basic extraction"
            }


class MCPServer:
    """MCP server using stdio transport."""

    def __init__(self):
        self.server_name = "openserp-websearch"
        self.version = "1.1.0"
        self.client = WebSearchClient()
        self.fetch_client = WebFetchClient()

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
                        },
                        {
                            "name": "webpage_fetch",
                            "description": "Fetch and extract text content from a webpage URL",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "url": {
                                        "type": "string",
                                        "description": "The URL to fetch"
                                    },
                                    "timeout": {
                                        "type": "integer",
                                        "description": "Request timeout in seconds",
                                        "default": 30
                                    }
                                },
                                "required": ["url"]
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

        if tool_name == "webpage_fetch":
            result = self.fetch_client.fetch(
                arguments.get("url", ""),
                arguments.get("timeout", 30)
            )
            is_error = "error" in result

            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(result, indent=2, ensure_ascii=False)
                        }
                    ],
                    "isError": is_error
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
