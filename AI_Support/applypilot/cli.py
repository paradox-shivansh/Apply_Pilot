"""Command line entry point:  python -m applypilot.cli --greenhouse stripe --keywords python,backend"""
import argparse

from .config import get_llm
from .graph import build_job_graph, make_analyzer
from .sources import GreenhouseSource, LeverSource, RemotiveSource
from .store import JobStore


def main() -> None:
    p = argparse.ArgumentParser(description="ApplyPilot Step 1: read job openings")
    p.add_argument("--greenhouse", nargs="*", default=[], help="Greenhouse board slugs")
    p.add_argument("--lever", nargs="*", default=[], help="Lever company slugs")
    p.add_argument("--remotive", nargs="*", default=[], help="Remotive search terms")
    p.add_argument("--keywords", default="", help="comma-separated title/description filter")
    p.add_argument("--max-jobs", type=int, default=10, help="cap LLM calls per run")
    p.add_argument("--model", default=None, help="e.g. openai:gpt-4o-mini")
    args = p.parse_args()

    sources = (
        [GreenhouseSource(b) for b in args.greenhouse]
        + [LeverSource(c) for c in args.lever]
        + [RemotiveSource(s) for s in args.remotive]
    )
    if not sources:
        p.error("give at least one of --greenhouse / --lever / --remotive")

    store = JobStore()
    graph = build_job_graph(sources, make_analyzer(get_llm(args.model)), store)
    result = graph.invoke(
        {"keywords": [k.strip() for k in args.keywords.split(",") if k.strip()], "max_jobs": args.max_jobs}
    )

    for job in result.get("processed", []):
        a = job.analysis
        print(f"\n{job.raw.title}  |  {job.raw.company}  |  {a.seniority}, {a.remote_type}")
        print(f"  required: {', '.join(a.required_skills)}")
        print(f"  preferred: {', '.join(a.preferred_skills) or '-'}")
        if a.red_flags:
            print(f"  red flags: {'; '.join(a.red_flags)}")
    print(f"\nAnalyzed {len(result.get('processed', []))} new jobs. Errors: {len(result.get('errors', []))}")
    for e in result.get("errors", []):
        print("  !", e)


if __name__ == "__main__":
    main()
