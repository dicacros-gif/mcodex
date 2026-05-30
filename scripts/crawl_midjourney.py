from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import parse_qsl, unquote, urlencode, urlparse, urlunparse


ROOT = Path(__file__).resolve().parents[1]
DOCS_DIR = ROOT / "docs"
DATA_PATH = DOCS_DIR / "data" / "midjourney.json"
MEDIA_ROOT = DOCS_DIR / "media"

SOURCE_TABS = [
    {
        "key": "images",
        "label": "Images Top",
        "url": "https://www.midjourney.com/explore?tab=top",
        "media_type": "image",
    },
    {
        "key": "videos",
        "label": "Videos",
        "url": "https://www.midjourney.com/explore?tab=videos",
        "media_type": "video",
    },
    {
        "key": "styles",
        "label": "Styles",
        "url": "https://www.midjourney.com/explore?tab=styles_random",
        "media_type": "style",
    },
]

UUID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    re.IGNORECASE,
)
HEX32_RE = re.compile(r"(?<![0-9a-f])([0-9a-f]{32})(?![0-9a-f])", re.IGNORECASE)
VIDEO_URL_RE = re.compile(r"\.(?:mp4|webm|mov)(?:\?|#|$)", re.IGNORECASE)
IMAGE_URL_RE = re.compile(r"\.(?:png|jpe?g|webp|gif)(?:\?|#|$)", re.IGNORECASE)

