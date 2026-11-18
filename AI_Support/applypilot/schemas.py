"""
Data models (Pydantic).

Two layers on purpose:
  RawJob       -> what a job source gave us (facts we do NOT need an LLM for)
  JobAnalysis  -> what the LLM extracts from the messy description text
  ProcessedJob -> RawJob + JobAnalysis, the object the rest of the system uses

Pydantic gives us validation, and LangChain can turn JobAnalysis into a JSON
schema that the model must follow (structured output).
"""
from __future__ import annotations

import hashlib
from typing import Literal, Optional

from pydantic import BaseModel, Field


class RawJob(BaseModel):
    source: str                       # "greenhouse", "lever", "remotive"
    source_id: str                    # id inside that source
    url: str
    title: str
    company: str
    location: str = ""
    description_text: str = ""        # already cleaned to plain text
    posted_at: Optional[str] = None

    @property
    def job_id(self) -> str:
        """Stable id used for de-duplication (same job never processed twice)."""
        key = f"{self.source}|{self.source_id}|{self.url}".lower()
        return hashlib.sha1(key.encode()).hexdigest()[:16]


class JobAnalysis(BaseModel):
    """Structured facts the LLM must extract. Field descriptions ARE the prompt:
    the model sees them as part of the JSON schema."""

    summary: str = Field(description="2 sentence plain-English summary of the role.")
    seniority: Literal["intern", "junior", "mid", "senior", "lead", "unknown"] = Field(
        description="Seniority level implied by the posting."
    )
    remote_type: Literal["remote", "hybrid", "onsite", "unknown"]
    employment_type: Literal["full_time", "part_time", "contract", "internship", "unknown"]
    required_skills: list[str] = Field(
        description="Skills/tools the posting says are REQUIRED (must have, you will need). "
        "Use short canonical names, e.g. 'Python', 'PostgreSQL'."
    )
    preferred_skills: list[str] = Field(
        description="Skills marked nice-to-have / bonus / preferred."
    )
    responsibilities: list[str] = Field(
        description="Up to 6 key responsibilities, each under 20 words."
    )
    min_years_experience: Optional[int] = Field(
        default=None, description="Minimum years of experience stated, else null."
    )
    salary_text: Optional[str] = Field(
        default=None, description="Salary exactly as written in the posting, else null."
    )
    red_flags: list[str] = Field(
        default_factory=list,
        description="Concerns visible in the text: vague pay, unrealistic requirements, "
        "'rockstar/ninja', 'fast-paced' with no detail, etc. Empty if none.",
    )


class ProcessedJob(BaseModel):
    raw: RawJob
    analysis: JobAnalysis

    @property
    def job_id(self) -> str:
        return self.raw.job_id
