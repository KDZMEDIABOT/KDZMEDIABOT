# websocket_client.py
"""
WebSocket client for KDZMEDIABOT to communicate with KDZJAVACORE.
Supports auto-reconnect, heartbeat, and async request-response pattern.
"""

import asyncio
import json
import logging
import time
import uuid
from typing import Callable, Dict, Optional, Any
from threading import Thread, Lock
import traceback

try:
    import websockets
    from websockets.client import WebSocketClientProtocol
    WEBSOCKETS_AVAILABLE = True
except ImportError:
    WEBSOCKETS_AVAILABLE = False
    print("WARNING: websockets library not available. Run: pip install websockets")


logger = logging.getLogger(__name__)


class WebSocketClient:
    """
    Async WebSocket client with auto-reconnect and request-response correlation.

    Usage:
        ws_client = WebSocketClient("ws://localhost:8080/ws/bot", response_callback)
        ws_client.start()
        response = await ws_client.send_request({"type": "ai_request", ...})
    """

    def __init__(
        self,
        url: str,
        response_callback: Optional[Callable[[dict], None]] = None,
        reconnect_delay: int = 5,
        max_reconnect_attempts: int = 10,
        heartbeat_interval: int = 30,
        timeout: int = 60
    ):
        self.url = url
        self.response_callback = response_callback
        self.reconnect_delay = reconnect_delay
        self.max_reconnect_attempts = max_reconnect_attempts
        self.heartbeat_interval = heartbeat_interval
        self.timeout = timeout

        self.ws: Optional[WebSocketClientProtocol] = None
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.thread: Optional[Thread] = None
        self.running = False
        self.connected = False
        self.reconnect_count = 0

        # Request-response correlation
        self._pending_requests: Dict[str, asyncio.Future] = {}
        self._pending_lock = Lock()

        # Message queue for outgoing messages
        self._outgoing_queue: asyncio.Queue = asyncio.Queue()

    def start(self):
        """Start the WebSocket client in a background thread."""
        if not WEBSOCKETS_AVAILABLE:
            logger.error("Cannot start WebSocket: websockets library not installed")
            return False

        if self.running:
            logger.warning("WebSocket client already running")
            return True

        self.running = True
        self.thread = Thread(target=self._run, daemon=True)
        self.thread.start()
        logger.info(f"WebSocket client thread started, connecting to {self.url}")
        return True

    def stop(self):
        """Stop the WebSocket client."""
        self.running = False
        if self.loop:
            asyncio.run_coroutine_threadsafe(self._disconnect(), self.loop)
        if self.thread:
            self.thread.join(timeout=5)

    def _run(self):
        """Main thread entry point."""
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self._main_loop())

    async def _main_loop(self):
        """Main connection loop with auto-reconnect."""
        while self.running:
            try:
                await self._connect()
                self.reconnect_count = 0

                # Run message handlers
                await asyncio.gather(
                    self._receive_loop(),
                    self._send_loop(),
                    self._heartbeat_loop()
                )

            except Exception as e:
                logger.error(f"WebSocket error: {e}")
                logger.debug(traceback.format_exc())

            self.connected = False
            self.ws = None

            if not self.running:
                break

            # Auto-reconnect with backoff
            self.reconnect_count += 1
            if self.reconnect_count > self.max_reconnect_attempts:
                logger.error(f"Max reconnect attempts ({self.max_reconnect_attempts}) reached")
                break

            delay = min(self.reconnect_delay * self.reconnect_count, 60)
            logger.info(f"Reconnecting in {delay}s (attempt {self.reconnect_count})...")
            await asyncio.sleep(delay)

    async def _connect(self):
        """Establish WebSocket connection."""
        logger.info(f"Connecting to {self.url}...")

        self.ws = await websockets.connect(
            self.url,
            ping_interval=None,  # We'll handle heartbeat manually
            close_timeout=5
        )

        self.connected = True
        logger.info("WebSocket connected")

        # Send identification message
        identify_msg = {
            "type": "identify",
            "platform": "kdzmediabot",
            "version": "1.0",
            "timestamp": int(time.time())
        }
        await self.ws.send(json.dumps(identify_msg))

    async def _disconnect(self):
        """Close WebSocket connection."""
        if self.ws:
            await self.ws.close()
            self.ws = None
        self.connected = False

    async def _receive_loop(self):
        """Receive messages from WebSocket."""
        while self.running and self.ws:
            try:
                message = await self.ws.recv()
                if isinstance(message, str):
                    data = json.loads(message)
                    await self._handle_message(data)
                else:
                    logger.warning(f"Received non-text message: {message}")

            except websockets.exceptions.ConnectionClosed:
                logger.info("WebSocket connection closed")
                break
            except json.JSONDecodeError as e:
                logger.error(f"Failed to decode JSON: {e}")
            except Exception as e:
                logger.error(f"Receive error: {e}")

    async def _send_loop(self):
        """Send messages from outgoing queue."""
        while self.running:
            try:
                message = await asyncio.wait_for(
                    self._outgoing_queue.get(),
                    timeout=0.1
                )
                if self.ws and self.connected:
                    await self.ws.send(json.dumps(message))
                    logger.debug(f"Sent: {message.get('type', 'unknown')}")
            except asyncio.TimeoutError:
                continue
            except Exception as e:
                logger.error(f"Send error: {e}")

    async def _heartbeat_loop(self):
        """Send periodic heartbeat/ping messages."""
        while self.running:
            await asyncio.sleep(self.heartbeat_interval)
            if self.ws and self.connected:
                try:
                    ping_msg = {
                        "type": "ping",
                        "timestamp": int(time.time())
                    }
                    await self.ws.send(json.dumps(ping_msg))
                except Exception as e:
                    logger.error(f"Heartbeat failed: {e}")

    async def _handle_message(self, data: dict):
        """Handle incoming WebSocket message."""
        msg_type = data.get("type", "unknown")
        logger.debug(f"Received message type: {msg_type}")

        if msg_type == "pong":
            pass  # Heartbeat response

        elif msg_type == "ai_response":
            request_id = data.get("request_id")
            if request_id:
                with self._pending_lock:
                    future = self._pending_requests.pop(request_id, None)
                if future and not future.done():
                    future.set_result(data)

            # Also call the callback if registered
            if self.response_callback:
                try:
                    self.response_callback(data)
                except Exception as e:
                    logger.error(f"Response callback error: {e}")

        elif self.response_callback:
            # Pass other messages to callback
            try:
                self.response_callback(data)
            except Exception as e:
                logger.error(f"Callback error: {e}")

    async def send_request(self, data: dict) -> Optional[dict]:
        """
        Send a request and wait for response.

        Returns the response dict or None if timed out.
        """
        if not self.connected or not self.ws:
            logger.error("WebSocket not connected")
            return None

        # Generate request ID if not present
        request_id = data.get("request_id", str(uuid.uuid4()))
        data["request_id"] = request_id

        # Create future for response
        future = self.loop.create_future()

        with self._pending_lock:
            self._pending_requests[request_id] = future

        # Send message
        await self._outgoing_queue.put(data)

        # Wait for response
        try:
            response = await asyncio.wait_for(future, timeout=self.timeout)
            return response
        except asyncio.TimeoutError:
            logger.warning(f"Request {request_id} timed out")
            with self._pending_lock:
                self._pending_requests.pop(request_id, None)
            return None

    async def send_notification(self, data: dict) -> bool:
        """Send a fire-and-forget message."""
        if not self.connected or not self.ws:
            return False
        await self._outgoing_queue.put(data)
        return True

    def is_connected(self) -> bool:
        """Check if WebSocket is connected."""
        return self.connected and self.ws is not None


