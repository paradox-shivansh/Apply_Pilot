"""Offline tests: no network, no API key, no cost. Fakes replace sources and the LLM."""
import pytest
from langchain_core.runnables import RunnableLambda

from applypilot.graph import build_job_graph
from applypilot.schemas import JobAnalysis, RawJob
from applypilot.sources import html_to_text
from applypilot.store import JobStore


def make_job(i: int, title="Backend Engineer", desc="Python and SQL") -> RawJob:
    return RawJob(source="fake", source_id=str(i), url=f"https://x/{i}", title=title,
                  company="Acme", description_text=desc)


class FakeSource:
    def __init__(self, jobs, name="fake"):
        self.jobs, self.name = jobs, name
    def fetch(self):
        return self.jobs


class BrokenSource:
    name = "broken"
    def fetch(self):
        raise RuntimeError("portal down")


def fake_analyzer(calls: list):
    def _run(inputs: dict) -> JobAnalysis:
        calls.append(inputs["title"])
        return JobAnalysis(summary="s", seniority="mid", remote_type="remote",
                           employment_type="full_time", required_skills=["Python"],
                           preferred_skills=[], responsibilities=[])
    return RunnableLambda(_run)


def run(tmp_path, sources, calls, **state):
    store = JobStore(tmp_path / "jobs.json")
    graph = build_job_graph(sources, fake_analyzer(calls), store)
    return graph.invoke(state), store


def test_parallel_analysis_collects_every_job(tmp_path):
    calls = []
    out, store = run(tmp_path, [FakeSource([make_job(i) for i in range(5)])], calls)
    assert len(out["processed"]) == 5          # reducer merged 5 parallel results
    assert len(store.all()) == 5


def test_keyword_filter_saves_llm_calls(tmp_path):
    calls = []
    jobs = [make_job(1, "Python Dev"), make_job(2, "Chef", "cook food")]
    out, _ = run(tmp_path, [FakeSource(jobs)], calls, keywords=["python"])
    assert calls == ["Python Dev"]             # the chef never reached the LLM


def test_second_run_skips_seen_jobs(tmp_path):
    calls = []
    jobs = [make_job(1), make_job(2)]
    run(tmp_path, [FakeSource(jobs)], calls)
    calls.clear()
    out, _ = run(tmp_path, [FakeSource(jobs + [make_job(3)])], calls)
    assert len(calls) == 1                     # only the new job costs money


def test_broken_source_does_not_stop_the_run(tmp_path):
    calls = []
    out, _ = run(tmp_path, [BrokenSource(), FakeSource([make_job(1)])], calls)
    assert len(out["processed"]) == 1
    assert any("broken" in e for e in out["errors"])


def test_max_jobs_caps_cost(tmp_path):
    calls = []
    out, _ = run(tmp_path, [FakeSource([make_job(i) for i in range(10)])], calls, max_jobs=3)
    assert len(calls) == 3


def test_llm_failure_is_recorded_not_raised(tmp_path):
    def boom(_): raise ValueError("bad json")
    store = JobStore(tmp_path / "j.json")
    graph = build_job_graph([FakeSource([make_job(1)])], RunnableLambda(boom), store)
    out = graph.invoke({})
    assert out.get("processed", []) == [] and len(out["errors"]) == 1


def test_html_to_text_handles_escaped_html_and_scripts():
    raw = "&lt;p&gt;Build APIs&lt;/p&gt;&lt;script&gt;steal()&lt;/script&gt;&lt;ul&gt;&lt;li&gt;Python&lt;/li&gt;&lt;/ul&gt;"
    text = html_to_text(raw)
    assert "Build APIs" in text and "Python" in text and "steal" not in text


def test_job_id_is_stable():
    assert make_job(1).job_id == make_job(1).job_id != make_job(2).job_id
