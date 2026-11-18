from ..schemas import RawJob
from .base import get_json


class LeverSource:
    """Public API: https://api.lever.co/v0/postings/<company>?mode=json"""

    def __init__(self, company: str):
        self.company = company
        self.name = f"lever:{company}"

    def fetch(self) -> list[RawJob]:
        data = get_json(f"https://api.lever.co/v0/postings/{self.company}", params={"mode": "json"})
        return [
            RawJob(
                source="lever",
                source_id=j["id"],
                url=j.get("hostedUrl", ""),
                title=j.get("text", ""),
                company=self.company,
                location=(j.get("categories") or {}).get("location", "") or "",
                description_text=j.get("descriptionPlain", ""),
                posted_at=str(j.get("createdAt", "")) or None,
            )
            for j in data
        ]
