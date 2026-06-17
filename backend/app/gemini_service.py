"""Google Gemini integration for conversational media search queries."""

import json
import logging
import re

import requests

from app.config import settings

logger = logging.getLogger(__name__)


class GeminiMediaService:
    """Translates natural-language search queries into structured media hints."""

    GEMINI_URL = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        "gemini-1.5-flash:generateContent"
    )

    def translate_natural_search(self, query: str) -> dict:
        api_key = settings.gemini_api_key
        if not api_key:
            return {"probable_title": "", "keywords": ""}

        prompt = (
            "You are a media library search assistant. Given a conversational TV or movie "
            "search query, extract the most probable exact title and comma-separated search "
            "keywords. Respond ONLY with valid JSON in this shape: "
            '{"probable_title": "...", "keywords": "word1, word2, ..."}\n'
            f"Query: {query}"
        )

        try:
            response = requests.post(
                f"{self.GEMINI_URL}?key={api_key}",
                json={
                    "contents": [{"parts": [{"text": prompt}]}],
                    "generationConfig": {"temperature": 0.2, "maxOutputTokens": 256},
                },
                timeout=settings.http_timeout,
            )
            response.raise_for_status()
            data = response.json()
            text = (
                data.get("candidates", [{}])[0]
                .get("content", {})
                .get("parts", [{}])[0]
                .get("text", "")
            )
            return self._parse_response(text)
        except Exception as exc:
            logger.warning("Gemini natural search failed: %s", exc)
            return {"probable_title": "", "keywords": ""}

    def _parse_response(self, text: str) -> dict:
        empty = {"probable_title": "", "keywords": ""}
        if not text:
            return empty

        cleaned = text.strip()
        fence_match = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", cleaned, re.DOTALL)
        if fence_match:
            cleaned = fence_match.group(1)

        try:
            payload = json.loads(cleaned)
        except json.JSONDecodeError:
            brace_match = re.search(r"\{.*\}", cleaned, re.DOTALL)
            if not brace_match:
                return empty
            try:
                payload = json.loads(brace_match.group(0))
            except json.JSONDecodeError:
                return empty

        return {
            "probable_title": str(payload.get("probable_title", "")).strip(),
            "keywords": str(payload.get("keywords", "")).strip(),
        }
