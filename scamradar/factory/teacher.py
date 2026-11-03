"""Client for the local open-weight teacher model (OpenAI-compatible HTTP API).

The teacher runs on the DGX Spark (e.g. vLLM or SGLang serving
``google/gemma-4-31b-it`` at ``http://localhost:8000/v1``). This module talks
plain HTTP with ``httpx``; it deliberately does not use any vendor SDK, and it
refuses hosted proprietary APIs (Anthropic, OpenAI, Google Gemini).

Anything that needs a teacher takes a :class:`ChatClient`, so tests inject a
fake.
"""

from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable
from urllib.parse import urlparse

import httpx

DEFAULT_BASE_URL = "http://localhost:8000/v1"
DEFAULT_TEACHER_MODEL = "google/gemma-4-31b-it"
ENV_BASE_URL = "SCAMRADAR_TEACHER_BASE_URL"
ENV_MODEL = "SCAMRADAR_TEACHER_MODEL"
ENV_API_KEY = "SCAMRADAR_TEACHER_API_KEY"

#: Hosted proprietary model APIs this project must never call.
FORBIDDEN_HOST_SUFFIXES = (
    "anthropic.com",
    "claude.ai",
    "openai.com",
    "openai.azure.com",
    "generativelanguage.googleapis.com",
    "aiplatform.googleapis.com",
    "gemini.google.com",
)

Message = dict[str, str]


class ForbiddenEndpointError(ValueError):
    """Raised when a client is pointed at a hosted proprietary model API."""


@runtime_checkable
class ChatClient(Protocol):
    """Minimal chat interface. ``model`` is recorded in provenance."""

    model: str

    def chat(
        self, messages: list[Message], *, temperature: float = 0.0, max_tokens: int = 256
    ) -> str: ...


def check_endpoint_allowed(base_url: str) -> None:
    host = (urlparse(base_url).hostname or "").lower()
    for suffix in FORBIDDEN_HOST_SUFFIXES:
        if host == suffix or host.endswith("." + suffix):
            raise ForbiddenEndpointError(
                f"{host} is a hosted proprietary model API. Scam Radar only uses an "
                "open-weight teacher served locally (see README: ground rules)."
            )


def is_loopback(base_url: str) -> bool:
    host = (urlparse(base_url).hostname or "").lower()
    return host in ("localhost", "::1") or host.startswith("127.")


def prompt_hash(messages: list[Message]) -> str:
    """Stable short hash of a prompt, recorded for provenance."""
    blob = json.dumps(messages, ensure_ascii=False, sort_keys=True).encode("utf-8")
    return hashlib.sha256(blob).hexdigest()[:16]


@dataclass
class OpenAICompatibleClient:
    """Plain-HTTP client for ``POST {base_url}/chat/completions``."""

    base_url: str = DEFAULT_BASE_URL
    model: str = DEFAULT_TEACHER_MODEL
    api_key: str | None = None
    timeout: float = 120.0
    transport: httpx.BaseTransport | None = None
    _client: httpx.Client = field(init=False, repr=False)

    def __post_init__(self) -> None:
        self.base_url = self.base_url.rstrip("/")
        check_endpoint_allowed(self.base_url)
        headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}
        self._client = httpx.Client(
            base_url=self.base_url,
            timeout=self.timeout,
            headers=headers,
            transport=self.transport,
            # A local teacher must never be routed through an HTTP(S)_PROXY.
            trust_env=not is_loopback(self.base_url),
        )

    @classmethod
    def from_env(
        cls, base_url: str | None = None, model: str | None = None
    ) -> OpenAICompatibleClient:
        return cls(
            base_url=base_url or os.environ.get(ENV_BASE_URL, DEFAULT_BASE_URL),
            model=model or os.environ.get(ENV_MODEL, DEFAULT_TEACHER_MODEL),
            api_key=os.environ.get(ENV_API_KEY),
        )

    def chat(
        self, messages: list[Message], *, temperature: float = 0.0, max_tokens: int = 256
    ) -> str:
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        resp = self._client.post("/chat/completions", json=payload)
        resp.raise_for_status()
        data = resp.json()
        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise ValueError(f"unexpected response shape from teacher: {data!r:.300}") from exc
        return str(content or "")

    def close(self) -> None:
        self._client.close()
