"""
MCP Tools command handler for KDZBot.
Allows IRC/Telegram bot users to directly use MCP tools like OpenSERP websearch.
"""

import asyncio
import json
import logging
import os
import time
from typing import Optional, Dict, Any, List, Callable
from dataclasses import dataclass

from mcp_client import McpClientFactory, McpServerConfig, McpToolRegistry

logger = logging.getLogger(__name__)


@dataclass
class ToolCallResult:
    """Result of a tool call."""
    success: bool
    result: Any
    error: Optional[str] = None


class McpToolsCommandHandler:
    """
    Handles MCP tool commands for KDZBot.

    Usage:
        !websearch <query>   - Search the web using OpenSERP
        !mcp tools           - List available MCP tools
        !mcp call <tool> <args_json> - Call a specific tool
    """

    DEFAULT_TIMEOUT = 60

    def __init__(self, config: dict):
        """
        Initialize MCP tools handler.

        config: dict from local.json with 'mcp' section:
        {
            "mcp": {
                "enabled": true,
                "servers": [
                    {
                        "name": "openserp-websearch",
                        "command": ["docker", "exec", "-i", "aisystem-openserp-mcp-sidecar-dev", "python", "-m", "openmcp_server"],
                        "env": {"OPENSERP_PROVIDER": "ddg"},
                        "timeout": 30
                    }
                ]
            }
        }
        """
        self.config = config or {}
        self.mcp_config = self.config.get("mcp", {})
        self.enabled = self.mcp_config.get("enabled", True)
        self.timeout = self.mcp_config.get("timeout_seconds", self.DEFAULT_TIMEOUT)

        self.registry: Optional[McpToolRegistry] = None
        self._initialized = False
        self._lock = asyncio.Lock()

    async def initialize(self) -> bool:
        """Initialize MCP connections to all configured servers."""
        if not self.enabled:
            logger.info("MCP tools handler is disabled in config")
            return False

        if self._initialized:
            return True

        async with self._lock:
            if self._initialized:
                return True

            try:
                self.registry = McpToolRegistry()

                servers = self.mcp_config.get("servers", [])
                if not servers:
                    # Try to use default OpenSERP sidecar if no explicit config
                    servers = self._get_default_servers()

                for server_config in servers:
                    try:
                        config = self._parse_server_config(server_config)
                        await self.registry.add_server(config)
                        logger.info(f"Connected to MCP server: {config.server_name}")
                    except Exception as e:
                        logger.error(f"Failed to connect to MCP server {server_config.get('name')}: {e}")

                self._initialized = True
                tool_count = len(self.registry.tools)
                logger.info(f"MCP tools handler initialized with {tool_count} tools")
                return tool_count > 0

            except Exception as e:
                logger.error(f"Failed to initialize MCP tools handler: {e}")
                return False

    def _get_default_servers(self) -> List[Dict]:
        """Get default MCP server configurations."""
        servers = []

        # Check for OpenSERP via Docker
        openserp_container = os.environ.get(
            "OPENSERP_MCP_CONTAINER_NAME",
            "aisystem-openserp-mcp-sidecar-dev"
        )

        servers.append({
            "name": "openserp-websearch",
            "command": [
                "docker", "exec", "-i",
                openserp_container,
                "python", "-u", "/opt/openmcp_server.py"
            ],
            "env": {
                "OPENSERP_PROVIDER": os.environ.get("OPENSERP_PROVIDER", "ddg"),
                "PYTHONUNBUFFERED": "1"
            },
            "timeout": 30
        })

        return servers

    def _parse_server_config(self, config: Dict) -> McpServerConfig:
        """Parse server config dict into McpServerConfig."""
        return McpServerConfig(
            server_name=config["name"],
            command=config.get("command"),
            url=config.get("url"),
            cwd=config.get("cwd"),
            env=config.get("env", {}),
            bearer_token=config.get("bearer_token"),
            headers=config.get("headers", {}),
            timeout=config.get("timeout", self.timeout),
            connect_timeout=config.get("connect_timeout", 15)
        )

    async def handle_command(self, user_id: str, platform: str, channel: str,
                           command: str, args: str) -> str:
        """
        Handle an MCP tools command.

        Returns the response string or error message.
        """
        if not self._initialized:
            success = await self.initialize()
            if not success:
                return "Error: MCP tools not available. Check configuration."

        command = command.lower().strip()

        if command == "websearch" or command == "search":
            return await self._handle_websearch(user_id, platform, channel, args)

        elif command == "mcp":
            return await self._handle_mcp_command(user_id, platform, channel, args)

        return f"Unknown MCP command: {command}"

    async def _handle_websearch(self, user_id: str, platform: str, channel: str,
                               query: str) -> str:
        """Handle web search using OpenSERP."""
        if not query or not query.strip():
            return "Usage: !websearch <query>"

        query = query.strip()

        # Find OpenSERP websearch tool
        tool_name = self._find_tool("openserp", "search")
        if not tool_name:
            tool_name = self._find_tool("openserp", "websearch")
        if not tool_name:
            # Try any search tool
            for qualified_name in self.registry.tools.keys():
                if "search" in qualified_name.lower():
                    tool_name = qualified_name
                    break

        if not tool_name:
            return "Error: No websearch tool available. Check MCP configuration."

        try:
            result = await asyncio.wait_for(
                self.registry.call_tool(tool_name, {"query": query}),
                timeout=self.timeout
            )
            return self._format_search_results(result, query)
        except asyncio.TimeoutError:
            return "Error: Web search timed out. Try again later."
        except Exception as e:
            logger.error(f"Web search error: {e}")
            return f"Error: Web search failed: {e}"

    async def _handle_mcp_command(self, user_id: str, platform: str, channel: str,
                                 args: str) -> str:
        """Handle general MCP commands like 'tools' and 'call'."""
        parts = args.split(None, 1)
        if not parts:
            return self._get_mcp_help()

        subcommand = parts[0].lower()
        subargs = parts[1] if len(parts) > 1 else ""

        if subcommand == "tools":
            return self._list_tools()

        elif subcommand == "call":
            return await self._handle_call_command(subargs)

        elif subcommand == "help":
            return self._get_mcp_help()

        return f"Unknown MCP subcommand: {subcommand}"

    async def _handle_call_command(self, args: str) -> str:
        """Handle direct tool call: !mcp call <tool_name> <args_json>."""
        parts = args.split(None, 1)
        if len(parts) < 1:
            return "Usage: !mcp call <tool_name> [args_json]"

        tool_name = parts[0]
        args_json = parts[1] if len(parts) > 1 else "{}"

        try:
            tool_args = json.loads(args_json)
        except json.JSONDecodeError as e:
            return f"Error: Invalid JSON arguments: {e}"

        # Try to resolve tool name
        qualified_name = tool_name
        if tool_name not in self.registry.tools:
            # Try to find by partial match
            matches = [name for name in self.registry.tools.keys()
                      if tool_name.lower() in name.lower()]
            if len(matches) == 1:
                qualified_name = matches[0]
            elif len(matches) > 1:
                return f"Ambiguous tool name '{tool_name}'. Matches: {', '.join(matches)}"
            else:
                return f"Unknown tool: {tool_name}. Use '!mcp tools' to list available tools."

        try:
            result = await asyncio.wait_for(
                self.registry.call_tool(qualified_name, tool_args),
                timeout=self.timeout
            )
            return self._format_tool_result(result)
        except asyncio.TimeoutError:
            return "Error: Tool call timed out."
        except Exception as e:
            logger.error(f"Tool call error: {e}")
            return f"Error: Tool call failed: {e}"

    def _find_tool(self, server_hint: str, tool_hint: str) -> Optional[str]:
        """Find a tool by server and tool name hints."""
        for qualified_name in self.registry.tools.keys():
            if server_hint.lower() in qualified_name.lower():
                if tool_hint.lower() in qualified_name.lower():
                    return qualified_name
        return None

    def _format_search_results(self, result: Dict, query: str) -> str:
        """Format search results for IRC/Telegram output."""
        lines = [f"Web search results for: '{query}'"]

        # Try to extract results from various response formats
        results_data = result

        if "result" in result:
            results_data = result["result"]
        elif "content" in result:
            results_data = result["content"]

        if isinstance(results_data, list):
            for i, item in enumerate(results_data[:5], 1):  # Max 5 results
                if isinstance(item, dict):
                    title = item.get("title", "No title")
                    url = item.get("url", "")
                    snippet = item.get("snippet", item.get("description", ""))
                    lines.append(f"{i}. {title}")
                    if url:
                        lines.append(f"   {url}")
                    if snippet:
                        snippet = snippet[:200] + "..." if len(snippet) > 200 else snippet
                        lines.append(f"   {snippet}")
                else:
                    lines.append(f"{i}. {str(item)[:100]}")

        elif isinstance(results_data, dict):
            # Single result or structured data
            for key, value in results_data.items():
                if isinstance(value, str):
                    lines.append(f"• {key}: {value[:200]}")
                elif isinstance(value, list):
                    lines.append(f"• {key}:")
                    for item in value[:3]:
                        lines.append(f"  - {str(item)[:100]}")

        else:
            lines.append(str(results_data)[:500])

        return "\n".join(lines)

    def _format_tool_result(self, result: Any) -> str:
        """Format generic tool result."""
        if isinstance(result, dict):
            # Check for errors
            if "error" in result:
                return f"Error: {result['error']}"

            lines = ["Tool result:"]
            for key, value in result.items():
                if key == "content" and isinstance(value, list):
                    for item in value:
                        if isinstance(item, dict) and "text" in item:
                            lines.append(item["text"])
                        else:
                            lines.append(str(item))
                else:
                    lines.append(f"{key}: {json.dumps(value, indent=2)[:300]}")
            return "\n".join(lines[:50])  # Limit output

        return str(result)[:1000]

    def _list_tools(self) -> str:
        """List all available MCP tools."""
        if not self.registry or not self.registry.tools:
            return "No MCP tools available."

        lines = ["Available MCP tools:"]
        for qualified_name, tool in self.registry.tools.items():
            desc = tool.description or "No description"
            lines.append(f"• {qualified_name}: {desc}")

        return "\n".join(lines)

    def _get_mcp_help(self) -> str:
        """Get help message for MCP commands."""
        return """MCP Tools Commands:
!websearch <query> - Search the web
!mcp tools         - List available MCP tools
!mcp call <tool> <json_args> - Call a specific tool
!mcp help          - Show this help

Examples:
!websearch python async tutorial
!mcp call openserp-websearch/search {"query": "latest crypto prices"}"""

    async def close(self) -> None:
        """Close all MCP connections."""
        if self.registry:
            await self.registry.close_all()
            self.registry = None
        self._initialized = False


