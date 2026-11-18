from ..schemas import RawJob
from .base import get_json, html_to_text


class GreenhouseSource:
    """Public API: https://boards-api.greenhouse.io/v1/boards/<board>/jobs?content=true
    <board> is the company slug in boards.greenhouse.io/<board>."""

    def __init__(self, board: str):
        self.board = board
        self.name = f"greenhouse:{board}"

    def fetch(self) -> list[RawJob]:
        data = get_json(
            f"https://boards-api.greenhouse.io/v1/boards/{self.board}/jobs",
            params={"content": "true"},
        )
        return [
            RawJob(
                source="greenhouse",
                source_id=str(j["id"]),
                url=j.get("absolute_url", ""),
                title=j.get("title", ""),
                company=self.board,
                location=(j.get("location") or {}).get("name", ""),
                description_text=html_to_text(j.get("content", "")),
                posted_at=j.get("updated_at"),
            )
            for j in data.get("jobs", [])
        ]
