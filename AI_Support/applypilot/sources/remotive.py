from ..schemas import RawJob
from .base import get_json, html_to_text


class RemotiveSource:
    """Public API: https://remotive.com/api/remote-jobs?search=<term>  (remote jobs board)"""

    def __init__(self, search: str = "", limit: int = 50):
        self.search = search
        self.limit = limit
        self.name = f"remotive:{search or 'all'}"

    def fetch(self) -> list[RawJob]:
        params = {"limit": self.limit}
        if self.search:
            params["search"] = self.search
        data = get_json("https://remotive.com/api/remote-jobs", params=params)
        return [
            RawJob(
                source="remotive",
                source_id=str(j["id"]),
                url=j.get("url", ""),
                title=j.get("title", ""),
                company=j.get("company_name", ""),
                location=j.get("candidate_required_location", "") or "Remote",
                description_text=html_to_text(j.get("description", "")),
                posted_at=j.get("publication_date"),
            )
            for j in data.get("jobs", [])
        ]
