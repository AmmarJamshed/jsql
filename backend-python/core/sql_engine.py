"""DuckDB session: CSV registration, queries, schema introspection."""

from __future__ import annotations

import io
import re
import uuid
from typing import Any

import duckdb
import pandas as pd


def path_stem(filename: str) -> str:
    name = filename.rsplit("/", 1)[-1].rsplit("\\", 1)[-1]
    return name.rsplit(".", 1)[0] if "." in name else name


def _sanitize_table_name(name: str) -> str:
    base = re.sub(r"[^a-zA-Z0-9_]", "_", name.strip())
    if not base or base[0].isdigit():
        base = "t_" + base
    return base[:63] or "table_" + uuid.uuid4().hex[:8]


class SqlEngine:
    def __init__(self) -> None:
        self._conn = duckdb.connect(database=":memory:")
        self._tables: dict[str, str] = {}  # logical_name -> duckdb identifier

    def register_csv(self, filename: str, raw_bytes: bytes) -> dict[str, Any]:
        logical = _sanitize_table_name(path_stem(filename))
        if logical in self._tables:
            logical = f"{logical}_{uuid.uuid4().hex[:6]}"
        ident = logical
        buf = io.BytesIO(raw_bytes)
        df = pd.read_csv(buf)
        self._conn.register(ident, df)
        self._tables[logical] = ident
        preview = df.head(20).to_dict(orient="records")
        columns = [{"name": c, "type": str(df[c].dtype)} for c in df.columns]
        return {
            "table": logical,
            "rows": int(len(df)),
            "columns": columns,
            "preview": preview,
        }

    def execute(self, sql: str, max_rows: int = 10_000) -> dict[str, Any]:
        sql_stripped = sql.strip().rstrip(";")
        if not sql_stripped:
            return {"columns": [], "rows": [], "truncated": False, "error": "Empty query"}
        try:
            result = self._conn.execute(sql_stripped)
            df = result.fetchdf()
            truncated = len(df) > max_rows
            if truncated:
                df = df.head(max_rows)
            cols = list(df.columns)
            records = df.to_dict(orient="records")
            return {
                "columns": cols,
                "rows": records,
                "truncated": truncated,
                "row_count": len(records),
            }
        except Exception as e:
            return {
                "columns": [],
                "rows": [],
                "truncated": False,
                "error": str(e),
            }

    def schema_summary(self) -> str:
        if not self._tables:
            return "No tables loaded."
        parts: list[str] = []
        for logical, ident in self._tables.items():
            try:
                df = self._conn.execute(f"SELECT * FROM {ident} LIMIT 0").fetchdf()
                cols = ", ".join(df.columns.tolist())
                cnt = self._conn.execute(f"SELECT COUNT(*) FROM {ident}").fetchone()[0]
                parts.append(f"Table `{logical}` ({cnt} rows): columns: {cols}")
            except Exception as e:
                parts.append(f"Table `{logical}`: (could not describe: {e})")
        return "\n".join(parts)