class ThreadSafeWebSocketClient:
    """
    Thread-safe wrapper around WebSocketClient for sync usage from bot code.

    Usage:
        ws = ThreadSafeWebSocketClient("ws://...")
        ws.start()
        response = ws.ai_request("What is Bitcoin?", system_prompt="...")
    """

    def __init__(self, config: dict, response_handler: Optional[Callable] = None):
        """
        config: dict from local.json websocket section
        """
        self.ws_config = config
        self.response_handler = response_handler
        self.client: Optional[WebSocketClient] = None
        self.enabled = WEBSOCKETS_AVAILABLE and config.get("enabled", True)

    def start(self):
        """Start the WebSocket client."""
        if not self.enabled:
            logger.info("WebSocket client disabled in config")
            return

        url = self.ws_config.get("url", "ws://localhost:8080/ws/bot")
        reconnect = self.ws_config.get("reconnect_delay", 5)
        heartbeat = self.ws_config.get("heartbeat_interval", 30)
        timeout = self.ws_config.get("timeout_seconds", 60)

        self.client = WebSocketClient(
            url=url,
            response_callback=self._on_response,
            reconnect_delay=reconnect,
            heartbeat_interval=heartbeat,
            timeout=timeout
        )
        self.client.start()

    def stop(self):
        """Stop the WebSocket client."""
        if self.client:
            self.client.stop()
            self.client = None

    def _on_response(self, data: dict):
        """Handle incoming response."""
        if self.response_handler:
            try:
                self.response_handler(data)
            except Exception as e:
                logger.error(f"Response handler error: {e}")

    def ai_request(self, query: str, user_id: str, platform: str,
                   channel: str, system_prompt: str = "") -> Optional[str]:
        """
        Send AI request and wait for response.

        Returns the AI response text or None on error/timeout.
        """
        if not self.client or not self.client.is_connected():
            logger.error("WebSocket not connected")
            return None

        request = {
            "type": "ai_request",
            "user_id": user_id,
            "channel": channel,
            "platform": platform,
            "system_prompt": system_prompt,
            "user_query": query,
            "timestamp": int(time.time())
        }

        # Run async request in the event loop
        try:
            future = asyncio.run_coroutine_threadsafe(
                self.client.send_request(request),
                self.client.loop
            )
            response = future.result(timeout=self.ws_config.get("timeout_seconds", 60))

            if response and response.get("status") == "success":
                return response.get("response", "")
            elif response:
                error = response.get("error", "Unknown error")
                logger.error(f"AI request failed: {error}")
                return f"Error: {error}"
            else:
                return "Error: Request timed out or no response"

        except Exception as e:
            logger.error(f"AI request exception: {e}")
            return f"Error: {e}"

    def is_connected(self) -> bool:
        """Check if connected."""
        return self.client is not None and self.client.is_connected()
