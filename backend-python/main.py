"""JSQL (JamshedSQL) FastAPI backend — DuckDB, FAISS, Ollama."""

from __future__ import annotations

import os
import time
from contextlib import asynccontextmanager
from pathlib import Path

import psutil
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from pydantic import BaseModel, Field
from starlette.requests import Request

from core.activity_log import log_line
from core.ai_engine import AiEngine, DEFAULT_MODEL
from core.ingestion import IngestionStore
from core.pdf_to_csv import pdf_bytes_to_csv_bytes
from core.sql_engine import SqlEngine

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"

sql_engine = SqlEngine()
ingestion = IngestionStore(DATA_DIR / "vectors")
ai_engine = AiEngine(
    base_url=os.environ.get("OLLAMA_HOST", "http://localhost:11434"),
    model=os.environ.get("OLLAMA_MODEL", DEFAULT_MODEL),
    enabled=True,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    log_line("━━━━━━━━ JSQL backend started — API :8765 ━━━━━━━━")
    log_line("Open the Activity tab in the desktop app to watch requests live (no extra console needed).")
    yield
    log_line("Backend process stopping.")


app = FastAPI(title="JSQL API", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def activity_request_log(request: Request, call_next):
    path = request.url.path
    if path == "/activity_log":
        return await call_next(request)
    log_line(f"→ {request.method} {path}")
    t0 = time.perf_counter()
    try:
        response = await call_next(request)
        ms = (time.perf_counter() - t0) * 1000
        log_line(f"← {response.status_code} {request.method} {path} ({ms:.0f} ms)")
        return response
    except Exception as ex:
        ms = (time.perf_counter() - t0) * 1000
        log_line(f"✗ {request.method} {path} failed after {ms:.0f} ms — {ex!s}")
        raise


def system_profile() -> dict:
    vm = psutil.virtual_memory()
    cpu_n = psutil.cpu_count(logical=True) or 1
    ram_gb = round(vm.total / (1024**3), 2)
    suggested_model = DEFAULT_MODEL
    max_rows = 50_000
    if ram_gb < 8:
        suggested_model = "phi3:mini"
        max_rows = 20_000
    if ram_gb < 4:
        max_rows = 5_000
    return {
        "ram_gb": ram_gb,
        "cpu_logical": cpu_n,
        "suggested_model": suggested_model,
        "default_max_rows": max_rows,
    }


_profile = system_profile()


class QuerySqlBody(BaseModel):
    sql: str = Field(..., description="DuckDB SQL")
    max_rows: int | None = Field(None, ge=1, le=500_000)


class NlToSqlBody(BaseModel):
    prompt: str = Field(..., description="Natural language question")
    max_rows: int | None = None


class SemanticSearchBody(BaseModel):
    query: str
    top_k: int = Field(8, ge=1, le=50)


class SummarizeBody(BaseModel):
    columns: list[str]
    rows: list[dict]


class AiModeBody(BaseModel):
    enabled: bool


@app.get("/activity_log")
def activity_log_endpoint():
    """Recent backend activity lines for the desktop Activity tab."""
    from core.activity_log import as_json

    return as_json()


@app.get("/health")
def health():
    ollama = ai_engine.health_ollama()
    return {
        "status": "ok",
        "service": "jsql-backend",
        "system": _profile,
        "ollama": ollama,
        "ai_mode": ai_engine.enabled,
        "ollama_model": ai_engine.model,
    }


@app.post("/upload_csv")
async def upload_csv(file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(400, "Missing filename")
    raw = await file.read()
    if not raw:
        raise HTTPException(400, "Empty file")
    try:
        log_line(f"DuckDB: ingesting CSV {file.filename!r} ({len(raw)} bytes)…")
        result = sql_engine.register_csv(file.filename, raw)
        log_line(f"DuckDB: table {result['table']!r} registered — {result['rows']} rows")
    except Exception as e:
        log_line(f"CSV error: {e!s}")
        raise HTTPException(400, f"Could not parse CSV: {e}") from e
    return {"ok": True, **result}


@app.post("/query_sql")
def query_sql(body: QuerySqlBody):
    max_r = body.max_rows or _profile["default_max_rows"]
    preview = (body.sql or "").strip().replace("\n", " ")[:120]
    log_line(f"SQL: executing ({preview}{'…' if len((body.sql or '')) > 120 else ''})")
    out = sql_engine.execute(body.sql, max_rows=max_r)
    if out.get("error"):
        log_line(f"SQL: error — {out['error'][:200]}")
        return {"ok": False, "message": out["error"], **{k: v for k, v in out.items() if k != "error"}}
    log_line(f"SQL: OK — {out.get('row_count', 0)} row(s) returned (truncated={out.get('truncated', False)})")
    return {"ok": True, **out}


@app.post("/nl_to_sql")
def nl_to_sql(body: NlToSqlBody):
    log_line(f"AI: NL→SQL — {body.prompt[:100]!r}{'…' if len(body.prompt) > 100 else ''}")
    schema = sql_engine.schema_summary()
    gen = ai_engine.natural_language_to_sql(body.prompt, schema)
    if not gen.get("ok"):
        log_line(f"AI: NL→SQL failed — {gen.get('error', 'unknown')[:160]}")
        return {
            "ok": False,
            "sql": "",
            "message": gen.get("error", "AI error"),
            "ai_response": gen.get("response", ""),
        }
    sql = gen.get("sql") or ""
    if not sql.strip():
        log_line("AI: model returned no usable SQL")
        return {
            "ok": False,
            "sql": "",
            "message": "Model did not return usable SQL.",
            "ai_response": gen.get("response", ""),
        }
    log_line(f"AI: generated SQL ({len(sql)} chars) — running…")
    max_r = body.max_rows or _profile["default_max_rows"]
    result = sql_engine.execute(sql, max_rows=max_r)
    if result.get("error"):
        log_line(f"AI: executed SQL error — {result['error'][:160]}")
        return {
            "ok": False,
            "sql": sql,
            "message": result["error"],
            "columns": result.get("columns", []),
            "rows": result.get("rows", []),
        }
    log_line(f"AI: NL→SQL OK — {result.get('row_count', 0)} row(s)")
    return {"ok": True, "sql": sql, **result}


@app.post("/upload_text")
async def upload_text(file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(400, "Missing filename")
    raw = await file.read()
    if not raw:
        raise HTTPException(400, "Empty file")
    name = file.filename.lower()
    try:
        log_line(f"Vectors: ingesting {file.filename!r}…")
        if name.endswith(".pdf"):
            out = ingestion.ingest_pdf_bytes(file.filename, raw)
        else:
            out = ingestion.ingest_text_bytes(file.filename, raw)
        log_line(f"Vectors: indexed {out.get('chunks', 0)} chunk(s) from {file.filename!r}")
    except Exception as e:
        log_line(f"Vectors: ingest error — {e!s}")
        raise HTTPException(400, str(e)) from e
    return {"ok": True, **out}


@app.post("/convert_pdf_csv")
async def convert_pdf_csv(file: UploadFile = File(...)):
    """Extract tables (pdfplumber) or text lines (pypdf) and return a CSV file."""
    if not file.filename:
        raise HTTPException(400, "Missing filename")
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(400, "Only .pdf files are supported")
    raw = await file.read()
    if not raw:
        raise HTTPException(400, "Empty file")
    try:
        csv_bytes, mode, n = pdf_bytes_to_csv_bytes(raw)
        log_line(f"PDF→CSV: {file.filename!r} mode={mode} rows={n} bytes={len(csv_bytes)}")
    except ValueError as e:
        log_line(f"PDF→CSV failed: {e!s}")
        raise HTTPException(400, str(e)) from e
    except Exception as e:
        log_line(f"PDF→CSV error: {e!s}")
        raise HTTPException(400, f"Conversion failed: {e}") from e
    out_name = Path(file.filename).stem + ".csv"
    safe = out_name.replace('"', "")
    return Response(
        content=csv_bytes,
        media_type="text/csv; charset=utf-8",
        headers={
            "Content-Disposition": f'attachment; filename="{safe}"',
            "X-JSQL-PDF-Mode": mode,
            "X-JSQL-Row-Count": str(n),
        },
    )


@app.post("/semantic_search")
def semantic_search(body: SemanticSearchBody):
    log_line(f"Vectors: semantic search — {body.query[:80]!r}…")
    r = ingestion.search(body.query, top_k=body.top_k)
    n = len(r.get("results", []))
    log_line(f"Vectors: {n} hit(s)")
    return {"ok": True, **r}


@app.post("/ai/explain_dataset")
def ai_explain_dataset():
    log_line("AI: explain dataset (Ollama)…")
    schema = sql_engine.schema_summary()
    r = ai_engine.explain_dataset(schema)
    if not r.get("ok"):
        log_line(f"AI: explain dataset failed — {r.get('error', '')[:120]}")
        return {"ok": False, "message": r.get("error", "AI error"), "text": ""}
    log_line("AI: explain dataset done")
    return {"ok": True, "text": r.get("response", "")}


@app.post("/ai/summarize_results")
def ai_summarize_results(body: SummarizeBody):
    log_line("AI: summarize results (Ollama)…")
    r = ai_engine.summarize_results(body.columns, body.rows)
    if not r.get("ok"):
        log_line(f"AI: summarize failed — {r.get('error', '')[:120]}")
        return {"ok": False, "message": r.get("error", "AI error"), "text": ""}
    log_line("AI: summarize done")
    return {"ok": True, "text": r.get("response", "")}


@app.post("/ai/mode")
def ai_mode(body: AiModeBody):
    ai_engine.set_enabled(body.enabled)
    log_line(f"AI mode set to {'ON' if body.enabled else 'OFF'}")
    return {"ok": True, "ai_mode": ai_engine.enabled}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="127.0.0.1", port=8765, reload=False)
