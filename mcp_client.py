"""
MCP (Model Context Protocol) client for KDZBot.
Supports both stdio and HTTP transports for connecting to MCP servers like OpenSERP.
"""

import asyncio
import json
import logging
import subprocess
import time
import uuid
from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional, Union
from dataclasses import dataclass, field
import aiohttp

logger = logging.getLogger(__name__)


DEFAULT_PROTOCOL_VERSION = "2024-11-05"


@dataclass
class McpToolDescriptor:
    """Description of an MCP tool."""
    name: str
    description: Optional[str] = None
    input_schema: Optional[Dict] = None


@dataclass
class McpServerConfig:
    """Configuration for an MCP server connection."""
    server_name: str
    # For HTTP transport
    url: Optional[str] = None
    bearer_token: Optional[str] = None
    headers: Dict[str, str] = field(default_factory=dict)
    # For stdio transport
    command: Optional[List[str]] = None
    cwd: Optional[str] = None
    env: Dict[str, str] = field(default_factory=dict)
    # Connection settings
    timeout: int = 60
    connect_timeout: int = 15

    def __post_init__(self):
        if not self.url and not self.command:
            raise ValueError("Either url or command must be specified")


class McpClient(ABC):
    """Abstract base class for MCP clients."""

    def __init__(self, config: McpServerConfig):
        self.config = config
        self._initialized = False
        self._id_counter = 0

    def _next_id(self) -> Union[int, str]:
        self._id_counter += 1
        return self._id_counter

    @abstractmethod
    async def initialize(self) -> None:
        """Initialize the MCP connection."""
        pass

    @abstractmethod
    async def list_tools(self) -> List[McpToolDescriptor]:
        """List available tools from the server."""
        pass

    @abstractmethod
    async def call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call a tool with the given arguments."""
        pass

    @abstractmethod
    async def close(self) -> None:
        """Close the connection."""
        pass

    def _create_request(self, method: str, params: Optional[Dict] = None) -> Dict:
        """Create a JSON-RPC request."""
        return {
            "jsonrpc": "2.0",
            "id": self._next_id(),
            "method": method,
            "params": params or {}
        }

    def _create_notification(self, method: str, params: Optional[Dict] = None) -> Dict:
        """Create a JSON-RPC notification (no id)."""
        return {
            "jsonrpc": "2.0",
            "method": method,
            "params": params or {}
        }


class McpHttpClient(McpClient):
    """HTTP-based MCP client using JSON-RPC."""

    def __init__(self, config: McpServerConfig):
        super().__init__(config)
        self.session: Optional[aiohttp.ClientSession] = None

    async def initialize(self) -> None:
        """Initialize HTTP connection and perform MCP handshake."""
        if self._initialized:
            return

        timeout = aiohttp.ClientTimeout(
            total=self.config.timeout,
            connect=self.config.connect_timeout
        )
        self.session = aiohttp.ClientSession(timeout=timeout)

        # MCP initialize handshake
        init_params = {
            "protocolVersion": DEFAULT_PROTOCOL_VERSION,
            "capabilities": {"tools": {}},
            "clientInfo": {
                "name": "kdzbot-mcp-client",
                "version": "1.0.0"
            }
        }

        await self._send_request("initialize", init_params)
        await self._send_notification("notifications/initialized", {})

        self._initialized = True
        logger.info(f"MCP HTTP client initialized for server '{self.config.server_name}'")

    async def list_tools(self) -> List[McpToolDescriptor]:
        """List available tools."""
        if not self._initialized:
            raise RuntimeError("Client not initialized")

        result = await self._send_request("tools/list", {})
        tools = []

        for tool_node in result.get("tools", []):
            name = tool_node.get("name")
            if not name:
                continue
            tools.append(McpToolDescriptor(
                name=name,
                description=tool_node.get("description"),
                input_schema=tool_node.get("inputSchema")
            ))

        return tools

    async def call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call a tool with arguments."""
        if not self._initialized:
            raise RuntimeError("Client not initialized")

        params = {
            "name": tool_name,
            "arguments": arguments
        }

        result = await self._send_request("tools/call", params)
        return result

    async def close(self) -> None:
        """Close HTTP session."""
        if self.session:
            await self.session.close()
            self.session = None
        self._initialized = False

    async def _send_request(self, method: str, params: Optional[Dict] = None) -> Dict:
        """Send JSON-RPC request and return result."""
        request = self._create_request(method, params)

        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json"
        }

        if self.config.bearer_token:
            headers["Authorization"] = f"Bearer {self.config.bearer_token}"

        headers.update(self.config.headers)

        try:
            async with self.session.post(
                self.config.url,
                json=request,
                headers=headers
            ) as response:
                if response.status != 200:
                    raise RuntimeError(
                        f"MCP server '{self.config.server_name}' returned status {response.status}"
                    )

                data = await response.json()

                if "error" in data:
                    raise RuntimeError(
                        f"MCP server '{self.config.server_name}' error: {data['error']}"
                    )

                return data.get("result", {})

        except aiohttp.ClientError as e:
            raise RuntimeError(
                f"Failed to call MCP server '{self.config.server_name}': {e}"
            )

    async def _send_notification(self, method: str, params: Optional[Dict] = None) -> None:
        """Send JSON-RPC notification (fire-and-forget)."""
        notification = self._create_notification(method, params)

        headers = {"Content-Type": "application/json"}
        if self.config.bearer_token:
            headers["Authorization"] = f"Bearer {self.config.bearer_token}"
        headers.update(self.config.headers)

        try:
            async with self.session.post(
                self.config.url,
                json=notification,
                headers=headers
            ):
                pass  # Notifications don't wait for response
        except Exception:
            pass  # Best effort for notifications


