"""
Model factory.

`init_chat_model("provider:model")` is a LangChain helper that returns the right
chat-model class for ANY supported provider. Because every other file only uses
the generic interface, the user can switch OpenAI <-> Anthropic <-> Gemini by
changing one environment variable. This mirrors the "bring your own API key"
design of the extension.
"""
import os

from langchain.chat_models import init_chat_model

DEFAULT_MODEL = "anthropic:claude-haiku-5-5"  # cheap + fast is right for extraction


def get_llm(model: str | None = None, temperature: float = 0.0):
    """temperature=0 -> most deterministic output, best for data extraction."""
    model = model or os.getenv("APPLYPILOT_MODEL", DEFAULT_MODEL)
    return init_chat_model(model, temperature=temperature)
