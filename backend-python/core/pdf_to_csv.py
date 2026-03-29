"""Extract PDF tables (pdfplumber) or plain text lines (pypdf) → CSV bytes."""

from __future__ import annotations

import csv
import io

from pypdf import PdfReader


def pdf_bytes_to_csv_bytes(raw: bytes) -> tuple[bytes, str, int]:
    """
    Returns (utf-8 csv bytes, mode description, data row count excluding header for text mode).
    mode: 'tables' | 'text_lines'
    """
    table_rows: list[list[str]] = []

    try:
        import pdfplumber

        with pdfplumber.open(io.BytesIO(raw)) as pdf:
            for page in pdf.pages:
                for table in page.extract_tables() or []:
                    for row in table:
                        if not row:
                            continue
                        table_rows.append(
                            [
                                ("" if c is None else str(c).replace("\n", " ").replace("\r", " ").strip())
                                for c in row
                            ]
                        )
    except Exception:
        table_rows = []

    if table_rows:
        buf = io.StringIO()
        writer = csv.writer(buf)
        for row in table_rows:
            writer.writerow(row)
        data = buf.getvalue().encode("utf-8")
        return data, "tables", len(table_rows)

    reader = PdfReader(io.BytesIO(raw))
    lines: list[list[str]] = []
    for page in reader.pages:
        text = page.extract_text() or ""
        for line in text.splitlines():
            s = line.strip()
            if s:
                lines.append([s])

    if not lines:
        raise ValueError("Could not extract tables or text from this PDF.")

    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(["text"])
    writer.writerows(lines)
    return buf.getvalue().encode("utf-8"), "text_lines", len(lines)
