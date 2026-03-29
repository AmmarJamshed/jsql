"""In-memory ring buffer of activity lines for the desktop Activity tab."""

from __future__ import annotations

import threading
from collections import deque
from datetime import datetime
from typing import Any

_lock = threading.Lock()
_lines: deque[str] = deque(maxlen=800)


def log_line(message: str) -> None:
    ts = datetime.now().strftime("%H:%M:%S")
    line = f"[{ts}] {message}"
    with _lock:
        _lines.append(line)


def get_lines() -> list[str]:
    with _lock:
        return list(_lines)


def as_json() -> dict[str, Any]:
    lines = get_lines()
    return {"lines": lines, "count": len(lines)}
