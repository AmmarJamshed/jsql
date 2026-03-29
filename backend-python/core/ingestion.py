"""Text/PDF chunking, embeddings, FAISS index (persisted under data dir)."""

from __future__ import annotations

import io
import json
import re
import uuid
from pathlib import Path
from typing import Any

import faiss
import numpy as np
from pypdf import PdfReader
from sentence_transformers import SentenceTransformer

CHUNK_SIZE = 500
CHUNK_OVERLAP = 80


def _chunk_text(text: str, size: int = CHUNK_SIZE, overlap: int = CHUNK_OVERLAP) -> list[str]:
    text = re.sub(r"\s+", " ", text).strip()
    if not text:
        return []
    chunks: list[str] = []
    i = 0
    while i < len(text):
        chunks.append(text[i : i + size])
        i += size - overlap
    return [c for c in chunks if c.strip()]


class IngestionStore:
    def __init__(self, data_dir: Path) -> None:
        self.data_dir = data_dir
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self._meta_path = self.data_dir / "chunks_meta.json"
        self._index_path = self.data_dir / "faiss.index"
        self._model: SentenceTransformer | None = None
        self._dim: int | None = None
        self._index: faiss.IndexFlatIP | None = None
        self._chunks: list[dict[str, Any]] = []
        self._load()

    def _get_model(self) -> SentenceTransformer:
        if self._model is None:
            self._model = SentenceTransformer("all-MiniLM-L6-v2")
            self._dim = self._model.get_sentence_embedding_dimension()
        return self._model

    def _load(self) -> None:
        if self._meta_path.exists():
            try:
                self._chunks = json.loads(self._meta_path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                self._chunks = []
        if not self._chunks:
            self._index = None
            return
        if self._index_path.exists():
            try:
                self._get_model()
                self._index = faiss.read_index(str(self._index_path))
            except Exception:
                self._rebuild_index_from_meta()
        else:
            self._rebuild_index_from_meta()

    def _rebuild_index_from_meta(self) -> None:
        self._model = None
        self._index = None
        self._dim = None
        if not self._chunks:
            return
        model = self._get_model()
        dim = model.get_sentence_embedding_dimension()
        self._index = faiss.IndexFlatIP(dim)
        for ch in self._chunks:
            vec = np.array(ch["embedding"], dtype="float32").reshape(1, -1)
            faiss.normalize_L2(vec)
            self._index.add(vec)
        self._persist()

    def _persist(self) -> None:
        self._meta_path.write_text(json.dumps(self._chunks, ensure_ascii=False, indent=0), encoding="utf-8")
        if self._index is not None:
            faiss.write_index(self._index, str(self._index_path))

    def _ensure_index(self, dim: int) -> faiss.IndexFlatIP:
        if self._index is None:
            self._dim = dim
            self._index = faiss.IndexFlatIP(dim)
        elif self._dim != dim:
            self._rebuild_index_from_meta()
        return self._index  # type: ignore[return-value]

    def ingest_text_bytes(self, filename: str, raw: bytes) -> dict[str, Any]:
        text = raw.decode("utf-8", errors="replace")
        return self._ingest_text(filename, text)

    def ingest_pdf_bytes(self, filename: str, raw: bytes) -> dict[str, Any]:
        reader = PdfReader(io.BytesIO(raw))
        parts: list[str] = []
        for page in reader.pages:
            parts.append(page.extract_text() or "")
        text = "\n".join(parts)
        return self._ingest_text(filename, text)

    def _ingest_text(self, source: str, text: str) -> dict[str, Any]:
        pieces = _chunk_text(text)
        if not pieces:
            return {"chunks": 0, "message": "No text extracted", "source": source}
        model = self._get_model()
        dim = model.get_sentence_embedding_dimension()
        embs = model.encode(pieces, convert_to_numpy=True, show_progress_bar=False)
        embs = embs.astype("float32")
        faiss.normalize_L2(embs)
        index = self._ensure_index(dim)
        for chunk, vec in zip(pieces, embs):
            cid = str(uuid.uuid4())
            rec = {
                "id": cid,
                "source": source,
                "text": chunk,
                "embedding": vec.tolist(),
            }
            self._chunks.append(rec)
            index.add(vec.reshape(1, -1))
        self._persist()
        return {"chunks": len(pieces), "source": source, "total_stored": len(self._chunks)}

    def search(self, query: str, top_k: int = 8) -> dict[str, Any]:
        if not self._chunks or self._index is None:
            return {"results": [], "message": "No documents indexed yet."}
        model = self._get_model()
        q = model.encode([query], convert_to_numpy=True, show_progress_bar=False).astype("float32")
        faiss.normalize_L2(q)
        k = min(top_k, self._index.ntotal)
        if k <= 0:
            return {"results": [], "message": "Empty index."}
        scores, idxs = self._index.search(q, k)
        out: list[dict[str, Any]] = []
        for score, i in zip(scores[0], idxs[0]):
            if i < 0 or i >= len(self._chunks):
                continue
            ch = self._chunks[i]
            out.append(
                {
                    "score": float(score),
                    "source": ch["source"],
                    "text": ch["text"],
                    "id": ch["id"],
                }
            )
        return {"results": out}