class McpStdioClient(McpClient):
    """Stdio-based MCP client for running local MCP servers via subprocess."""

    def __init__(self, config: McpServerConfig):
        super().__init__(config)
        self.process: Optional[subprocess.Popen] = None
        self._lock = asyncio.Lock()
        self._pending_responses: Dict[Union[int, str], asyncio.Future] = {}
        self._reader_task: Optional[asyncio.Task] = None
        self._stdin_writer = None

    async def initialize(self) -> None:
        """Start subprocess and perform MCP handshake."""
        if self._initialized:
            return

        if not self.config.command:
            raise ValueError("Command is required for stdio client")

        logger.info(f"Starting MCP stdio process: {' '.join(self.config.command)}")

        # Start subprocess
        try:
            self.process = subprocess.Popen(
                self.config.command,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=self.config.cwd,
                env={**dict(), **self.config.env}  # Current env + custom env
            )
        except Exception as e:
            raise RuntimeError(f"Failed to start MCP process: {e}")

        # Start reader task
        self._reader_task = asyncio.create_task(self._read_loop())

        # Wait a moment for process to start
        await asyncio.sleep(0.5)

        # MCP initialize handshake
        init_params = {
            "protocolVersion": DEFAULT_PROTOCOL_VERSION,
            "capabilities": {"tools": {}},
            "clientInfo": {
                "name": "kdzbot-mcp-client",
                "version": "1.0.0"
            }
        }

        await self._send_request("initialize", init_params)
        await self._send_notification("notifications/initialized", {})

        self._initialized = True
        logger.info(f"MCP stdio client initialized for server '{self.config.server_name}'")

    async def list_tools(self) -> List[McpToolDescriptor]:
        """List available tools."""
        if not self._initialized:
            raise RuntimeError("Client not initialized")

        result = await self._send_request("tools/list", {})
        tools = []

        for tool_node in result.get("tools", []):
            name = tool_node.get("name")
            if not name:
                continue
            tools.append(McpToolDescriptor(
                name=name,
                description=tool_node.get("description"),
                input_schema=tool_node.get("inputSchema")
            ))

        return tools

    async def call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call a tool with arguments."""
        if not self._initialized:
            raise RuntimeError("Client not initialized")

        params = {
            "name": tool_name,
            "arguments": arguments
        }

        result = await self._send_request("tools/call", params)
        return result

    async def close(self) -> None:
        """Terminate subprocess."""
        if self._reader_task:
            self._reader_task.cancel()
            try:
                await self._reader_task
            except asyncio.CancelledError:
                pass

        if self.process:
            self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()

        self._initialized = False
        self._pending_responses.clear()

    async def _read_loop(self):
        """Background task to read stdout from subprocess."""
        try:
            while True:
                # Read line from stdout
                line = await asyncio.get_event_loop().run_in_executor(
                    None, self.process.stdout.readline
                )

                if not line:
                    break

                line = line.decode('utf-8').strip()
                if not line:
                    continue

                try:
                    data = json.loads(line)
                    response_id = data.get("id")

                    # Check if this is a response we're waiting for
                    if response_id is not None and response_id in self._pending_responses:
                        future = self._pending_responses.pop(response_id)
                        if not future.done():
                            future.set_result(data)
                except json.JSONDecodeError:
                    logger.warning(f"Invalid JSON from MCP server: {line[:200]}")

        except Exception as e:
            logger.error(f"MCP read loop error: {e}")

    async def _send_request(self, method: str, params: Optional[Dict] = None) -> Dict:
        """Send JSON-RPC request and wait for response."""
        request = self._create_request(method, params)
        request_id = request["id"]

        # Create future for response
        future = asyncio.get_event_loop().create_future()
        self._pending_responses[request_id] = future

        # Send message
        await self._send_message(request)

        # Wait for response with timeout
        try:
            response = await asyncio.wait_for(future, timeout=self.config.timeout)
        except asyncio.TimeoutError:
            self._pending_responses.pop(request_id, None)
            raise RuntimeError(f"MCP request timeout for method '{method}'")

        if "error" in response:
            raise RuntimeError(
                f"MCP server '{self.config.server_name}' error: {response['error']}"
            )

        return response.get("result", {})

    async def _send_notification(self, method: str, params: Optional[Dict] = None) -> None:
        """Send JSON-RPC notification."""
        notification = self._create_notification(method, params)
        await self._send_message(notification)

    async def _send_message(self, message: Dict) -> None:
        """Send message to subprocess stdin."""
        payload = json.dumps(message).encode('utf-8')

        def write():
            try:
                self.process.stdin.write(payload + b'\n')
                self.process.stdin.flush()
            except BrokenPipeError:
                raise RuntimeError("MCP subprocess stdin closed")

        await asyncio.get_event_loop().run_in_executor(None, write)


class McpClientFactory:
    """Factory for creating MCP clients."""

    @staticmethod
    def create(config: McpServerConfig) -> McpClient:
        """Create appropriate MCP client based on config."""
        if config.command:
            return McpStdioClient(config)
        elif config.url:
            return McpHttpClient(config)
        else:
            raise ValueError("Config must specify either command or url")


class McpToolRegistry:
    """Registry for managing multiple MCP servers and their tools."""

    def __init__(self):
        self.clients: Dict[str, McpClient] = {}
        self.tools: Dict[str, McpToolDescriptor] = {}
        self.tool_to_server: Dict[str, str] = {}  # tool_name -> server_name

    async def add_server(self, config: McpServerConfig) -> None:
        """Add and initialize an MCP server."""
        client = McpClientFactory.create(config)
        await client.initialize()

        # Register client
        self.clients[config.server_name] = client

        # Register tools
        tools = await client.list_tools()
        for tool in tools:
            qualified_name = f"{config.server_name}/{tool.name}"
            self.tools[qualified_name] = tool
            self.tool_to_server[qualified_name] = config.server_name
            logger.info(f"Registered MCP tool: {qualified_name}")

    async def call_tool(self, qualified_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """Call a tool by its qualified name (server_name/tool_name)."""
        if qualified_name not in self.tools:
            raise ValueError(f"Unknown tool: {qualified_name}")

        server_name = self.tool_to_server[qualified_name]
        client = self.clients[server_name]
        tool = self.tools[qualified_name]

        return await client.call_tool(tool.name, arguments)

    def get_tool_descriptions(self) -> List[str]:
        """Get formatted descriptions of all tools."""
        descriptions = []
        for qualified_name, tool in self.tools.items():
            desc = f"- {qualified_name}"
            if tool.description:
                desc += f": {tool.description}"
            descriptions.append(desc)
        return descriptions

    def get_tools_for_server(self, server_name: str) -> List[str]:
        """Get qualified names of tools for a specific server."""
        return [
            name for name, srv in self.tool_to_server.items()
            if srv == server_name
        ]

    async def close_all(self) -> None:
        """Close all MCP connections."""
        for client in self.clients.values():
            try:
                await client.close()
            except Exception:
                pass
        self.clients.clear()
        self.tools.clear()
        self.tool_to_server.clear()
