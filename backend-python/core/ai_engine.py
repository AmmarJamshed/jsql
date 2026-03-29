"""Ollama HTTP client: generate, NL→SQL, insights."""

from __future__ import annotations

import json
import os
import re
from typing import Any

import httpx

OLLAMA_GENERATE = "http://localhost:11434/api/generate"
# Bare "llama3" often 404s on newer Ollama; llama3.2 is a common default after `ollama pull llama3.2`
DEFAULT_MODEL = "llama3.2"
DEFAULT_TIMEOUT = 120.0

# Generic names only — exact tags (e.g. qwen2.5:3b) come from Ollama /api/tags first.
_MODEL_FALLBACKS = (
    "llama3.2",
    "llama3.1",
    "llama3",
    "mistral",
    "phi3",
    "gemma2",
)


def _extract_sql(text: str) -> str:
    text = text.strip()
    m = re.search(r"```(?:sql)?\s*([\s\S]*?)```", text, re.IGNORECASE)
    if m:
        return m.group(1).strip().rstrip(";")
    lines = [ln for ln in text.splitlines() if ln.strip() and not ln.strip().lower().startswith("here")]
    candidate = "\n".join(lines).strip()
    if "select" in candidate.lower():
        idx = candidate.lower().find("select")
        return candidate[idx:].split(";")[0].strip()
    return candidate


def _parse_ollama_error_body(response: httpx.Response) -> str:
    try:
        data = response.json()
        err = data.get("error")
        if isinstance(err, str) and err.strip():
            return err.strip()
    except Exception:
        pass
    text = (response.text or "").strip()
    return text[:300] if text else ""


class AiEngine:
    def __init__(
        self,
        base_url: str = "http://localhost:11434",
        model: str = DEFAULT_MODEL,
        enabled: bool = True,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.enabled = enabled
        self._installed_cache: list[str] = []
        self._installed_cache_at: float = 0.0

    def set_enabled(self, on: bool) -> None:
        self.enabled = on

    def _installed_model_names(self) -> list[str]:
        """Names exactly as Ollama lists them (e.g. qwen2.5:3b). Cached ~45s."""
        import time

        now = time.time()
        if self._installed_cache and (now - self._installed_cache_at) < 45:
            return self._installed_cache
        self._installed_cache = self.list_local_models()
        self._installed_cache_at = now
        return self._installed_cache

    def _models_to_try(self) -> list[str]:
        extra = os.environ.get("OLLAMA_MODEL_FALLBACKS", "")
        extra_list = [x.strip() for x in extra.split(",") if x.strip()]
        seen: set[str] = set()
        out: list[str] = []
        for m in (self.model, *extra_list):
            if m and m not in seen:
                seen.add(m)
                out.append(m)
        # Use whatever is actually installed (fixes qwen2.5 vs qwen2.5:3b, llama3.2:latest, etc.)
        for name in self._installed_model_names():
            if name not in seen:
                seen.add(name)
                out.append(name)
        for m in _MODEL_FALLBACKS:
            if m not in seen:
                seen.add(m)
                out.append(m)
        return out

    def list_local_models(self) -> list[str]:
        try:
            with httpx.Client(timeout=5.0) as client:
                r = client.get(f"{self.base_url}/api/tags")
                if r.is_success:
                    return [m.get("name", "") for m in r.json().get("models", []) if m.get("name")]
        except Exception:
            pass
        return []

    def _fail_http(self, response: httpx.Response, tried_models: list[str]) -> dict[str, Any]:
        code = response.status_code
        body = _parse_ollama_error_body(response)
        installed = self.list_local_models()
        if code == 404:
            lines = [
                f"No working model found. Tried: {', '.join(tried_models)}.",
                body or "Ollama returned 404 (unknown model name or wrong tag).",
            ]
            if installed:
                lines.append(f"Models on this PC: {', '.join(installed[:12])}")
                lines.append("Set OLLAMA_MODEL to the exact name above (including the tag, e.g. qwen2.5:3b).")
            else:
                lines.append("No models listed. Is Ollama running? Run: ollama pull llama3.2")
            return {"ok": False, "error": " ".join(lines), "response": ""}
        msg = f"Ollama HTTP {code}"
        if body:
            msg += f": {body}"
        return {"ok": False, "error": msg, "response": ""}

    def _post_generate(self, prompt: str, stream: bool = False) -> dict[str, Any]:
        if not self.enabled:
            return {"ok": False, "error": "AI mode is OFF. Enable it in the app.", "response": ""}
        url = f"{self.base_url}/api/generate"
        tried: list[str] = []
        last_resp: httpx.Response | None = None
        try:
            with httpx.Client(timeout=DEFAULT_TIMEOUT) as client:
                for model_name in self._models_to_try():
                    tried.append(model_name)
                    payload = {"model": model_name, "prompt": prompt, "stream": stream}
                    r = client.post(url, json=payload)
                    if r.status_code == 404:
                        last_resp = r
                        continue
                    if r.is_success:
                        data = r.json()
                        return {
                            "ok": True,
                            "response": (data.get("response") or "").strip(),
                            "raw": data,
                            "model_used": model_name,
                        }
                    last_resp = r
                    try:
                        r.raise_for_status()
                    except httpx.HTTPStatusError:
                        return self._fail_http(r, tried)
                if last_resp is not None:
                    return self._fail_http(last_resp, tried)
                return {
                    "ok": False,
                    "error": "No model candidates to try. Set OLLAMA_MODEL.",
                    "response": "",
                }
        except httpx.ConnectError:
            return {
                "ok": False,
                "error": "Ollama is not running on this PC. Install it from https://ollama.com/download, start the Ollama app, then try again (it must listen on localhost:11434).",
                "response": "",
            }
        except Exception as e:
            return {"ok": False, "error": str(e), "response": ""}

    def natural_language_to_sql(self, user_question: str, schema_context: str) -> dict[str, Any]:
        prompt = f"""You are a SQL expert for DuckDB. The database has these tables (use exact table and column names):

{schema_context}

Rules:
- Output ONLY one SQL SELECT statement, no markdown unless wrapping in ```sql```.
- Use DuckDB SQL syntax.
- Do not explain; only the query.

Question: {user_question}
"""
        out = self._post_generate(prompt)
        if not out["ok"]:
            return {**out, "sql": ""}
        sql = _extract_sql(out["response"])
        return {**out, "sql": sql}

    def explain_dataset(self, schema_context: str) -> dict[str, Any]:
        prompt = f"""Briefly explain this dataset for an analyst (bullet points, max 8 bullets):

{schema_context}
"""
        return self._post_generate(prompt)

    def summarize_results(self, columns: list[str], sample_rows: list[dict[str, Any]], max_chars: int = 4000) -> dict[str, Any]:
        payload = json.dumps({"columns": columns, "sample": sample_rows[:50]}, default=str)[:max_chars]
        prompt = f"""Summarize these query results in 3-5 short bullets for a business user:

{payload}
"""
        return self._post_generate(prompt)

    def health_ollama(self) -> dict[str, Any]:
        try:
            with httpx.Client(timeout=5.0) as client:
                r = client.get(f"{self.base_url}/api/tags")
                r.raise_for_status()
                models = [m.get("name", "") for m in r.json().get("models", []) if m.get("name")]
            return {"reachable": True, "detail": "ok", "models": models}
        except Exception as e:
            return {"reachable": False, "detail": str(e), "models": []}
