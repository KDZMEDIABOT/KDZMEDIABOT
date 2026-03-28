# ai_command.py
"""
AI command handler for KDZMEDIABOT.
Manages prompt loading and routes AI requests through WebSocket to KDZJAVACORE.
"""

import os
import logging
import time
from typing import Optional, Callable
from threading import Lock

from websocket_client import WebSocketClient, ThreadSafeWebSocketClient

logger = logging.getLogger(__name__)


class AiCommandHandler:
    """
    Handles AI command (!ai /ai) for IRC/Telegram bot.

    Loads system prompt from KDZPROMPT.txt and sends requests via WebSocket
    to KDZJAVACORE which calls the LLM.
    """

    DEFAULT_PROMPT_FILE = "KDZPROMPT.txt"
    DEFAULT_TIMEOUT = 60

    def __init__(self, config: dict, response_callback: Optional[Callable] = None):
        """
        Initialize AI command handler.

        config: dict from local.json, containing:
            - websocket: WebSocket connection settings
            - ai: AI command settings (prompt_file, timeout, max_length)
        """
        self.config = config or {}
        self.response_callback = response_callback

        # Load AI-specific config
        ai_config = self.config.get("ai", {})
        self.prompt_file = ai_config.get("prompt_file", self.DEFAULT_PROMPT_FILE)
        self.timeout = ai_config.get("timeout_seconds", self.DEFAULT_TIMEOUT)
        self.max_length = ai_config.get("max_response_length", 2000)

        # Load system prompt
        self.system_prompt = self._load_prompt()
        self.prompt_last_modified = 0

        # Initialize WebSocket client
        ws_config = self.config.get("websocket", {})
        self.ws_client = ThreadSafeWebSocketClient(ws_config, response_callback)

        self._lock = Lock()
        self._pending_responses: dict = {}  # request_id -> callback info

        logger.info(f"AiCommandHandler initialized, prompt file: {self.prompt_file}")

    def start(self):
        """Start the WebSocket connection."""
        self.ws_client.start()
        logger.info("AiCommandHandler WebSocket started")

    def stop(self):
        """Stop the WebSocket connection."""
        self.ws_client.stop()
        logger.info("AiCommandHandler WebSocket stopped")

    def _load_prompt(self) -> str:
        """Load or reload the system prompt from file."""
        try:
            if os.path.exists(self.prompt_file):
                with open(self.prompt_file, 'r', encoding='utf-8') as f:
                    prompt = f.read().strip()
                self.prompt_last_modified = os.path.getmtime(self.prompt_file)
                logger.info(f"Loaded system prompt from {self.prompt_file} "
                           f"({len(prompt)} chars)")
                return prompt
            else:
                logger.warning(f"Prompt file {self.prompt_file} not found, using default")
                return self._default_prompt()
        except Exception as e:
            logger.error(f"Error loading prompt file: {e}")
            return self._default_prompt()

    def _check_prompt_reload(self):
        """Check if prompt file has been modified and reload."""
        try:
            if os.path.exists(self.prompt_file):
                mtime = os.path.getmtime(self.prompt_file)
                if mtime > self.prompt_last_modified:
                    logger.info(f"Prompt file modified, reloading...")
                    self.system_prompt = self._load_prompt()
        except Exception as e:
            logger.error(f"Error checking prompt file: {e}")

    def _default_prompt(self) -> str:
        """Return default prompt if file is missing."""
        return (
            "You are a helpful cryptocurrency and financial assistant. "
            "Provide accurate, concise information about crypto markets, "
            "prices, and financial data. Be friendly but professional."
        )

    def is_available(self) -> bool:
        """Check if AI command is available (WebSocket connected)."""
        return self.ws_client.is_connected()

    def handle_ai_command(self, user_id: str, channel: str,
                         platform: str, aiContext) -> str:
        """
        Handle the !ai /ai command.

        Args:
            query: The user's question
            user_id: User identifier (IRC nick or Telegram user ID)
            channel: Channel name or chat ID
            platform: "irc" or "telegram"

        Returns:
            The AI response or error message
        """
        # Check for prompt file reload
        self._check_prompt_reload()

        if not self.is_available():
            return ("Error: AI service is not available. "
                   "WebSocket connection to backend is not established.")

        logger.info(f"AI request from {user_id} on {platform}/{channel}: {query[:100]}...")

        # Send request and get response
        try:
            response = self.ws_client.ai_request(
                user_id=user_id,
                platform=platform,
                channel=channel,
                system_prompt=self.system_prompt,
                ai_context=aiContext
            )

            if response is None:
                return "Error: Failed to get response from AI service"

            if response.startswith("Error:"):
                return response

            # Truncate if too long
            if len(response) > self.max_length:
                response = response[:self.max_length] + "... [truncated]"

            logger.info(f"AI response sent to {user_id}: {response[:100]}...")
            return response

        except Exception as e:
            logger.error(f"AI command exception: {e}")
            return f"Error processing AI request: {e}"

    def get_status(self) -> dict:
        """Return current status info."""
        return {
            "connected": self.is_available(),
            "prompt_file": self.prompt_file,
            "prompt_loaded": len(self.system_prompt),
            "timeout": self.timeout
        }
