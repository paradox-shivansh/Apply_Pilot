"""
Tiny JSON-file store so the pipeline remembers which jobs it has already
analyzed. This is what makes the system cheap to re-run: a job seen yesterday
is skipped today, so the LLM is never paid for twice.

(Later this can be swapped for SQLite or the extension's IndexedDB. The graph
only uses has() and save_many(), so nothing else has to change.)
"""
from __future__ import annotations

import json
from pathlib import Path

from .schemas import ProcessedJob


class JobStore:
    def __init__(self, path: str | Path = "data/jobs.json"):
        self.path = Path(path)
        self._jobs: dict[str, dict] = {}
        if self.path.exists():
            self._jobs = json.loads(self.path.read_text())

    def has(self, job_id: str) -> bool:
        return job_id in self._jobs

    def save_many(self, jobs: list[ProcessedJob]) -> None:
        for job in jobs:
            self._jobs[job.job_id] = job.model_dump()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self._jobs, indent=2))

    def all(self) -> list[ProcessedJob]:
        return [ProcessedJob.model_validate(v) for v in self._jobs.values()]
