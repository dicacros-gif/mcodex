# Midjourney Public Archive

Midjourney Explore의 공개 탭을 매일 크롤링해서 `docs/` 아래에 누적 저장하는 저장소입니다.

- 이미지 탭: `https://www.midjourney.com/explore?tab=top`
- 비디오 탭: `https://www.midjourney.com/explore?tab=videos`
- 스타일 탭: `https://www.midjourney.com/explore?tab=styles_random`

새 항목만 `docs/data/midjourney.json` 맨 위에 추가합니다. 기존 JSON 항목과 `docs/media/`에 받은 이미지/비디오는 삭제하지 않습니다. GitHub Pages를 `docs` 폴더로 켜면 `docs/index.html`에서 최신 항목이 위에 보입니다.

비디오 탭은 영상 파일을 저장하지 않고 썸네일 이미지만 `docs/media/videos/`에 저장합니다. 실제 영상 URL이 발견되어도 GitHub에는 썸네일만 커밋됩니다.

## Daily automation

`.github/workflows/midjourney-crawl.yml`이 매일 09:00 KST에 실행됩니다. 수동 실행도 가능합니다.

1. GitHub 저장소의 **Actions** 탭에서 `Midjourney daily crawl`을 선택합니다.
2. **Run workflow**를 누르면 즉시 한 번 실행됩니다.
3. 매일 자동 실행에서 새 항목이 있으면 Actions가 `docs/` 변경분을 커밋합니다.

## Midjourney access

Midjourney는 Cloudflare 또는 로그인 챌린지를 반환할 수 있습니다. 이 경우 GitHub 저장소 **Settings > Secrets and variables > Actions**에 아래 중 하나를 추가한 뒤 다시 실행하세요.

- `MIDJOURNEY_STORAGE_STATE_JSON`: Playwright/브라우저 storage state JSON 전체
- `MIDJOURNEY_COOKIES`: `name=value; name2=value2` 형식의 쿠키 문자열 또는 쿠키 JSON 배열

공개 페이지라도 자동화 환경의 IP나 브라우저 상태에 따라 차단될 수 있습니다. 크롤러는 차단 페이지를 감지하면 데이터를 덮어쓰지 않고 실패하도록 만들어져 있습니다.

스타일 탭은 Midjourney의 `/api/explore-srefs` endpoint를 사용합니다. 현재 비로그인 자동화 세션에서는 이 endpoint가 403을 반환할 수 있으므로, 스타일 수집까지 안정적으로 하려면 위 쿠키 또는 storage state secret을 설정하세요.

## Optional variables

저장소 **Variables**에서 조정할 수 있습니다.

- `MAX_PER_TAB`: 탭마다 확인할 최대 항목 수, 기본값 `24`
- `MAX_ASSET_MB`: 단일 파일 다운로드 최대 MB, 기본값 `80`
- `SCROLL_STEPS`: 페이지 스크롤 횟수, 기본값 `4`
- `DOWNLOAD_MEDIA`: `false`로 두면 파일 다운로드 없이 원본 URL 메타데이터만 저장

## Queue deletes from the homepage

`docs/index.html`의 설명란에 있는 `X`를 누르면 해당 항목이 즉시 화면에서 숨겨지고, 브라우저의 삭제 대기 목록에 저장됩니다. GitHub token은 필요하지 않습니다.

정적 GitHub Pages는 인증 없이 저장소 파일을 직접 수정할 수 없으므로, 실제 GitHub 파일 삭제는 다음 관리 작업에서 반영합니다. 홈페이지의 **Copy deletes** 버튼으로 삭제 대기 목록을 복사한 뒤 아래 스크립트에 넘기면 `docs/data/midjourney.json` 항목과 `docs/media/` 파일이 함께 제거됩니다.

```bash
python scripts/apply_deletions.py --file pending-deletes.json
```

아이디를 직접 넘길 수도 있습니다.

```bash
python scripts/apply_deletions.py --ids images-example-id,videos-example-id
```

## Local run

```bash
python -m pip install -r requirements.txt
python -m playwright install chromium
python scripts/crawl_midjourney.py
```

로컬에서 로그인 상태를 써야 한다면 `MIDJOURNEY_COOKIES` 또는 `MIDJOURNEY_STORAGE_STATE_JSON` 환경 변수를 지정해서 실행합니다.

이미 JSON에는 있지만 `docs/media/` 파일이 비어 있는 항목만 다시 저장하려면:

```bash
python scripts/crawl_midjourney.py --backfill-assets-only
```