class McpToolsLoopEngine:
    """
    LLM Loop Engine for Python - allows multi-step tool calling.
    Similar to Java LlmLoopEngine but simplified for Python bot.
    """

    def __init__(self, mcp_handler: McpToolsCommandHandler,
                 llm_callback: Callable[[str, str], str],
                 max_steps: int = 8):
        self.mcp_handler = mcp_handler
        self.llm_callback = llm_callback
        self.max_steps = max_steps

    async def run(self, system_prompt: str, user_prompt: str) -> str:
        """
        Run a loop that lets LLM call MCP tools iteratively.

        Args:
            system_prompt: System prompt for LLM
            user_prompt: User's request
            llm_callback: Function to call LLM (system_prompt, current_prompt) -> response

        Returns:
            Final answer after tool calls
        """
        if not self.mcp_handler._initialized:
            await self.mcp_handler.initialize()

        tools_description = "\n".join(
            self.mcp_handler.registry.get_tool_descriptions()
        )

        conversation_history = []
        current_prompt = self._build_initial_prompt(
            user_prompt, tools_description
        )

        for step in range(1, self.max_steps + 1):
            # Get LLM response
            llm_response = self.llm_callback(system_prompt, current_prompt)

            # Parse response for tool calls
            instruction = self._parse_instruction(llm_response)

            if instruction["action"] == "final":
                return instruction.get("final_answer", "No answer provided")

            elif instruction["action"] == "tool_call":
                tool_name = instruction.get("tool")
                tool_args = instruction.get("arguments", {})

                try:
                    # Call the MCP tool
                    result = await self.mcp_handler.registry.call_tool(
                        tool_name, tool_args
                    )
                    result_text = json.dumps(result)

                    # Add to conversation history
                    conversation_history.append({
                        "step": step,
                        "tool": tool_name,
                        "args": tool_args,
                        "result": result_text
                    })

                    # Build next prompt
                    current_prompt = self._build_followup_prompt(
                        user_prompt, tools_description, conversation_history
                    )

                except Exception as e:
                    # Tool call failed, add error to history
                    conversation_history.append({
                        "step": step,
                        "tool": tool_name,
                        "error": str(e)
                    })
                    current_prompt = self._build_followup_prompt(
                        user_prompt, tools_description, conversation_history
                    )

            else:
                return f"Unknown action: {instruction['action']}"

        return f"Reached max steps ({self.max_steps}). Last response: {llm_response}"

    def _build_initial_prompt(self, user_request: str, tools_description: str) -> str:
        """Build the initial prompt with tool descriptions."""
        return f"""You are an assistant that can use MCP tools.

User request: {user_request}

Available tools:
{tools_description}

Respond with JSON only, no markdown fences.
Two valid response shapes:
1) {{"action": "tool_call", "tool": "server_name/tool_name", "arguments": {{...}}}}
2) {{"action": "final", "final_answer": "your response"}}

Think step by step. If you need to use tools to answer, use tool_call first.
If you have the answer, use final."""

    def _build_followup_prompt(self, user_request: str, tools_description: str,
                               history: List[Dict]) -> str:
        """Build followup prompt with conversation history."""
        history_text = "\n\n".join([
            f"Step {h['step']}:\nTool: {h.get('tool', 'N/A')}\n"
            f"Args: {json.dumps(h.get('args', {}))}\n"
            f"Result: {h.get('result', h.get('error', 'N/A'))}"
            for h in history
        ])

        return f"""You are an assistant that can use MCP tools.

User request: {user_request}

Available tools:
{tools_description}

Conversation history:
{history_text}

Based on the tool results above, provide your final answer.
Respond with JSON only:
{{"action": "final", "final_answer": "your response"}}"""

    def _parse_instruction(self, response: str) -> Dict:
        """Parse LLM response to extract action."""
        # Strip markdown fences
        text = response.strip()
        if text.startswith("```json"):
            text = text[7:]
        elif text.startswith("```"):
            text = text[3:]
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()

        try:
            data = json.loads(text)
            action = data.get("action", "unknown")

            if action == "tool_call":
                return {
                    "action": "tool_call",
                    "tool": data.get("tool"),
                    "arguments": data.get("arguments", {})
                }
            elif action == "final":
                return {
                    "action": "final",
                    "final_answer": data.get("final", data.get("final_answer", "No answer"))
                }
            else:
                return {"action": "unknown", "raw": response}

        except json.JSONDecodeError:
            # Assume it's a direct answer
            return {"action": "final", "final_answer": text}
