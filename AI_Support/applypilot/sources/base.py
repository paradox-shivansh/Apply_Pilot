"""
Job sources.

A "source" is anything with a .fetch() that returns list[RawJob]. We use a
Protocol (structural typing): any class with the right method qualifies, so
adding a new portal later = adding one small file, no changes to the graph.

IMPORTANT DESIGN CHOICE: we read official public job-board APIs (Greenhouse,
Lever, Remotive), NOT scraped HTML from LinkedIn/Indeed. APIs return clean JSON,
are allowed, and don't break when a site redesigns.
"""
from __future__ import annotations

import html
from typing import Protocol

import httpx
from bs4 import BeautifulSoup

from ..schemas import RawJob

HTTP_TIMEOUT = 20.0
USER_AGENT = "ApplyPilot/0.1 (personal job-search assistant)"


class JobSource(Protocol):
    name: str

    def fetch(self) -> list[RawJob]: ...


def html_to_text(raw: str) -> str:
    """Turn HTML (sometimes entity-escaped, as Greenhouse sends it) into plain text.

    Why: LLM tokens cost money and HTML tags are pure noise. We also collapse
    whitespace so the model sees clean paragraphs.
    """
    if not raw:
        return ""
    text = html.unescape(raw)                      # &lt;p&gt; -> <p>
    soup = BeautifulSoup(text, "html.parser")
    for tag in soup(["script", "style"]):          # never keep executable junk
        tag.decompose()
    for br in soup.find_all(["br", "li", "p", "h1", "h2", "h3", "h4"]):
        br.append("\n")                            # keep line structure
    lines = [ln.strip() for ln in soup.get_text().splitlines()]
    return "\n".join(ln for ln in lines if ln)


def get_json(url: str, params: dict | None = None):
    """One shared HTTP helper so timeouts/headers are consistent."""
    with httpx.Client(timeout=HTTP_TIMEOUT, headers={"User-Agent": USER_AGENT}) as client:
        resp = client.get(url, params=params)
        resp.raise_for_status()
        return resp.json()