EXTRACT_SCRIPT = r"""
({ expectedType, limit }) => {
  const results = [];
  const seen = new Set();

  function cleanUrl(value) {
    if (!value) return null;
    let url = String(value)
      .replaceAll("&amp;", "&")
      .replaceAll("&quot;", "\"")
      .replaceAll("&#34;", "\"")
      .replaceAll("\\/", "/")
      .trim()
      .replace(/["')\],;}]+$/g, "");
    try {
      url = new URL(url, location.href).href;
      const parsed = new URL(url);
      const nested = parsed.searchParams.get("url");
      if (nested) {
        url = new URL(nested, location.href).href;
      }
    } catch {
      return null;
    }
    return url;
  }

  function bestFromSrcset(srcset) {
    if (!srcset) return null;
    const candidates = srcset.split(",")
      .map((part) => {
        const [url, size = ""] = part.trim().split(/\s+/);
        const width = Number((size.match(/(\d+)w/) || [0, 0])[1]);
        return { url, width };
      })
      .filter((candidate) => candidate.url);
    candidates.sort((a, b) => a.width - b.width);
    return candidates.length ? candidates[candidates.length - 1].url : null;
  }

  function inferType(url, fallback) {
    const lower = String(url || "").toLowerCase();
    if (/\.(mp4|webm|mov)(\?|#|$)/.test(lower)) return "video";
    if (fallback === "video") return "video";
    return "image";
  }

  function likelyContent(url) {
    if (!url || !/^https?:\/\//i.test(url)) return false;
    const lower = url.toLowerCase();
    if (lower.startsWith("data:") || lower.startsWith("blob:")) return false;
    if (/\.(svg|ico)(\?|#|$)/.test(lower)) return false;
    if (/(logo|favicon|apple-touch-icon|sprite|manifest|font)/.test(lower)) return false;
    return /(\.png|\.jpg|\.jpeg|\.webp|\.gif|\.mp4|\.webm|\.mov)(\?|#|$)/i.test(lower)
      || /cdn\.midjourney\.com\/[0-9a-f-]{24,}/i.test(lower)
      || /(mj-gallery|discordapp|discordcdn|r2\.dev)/i.test(lower);
  }

  function dimensions(node) {
    const rect = node.getBoundingClientRect ? node.getBoundingClientRect() : { width: 0, height: 0 };
    return {
      width: Math.round(node.naturalWidth || node.videoWidth || rect.width || 0),
      height: Math.round(node.naturalHeight || node.videoHeight || rect.height || 0)
    };
  }

  function cardFor(node) {
    return node.closest("a[href], article, li, [role='listitem'], [role='link'], [data-testid]")
      || node.parentElement?.closest("div")
      || node.parentElement;
  }

  function add(item) {
    const mediaUrl = cleanUrl(item.mediaUrl || item.thumbnailUrl);
    const thumbnailUrl = cleanUrl(item.thumbnailUrl || item.mediaUrl);
    const key = `${item.mediaType || ""}|${mediaUrl || ""}|${thumbnailUrl || ""}|${item.pageUrl || ""}`;
    if (!mediaUrl || seen.has(key) || !likelyContent(mediaUrl)) return;
    seen.add(key);
    const text = String(item.text || "").replace(/\s+/g, " ").trim();
    const styleMatch = text.match(/--sref\s+([0-9]+)/i) || text.match(/\bsref\s*[:#]?\s*([0-9]+)/i);
    results.push({
      mediaType: item.mediaType || inferType(mediaUrl, expectedType),
      mediaUrl,
      thumbnailUrl,
      pageUrl: item.pageUrl || location.href,
      prompt: text.slice(0, 1000),
      title: String(item.title || "").trim().slice(0, 240),
      styleCode: styleMatch ? styleMatch[1] : null,
      width: item.width || 0,
      height: item.height || 0
    });
  }

  const nodes = [...document.querySelectorAll("video, video source, img, picture source")];
  for (const node of nodes) {
    const tag = node.tagName.toLowerCase();
    let src = null;
    let fallback = expectedType;

    if (tag === "img") {
      src = node.currentSrc || node.src || bestFromSrcset(node.getAttribute("srcset"));
      fallback = "image";
    } else if (tag === "video") {
      src = node.currentSrc || node.src || node.poster || node.querySelector("source")?.src;
      fallback = src && /\.(mp4|webm|mov)(\?|#|$)/i.test(src) ? "video" : "image";
    } else if (tag === "source") {
      const parent = node.closest("video, picture");
      src = node.src || bestFromSrcset(node.getAttribute("srcset"));
      fallback = parent?.tagName?.toLowerCase() === "video" ? "video" : "image";
    }

    src = cleanUrl(src);
    if (!likelyContent(src)) continue;

    const size = dimensions(node);
    if ((size.width && size.width < 96) || (size.height && size.height < 96)) continue;

    const card = cardFor(node);
    const anchor = node.closest("a[href]") || card?.querySelector?.("a[href]");
    const pageUrl = anchor?.href ? cleanUrl(anchor.href) : location.href;
    const text = card?.innerText || node.alt || node.getAttribute("aria-label") || "";
    const mediaType = inferType(src, fallback);

    add({
      mediaType,
      mediaUrl: src,
      thumbnailUrl: tag === "video" ? cleanUrl(node.poster) || src : src,
      pageUrl,
      prompt: text,
      title: node.alt || anchor?.getAttribute("aria-label") || document.title,
      width: size.width,
      height: size.height
    });
  }

  const html = document.documentElement.innerHTML;
  const rawUrls = html.match(/https?:\\?\/\\?\/[^"'<>\\\s]+/g) || [];
  for (const raw of rawUrls) {
    let url = raw.replaceAll("\\/", "/").replaceAll("&amp;", "&");
    try {
      url = decodeURIComponent(url);
    } catch {
      // Keep the original URL if it is not percent-encoded.
    }
    url = cleanUrl(url);
    if (!likelyContent(url)) continue;
    add({
      mediaType: inferType(url, expectedType),
      mediaUrl: url,
      thumbnailUrl: url,
      pageUrl: location.href,
      prompt: "",
      title: document.title
    });
  }

  const preferred = results.filter((item) => {
    if (expectedType === "video") return item.mediaType === "video";
    if (expectedType === "style") return item.mediaType === "image";
    return item.mediaType === expectedType;
  });
  const fallback = results.filter((item) => !preferred.includes(item));
  return [...preferred, ...fallback].slice(0, limit);
}
"""


class CrawlBlocked(RuntimeError):
    pass


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    return value.strip().lower() not in {"0", "false", "no", "off"}


def env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    try:
        return int(value)
    except ValueError:
        return default


