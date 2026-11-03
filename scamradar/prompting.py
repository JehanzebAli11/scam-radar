"""The single prompt format shared by training, inference and the prompt baseline."""

from __future__ import annotations

USER_PREFIX = "Classify this message:"


def user_content(text: str, prefix: str = USER_PREFIX) -> str:
    return f"{prefix}\n{text}"


def chat_messages(text: str, prefix: str = USER_PREFIX) -> list[dict[str, str]]:
    """User turn only; the model replies with the compact line."""
    return [{"role": "user", "content": user_content(text, prefix)}]


def render_gemma(text: str, completion: str | None = None, prefix: str = USER_PREFIX) -> str:
    """Render the Gemma 3 chat format without a tokenizer (used by dry runs and docs).

    Matches the template shipped with ``google/gemma-3-270m-it`` for a single
    user turn. Real training uses ``tokenizer.apply_chat_template``.
    """
    out = (
        f"<bos><start_of_turn>user\n{user_content(text, prefix)}<end_of_turn>\n"
        "<start_of_turn>model\n"
    )
    if completion is not None:
        out += f"{completion}<end_of_turn>\n"
    return out