def load_archive() -> dict[str, Any]:
    if not DATA_PATH.exists():
        return {
            "version": 1,
            "updated_at": None,
            "source_tabs": [
                {"key": tab["key"], "label": tab["label"], "url": tab["url"]}
                for tab in SOURCE_TABS
            ],
            "counts": {"images": 0, "videos": 0, "styles": 0, "total": 0},
            "items": [],
        }
    return json.loads(DATA_PATH.read_text(encoding="utf-8"))


def write_archive(archive: dict[str, Any]) -> None:
    DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
    DATA_PATH.write_text(
        json.dumps(archive, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def update_counts(archive: dict[str, Any]) -> None:
    counts = {tab["key"]: 0 for tab in SOURCE_TABS}
    for item in archive.get("items", []):
        if item.get("tab") in counts:
            counts[item["tab"]] += 1
    counts["total"] = sum(counts.values())
    archive["counts"] = counts


def canonical_url(value: str | None) -> str:
    if not value:
        return ""
    value = html.unescape(value.strip()).rstrip("\"')],;}")
    parsed = urlparse(value)
    path = re.sub(
        r"_(\d+)_N(\.(?:webp|jpe?g|png|gif))$",
        r"_N\2",
        parsed.path,
        flags=re.IGNORECASE,
    )
    drop_params = {"w", "h", "width", "height", "q", "quality", "fit", "fm", "format", "auto"}
    query = [
        (key, val)
        for key, val in parse_qsl(parsed.query, keep_blank_values=True)
        if key.lower() not in drop_params
    ]
    return urlunparse(
        (
            parsed.scheme.lower(),
            parsed.netloc.lower(),
            path,
            parsed.params,
            urlencode(query, doseq=True),
            "",
        )
    )


def public_id_from(*values: str | None) -> str | None:
    joined = " ".join(value or "" for value in values)
    match = UUID_RE.search(joined)
    if match:
        return match.group(0).lower()
    match = HEX32_RE.search(joined)
    if match:
        return match.group(1).lower()
    return None


def item_id(tab_key: str, item: dict[str, Any]) -> str:
    media = canonical_url(item.get("media_url"))
    page = canonical_url(item.get("page_url"))
    source = "|".join(
        [
            tab_key,
            item.get("media_type") or "",
            media,
            page,
            item.get("style_code") or "",
        ]
    )
    digest = hashlib.sha256(source.encode("utf-8")).hexdigest()
    public_id = public_id_from(media, page)
    if public_id:
        return f"{tab_key}-{public_id}-{digest[:8]}"
    return f"{tab_key}-{digest[:16]}"


def extension_from(url: str, content_type: str | None, media_type: str) -> str:
    path = unquote(urlparse(url).path).lower()
    for ext in (".mp4", ".webm", ".mov", ".jpg", ".jpeg", ".png", ".webp", ".gif"):
        if path.endswith(ext):
            return ".jpg" if ext == ".jpeg" else ext
    content_type = (content_type or "").split(";")[0].strip().lower()
    mapping = {
        "image/jpeg": ".jpg",
        "image/png": ".png",
        "image/webp": ".webp",
        "image/gif": ".gif",
        "video/mp4": ".mp4",
        "video/webm": ".webm",
        "video/quicktime": ".mov",
    }
    if content_type in mapping:
        return mapping[content_type]
    return ".mp4" if media_type == "video" else ".jpg"


def relative_to_docs(path: Path) -> str:
    return path.relative_to(DOCS_DIR).as_posix()


def parse_cookie_secret(raw: str) -> list[dict[str, Any]]:
    raw = raw.strip()
    if not raw:
        return []
    if raw.startswith("{") or raw.startswith("["):
        payload = json.loads(raw)
        cookies = payload.get("cookies", []) if isinstance(payload, dict) else payload
        return [normalize_cookie(cookie) for cookie in cookies if cookie.get("name")]

    cookies = []
    for pair in raw.split(";"):
        if "=" not in pair:
            continue
        name, value = pair.split("=", 1)
        name = name.strip()
        if not name:
            continue
        cookies.append(
            {
                "name": name,
                "value": value.strip(),
                "url": "https://www.midjourney.com",
            }
        )
    return cookies


def normalize_cookie(cookie: dict[str, Any]) -> dict[str, Any]:
    allowed = {"name", "value", "domain", "path", "expires", "httpOnly", "secure", "sameSite", "url"}
    normalized = {key: cookie[key] for key in allowed if key in cookie}
    if "url" not in normalized and "domain" not in normalized:
        normalized["url"] = "https://www.midjourney.com"
    normalized.setdefault("path", "/")
    if "sameSite" in normalized:
        same_site = str(normalized["sameSite"]).lower()
        normalized["sameSite"] = {"lax": "Lax", "strict": "Strict", "none": "None"}.get(
            same_site,
            "Lax",
        )
    return normalized


def prepare_storage_state() -> Path | None:
    state_json = os.getenv("MIDJOURNEY_STORAGE_STATE_JSON", "").strip()
    if state_json:
        path = ROOT / ".midjourney-storage-state.json"
        path.write_text(state_json, encoding="utf-8")
        return path

    state_path = os.getenv("MIDJOURNEY_STORAGE_STATE", "").strip()
    if not state_path:
        return None

    candidate = Path(state_path)
    if candidate.exists():
        return candidate

    if state_path.startswith("{"):
        path = ROOT / ".midjourney-storage-state.json"
        path.write_text(state_path, encoding="utf-8")
        return path

    return None


def detect_blocked(page: Any) -> None:
    try:
        title = page.title()
    except Exception:
        title = ""
    try:
        body = page.locator("body").inner_text(timeout=5000)
    except Exception:
        body = ""
    combined = f"{title}\n{body}\n{page.url}".lower()
    markers = [
        "just a moment",
        "enable javascript and cookies to continue",
        "cf_chl",
        "cdn-cgi/challenge-platform",
    ]
    if any(marker in combined for marker in markers):
        raise CrawlBlocked(
            "Midjourney returned a Cloudflare or login challenge. "
            "Add MIDJOURNEY_COOKIES or MIDJOURNEY_STORAGE_STATE_JSON as a GitHub secret and rerun."
        )


def normalize_item(tab: dict[str, str], raw: dict[str, Any], captured_at: str) -> dict[str, Any]:
    media_url = raw.get("mediaUrl") or raw.get("media_url") or raw.get("thumbnailUrl")
    thumbnail_url = raw.get("thumbnailUrl") or raw.get("thumbnail_url") or media_url
    media_type = raw.get("mediaType") or raw.get("media_type") or tab["media_type"]
    video_url = raw.get("videoUrl") or raw.get("video_url")
    if tab["media_type"] == "style":
        media_type = "image"
    if tab["key"] == "videos":
        if media_url and VIDEO_URL_RE.search(media_url):
            video_url = video_url or media_url
        media_type = "image"
        if thumbnail_url and not VIDEO_URL_RE.search(thumbnail_url):
            media_url = thumbnail_url
        else:
            media_url = None
    item = {
        "id": "",
        "tab": tab["key"],
        "tab_label": tab["label"],
        "media_type": media_type,
        "page_url": raw.get("pageUrl") or tab["url"],
        "media_url": media_url,
        "thumbnail_url": thumbnail_url,
        "asset_path": None,
        "prompt": raw.get("prompt") or "",
        "title": raw.get("title") or "",
        "style_code": raw.get("styleCode") or raw.get("style_code"),
        "width": raw.get("width") or 0,
        "height": raw.get("height") or 0,
        "captured_at": captured_at,
        "source_tab_url": tab["url"],
    }
    if video_url:
        item["video_url"] = video_url
    item["id"] = item_id(tab["key"], item)
    return item


def prompt_text(prompt: Any) -> str:
    if isinstance(prompt, str):
        return prompt
    if not isinstance(prompt, dict):
        return ""
    decoded = prompt.get("decodedPrompt")
    if isinstance(decoded, list):
        parts = [
            str(part.get("content", "")).strip()
            for part in decoded
            if isinstance(part, dict) and part.get("content")
        ]
        if parts:
            return " ".join(parts)
    return str(prompt.get("text") or prompt.get("full_command") or "").strip()


def collect_media_urls(payload: Any) -> list[str]:
    found: list[str] = []

    def walk(value: Any) -> None:
        if isinstance(value, str):
            if re.search(r"https?://", value) and re.search(
                r"\.(?:mp4|webm|mov|png|jpe?g|webp|gif)(?:\?|#|$)",
                value,
                re.IGNORECASE,
            ):
                found.append(html.unescape(value))
            return
        if isinstance(value, list):
            for child in value:
                walk(child)
            return
        if isinstance(value, dict):
            for child in value.values():
                walk(child)

    walk(payload)
    deduped = []
    seen = set()
    for url in found:
        key = canonical_url(url)
        if key in seen:
            continue
        seen.add(key)
        deduped.append(url)
    return deduped


def api_items_from_payload(tab: dict[str, str], payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        jobs = payload.get("items") or payload.get("jobs") or payload.get("data") or []
    else:
        jobs = payload
    if not isinstance(jobs, list):
        return []

    items: list[dict[str, Any]] = []
    for job in jobs:
        if not isinstance(job, dict):
            continue
        job_id = job.get("id")
        if not job_id:
            continue

        text = prompt_text(job.get("prompt"))
        title = job.get("display_name") or job.get("username_v2") or job.get("type") or ""
        page_url = f"https://www.midjourney.com/jobs/{job_id}"
        width = job.get("width") or 0
        height = job.get("height") or 0
        urls = collect_media_urls(job)
        video_urls = [url for url in urls if VIDEO_URL_RE.search(url)]
        image_urls = [url for url in urls if IMAGE_URL_RE.search(url)]

        source_items = job.get("items") if isinstance(job.get("items"), list) else []
        visible_indexes = [
            index
            for index, source_item in enumerate(source_items)
            if isinstance(source_item, dict) and not source_item.get("server_filtered")
        ]
        if not visible_indexes:
            visible_indexes = [0]
        generated_image = f"https://cdn.midjourney.com/{job_id}/0_{visible_indexes[0]}_384_N.webp"
        thumbnail_url = image_urls[0] if image_urls else generated_image

        if tab["key"] == "videos":
            items.append(
                {
                    "mediaType": "image",
                    "mediaUrl": thumbnail_url,
                    "thumbnailUrl": thumbnail_url,
                    "videoUrl": video_urls[0] if video_urls else None,
                    "pageUrl": page_url,
                    "prompt": text,
                    "title": title,
                    "width": width,
                    "height": height,
                }
            )
            continue

        if video_urls:
            for video_url in video_urls:
                items.append(
                    {
                        "mediaType": "video",
                        "mediaUrl": video_url,
                        "thumbnailUrl": thumbnail_url,
                        "pageUrl": page_url,
                        "prompt": text,
                        "title": title,
                        "width": width,
                        "height": height,
                    }
                )
            continue

        if tab["media_type"] in {"image", "style", "video"}:
            items.append(
                {
                    "mediaType": "image",
                    "mediaUrl": thumbnail_url,
                    "thumbnailUrl": thumbnail_url,
                    "pageUrl": page_url,
                    "prompt": text,
                    "title": title,
                    "styleCode": None,
                    "width": width,
                    "height": height,
                }
            )
    return items


def style_items_from_payload(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        styles = payload.get("items") or payload.get("styles") or payload.get("data") or []
    else:
        styles = payload
    if not isinstance(styles, list):
        return []

    items: list[dict[str, Any]] = []
    for style in styles:
        if not isinstance(style, dict):
            continue
        style_id = style.get("id") or style.get("style_id")
        formatted_sref = style.get("formatted_sref") or style.get("formattedSref") or style.get("sref")
        if not style_id and formatted_sref:
            style_id = formatted_sref if str(formatted_sref).startswith("0_") else f"0_{formatted_sref}"
        if not style_id:
            continue

        clean_sref = str(formatted_sref or style_id).replace("0_", "", 1)
        title = f"Style {clean_sref}"
        media_url = style.get("image_url") or style.get("imageUrl")
        if not media_url:
            media_url = f"https://cdn.midjourney.com/styles/{style_id}/portrait_384_N.webp"

        preview_job_ids = style.get("previewJobIds") or style.get("preview_job_ids")
        if isinstance(preview_job_ids, list) and preview_job_ids:
            page_url = f"https://www.midjourney.com/jobs/{preview_job_ids[0]}"
        else:
            page_url = f"https://www.midjourney.com/explore?tab=styles_random"

        items.append(
            {
                "mediaType": "image",
                "mediaUrl": media_url,
                "thumbnailUrl": media_url,
                "pageUrl": page_url,
                "prompt": title,
                "title": title,
                "styleCode": clean_sref,
                "width": 896,
                "height": 1344,
            }
        )
    return items


def save_asset(context: Any, item: dict[str, Any], max_bytes: int) -> str | None:
    if item.get("tab") == "videos":
        url = item.get("thumbnail_url") or item.get("media_url")
        if url and VIDEO_URL_RE.search(url):
            print(f"warning: skipping video file for thumbnail-only video tab item {item['id']}", file=sys.stderr)
            return None
    else:
        url = item.get("media_url") or item.get("thumbnail_url")
    if not url:
        return None

    media_dir = MEDIA_ROOT / item["tab"]
    media_dir.mkdir(parents=True, exist_ok=True)

    response = None
    body = None
    try:
        response = context.request.get(
            url,
            headers={"referer": item.get("source_tab_url") or "https://www.midjourney.com/explore"},
            timeout=60000,
        )
    except Exception as exc:
        print(f"warning: request download failed for {url}: {exc}", file=sys.stderr)

    def read_browser_body(download_context: Any, label: str) -> tuple[Any, bytes | None]:
        page = download_context.new_page()
        try:
            page_response = page.goto(url, wait_until="load", timeout=60000)
            if page_response is None or not page_response.ok:
                status = page_response.status if page_response is not None else "no response"
                print(f"warning: {label} browser download failed {status} {url}", file=sys.stderr)
                return page_response, None
            return page_response, page_response.body()
        except Exception as exc:
            print(f"warning: {label} browser download failed for {url}: {exc}", file=sys.stderr)
            return None, None
        finally:
            page.close()

    if response is not None and response.ok:
        body = response.body()
    else:
        status = response.status if response is not None else "no response"
        print(f"warning: request download failed {status} {url}; trying browser fallback", file=sys.stderr)
        response, body = read_browser_body(context, "current-context")

    if body is None:
        browser = getattr(context, "browser", None)
        if browser is not None:
            isolated = browser.new_context(
                user_agent=(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/126.0.0.0 Safari/537.36"
                ),
                locale="en-US",
            )
            try:
                response, body = read_browser_body(isolated, "isolated-context")
            finally:
                isolated.close()

    if response is None or not response.ok or body is None:
        status = response.status if response is not None else "no response"
        print(f"warning: download failed {status} {url}", file=sys.stderr)
        return None

    content_length = response.headers.get("content-length")
    if content_length and content_length.isdigit() and int(content_length) > max_bytes:
        print(f"warning: skipping oversized asset {url}", file=sys.stderr)
        return None

    if len(body) > max_bytes:
        print(f"warning: skipping oversized asset {url}", file=sys.stderr)
        return None

    content_type = response.headers.get("content-type")
    ext = extension_from(url, content_type, item["media_type"])
    asset_path = media_dir / f"{item['id']}{ext}"
    if not asset_path.exists():
        asset_path.write_bytes(body)
    return relative_to_docs(asset_path)


def crawl_tab(context: Any, tab: dict[str, str], args: argparse.Namespace) -> list[dict[str, Any]]:
    page = context.new_page()
    explore_payloads: list[Any] = []
    style_payloads: list[Any] = []

    def capture_api_response(response: Any) -> None:
        if response.status != 200:
            return
        is_style_feed = "/api/explore-srefs" in response.url
        is_explore_feed = "/api/explore" in response.url and not is_style_feed
        if not is_style_feed and not is_explore_feed:
            return
        try:
            payload = response.json()
            if is_style_feed:
                style_payloads.append(payload)
            else:
                explore_payloads.append(payload)
        except Exception as exc:
            print(f"warning: could not parse {response.url}: {exc}", file=sys.stderr)

    page.on("response", capture_api_response)
    try:
        print(f"opening {tab['label']}: {tab['url']}")
        page.goto(tab["url"], wait_until="domcontentloaded", timeout=args.timeout_ms)
        try:
            page.wait_for_load_state("networkidle", timeout=15000)
        except Exception:
            pass
        page.wait_for_timeout(args.initial_wait_ms)
        detect_blocked(page)

        for _ in range(args.scroll_steps):
            page.mouse.wheel(0, args.scroll_pixels)
            page.wait_for_timeout(args.scroll_wait_ms)
            detect_blocked(page)

        if tab["key"] == "styles" and not style_payloads:
            fetched_style_payloads = page.evaluate(
                r"""
                async () => {
                  const payloads = [];
                  for (const feed of ["random", "hot", "top"]) {
                    const url = new URL("/api/explore-srefs", location.origin);
                    url.searchParams.set("feed", feed);
                    url.searchParams.set("page", "0");
                    const response = await fetch(url.href, {
                      credentials: "include",
                      headers: { "X-CSRF-Protection": "1" }
                    });
                    if (response.ok) {
                      payloads.push(await response.json());
                    }
                  }
                  return payloads;
                }
                """
            )
            if fetched_style_payloads:
                style_payloads.extend(fetched_style_payloads)

        raw_items: list[dict[str, Any]] = []
        if tab["key"] == "styles":
            for payload in style_payloads:
                raw_items.extend(style_items_from_payload(payload))
        else:
            for payload in explore_payloads:
                raw_items.extend(api_items_from_payload(tab, payload))

        if tab["key"] != "styles":
            raw_items.extend(
                page.evaluate(
                    EXTRACT_SCRIPT,
                    {"expectedType": tab["media_type"], "limit": args.max_per_tab * 4},
                )
            )
        normalized = []
        seen = set()
        for raw in raw_items:
            item = normalize_item(tab, raw, args.captured_at)
            if item["id"] in seen:
                continue
            seen.add(item["id"])
            normalized.append(item)
            if len(normalized) >= args.max_per_tab:
                break
        print(f"found {len(normalized)} candidate items in {tab['label']}")
        return normalized
    finally:
        page.close()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Archive public Midjourney Explore tabs.")
    parser.add_argument(
        "--max-per-tab",
        type=int,
        default=env_int("MAX_PER_TAB", 24),
        help="Maximum new candidates to inspect per tab.",
    )
    parser.add_argument(
        "--tab",
        choices=[tab["key"] for tab in SOURCE_TABS],
        action="append",
        help="Limit the crawl to one or more tab keys.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        default=env_bool("HEADED", False),
        help="Run Chromium with a visible window.",
    )
    parser.add_argument(
        "--no-download",
        action="store_true",
        default=not env_bool("DOWNLOAD_MEDIA", True),
        help="Store metadata only and leave media URLs remote.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Crawl and report without writing data or media files.",
    )
    parser.add_argument(
        "--backfill-assets-only",
        action="store_true",
        help="Download missing docs/media assets for items already in the archive.",
    )
    parser.add_argument("--scroll-steps", type=int, default=env_int("SCROLL_STEPS", 4))
    parser.add_argument("--scroll-pixels", type=int, default=env_int("SCROLL_PIXELS", 2200))
    parser.add_argument("--scroll-wait-ms", type=int, default=env_int("SCROLL_WAIT_MS", 1200))
    parser.add_argument("--initial-wait-ms", type=int, default=env_int("INITIAL_WAIT_MS", 3000))
    parser.add_argument("--timeout-ms", type=int, default=env_int("TIMEOUT_MS", 90000))
    parser.add_argument(
        "--max-asset-mb",
        type=int,
        default=env_int("MAX_ASSET_MB", 80),
        help="Skip a single downloaded asset above this size.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    args.captured_at = utc_now()
    selected_tabs = [tab for tab in SOURCE_TABS if not args.tab or tab["key"] in args.tab]
    max_bytes = max(1, args.max_asset_mb) * 1024 * 1024

    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print(
            "Playwright is not installed. Run: python -m pip install -r requirements.txt",
            file=sys.stderr,
        )
        return 2

    archive = load_archive()
    existing_items = [item for item in archive.get("items", []) if item.get("id")]
    existing_by_id = {item.get("id"): item for item in existing_items}
    existing_ids = set(existing_by_id)
    new_items: list[dict[str, Any]] = []
    download_items: list[dict[str, Any]] = []
    extracted_total = 0
    crawl_errors: list[str] = []

    storage_state = prepare_storage_state()
    cookies = parse_cookie_secret(os.getenv("MIDJOURNEY_COOKIES", ""))

    if args.backfill_assets_only:
        missing = [
            item
            for item in archive.get("items", [])
            if item.get("id") and not item.get("asset_path") and item.get("media_url")
        ]
        if not missing:
            print("No missing assets to backfill.")
            return 0
        if args.dry_run:
            print(f"missing assets: {len(missing)}")
            for item in missing[:10]:
                print(f"- {item['tab']} {item['id']} {item.get('media_url')}")
            return 0
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(
                headless=not args.headed,
                args=["--disable-dev-shm-usage", "--no-sandbox"],
            )
            context = browser.new_context(
                user_agent=(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/126.0.0.0 Safari/537.36"
                ),
                locale="en-US",
            )
            backfilled_assets = 0
            try:
                for item in missing:
                    item["asset_path"] = save_asset(context, item, max_bytes)
                    if item.get("asset_path"):
                        backfilled_assets += 1
            finally:
                context.close()
                browser.close()
        if backfilled_assets and not args.dry_run:
            archive["updated_at"] = args.captured_at
            update_counts(archive)
            write_archive(archive)
        print(f"backfilled assets: {backfilled_assets}")
        return 0 if backfilled_assets else 1

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=not args.headed,
            args=["--disable-dev-shm-usage", "--no-sandbox"],
        )
        context_kwargs = {
            "viewport": {"width": 1440, "height": 1200},
            "user_agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/126.0.0.0 Safari/537.36"
            ),
            "locale": "en-US",
        }
        if storage_state:
            context_kwargs["storage_state"] = str(storage_state)
        context = browser.new_context(**context_kwargs)
        if cookies:
            context.add_cookies(cookies)

        try:
            for tab in selected_tabs:
                try:
                    candidates = crawl_tab(context, tab, args)
                except Exception as exc:
                    message = f"{tab['key']}: {exc}"
                    crawl_errors.append(message)
                    print(f"warning: {message}", file=sys.stderr)
                    continue
                extracted_total += len(candidates)
                for item in candidates:
                    if item["id"] in existing_ids:
                        existing = existing_by_id.get(item["id"])
                        if existing and not existing.get("asset_path"):
                            download_items.append(existing)
                        continue
                    new_items.append(item)
                    download_items.append(item)
                    existing_ids.add(item["id"])

            backfilled_assets = 0
            if download_items and not args.no_download and not args.dry_run:
                for item in download_items:
                    if item.get("asset_path"):
                        continue
                    item["asset_path"] = save_asset(context, item, max_bytes)
                    if item.get("asset_path") and item not in new_items:
                        backfilled_assets += 1
        finally:
            context.close()
            browser.close()

    if extracted_total == 0:
        for error in crawl_errors:
            print(f"crawl error: {error}", file=sys.stderr)
        print("No items were extracted from any Midjourney tab.", file=sys.stderr)
        return 1

    if not new_items and backfilled_assets == 0:
        print("No new Midjourney items found. Existing archive is unchanged.")
        return 0

    print(f"new items: {len(new_items)}")
    if backfilled_assets:
        print(f"backfilled assets: {backfilled_assets}")
    if args.dry_run:
        for item in new_items[:10]:
            print(f"- {item['tab']} {item['id']} {item.get('media_url')}")
        return 0

    archive["version"] = 1
    archive["updated_at"] = args.captured_at
    archive["source_tabs"] = [
        {"key": tab["key"], "label": tab["label"], "url": tab["url"]}
        for tab in SOURCE_TABS
    ]
    archive["crawl_errors"] = crawl_errors
    archive["items"] = new_items + archive.get("items", [])
    update_counts(archive)
    write_archive(archive)
    print(f"archive updated: {DATA_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
