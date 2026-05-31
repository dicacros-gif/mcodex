# Android local archive app

This repository includes a native Android app under `app/`.

Behavior:

- Opens to the saved-image gallery first. Crawling starts only when `Crawl` is
  tapped or when the user turns automatic crawl back on.
- Includes an in-app `Options` panel for crawl control.
- Shows `Images`, `Styles`, and `Videos` as three visible tappable tabs.
- Shows saved items under `TOP DAY` date sections, newest date first.
- The `Explorer` button opens the currently selected Midjourney tab in the
  visible WebView session.
- The Explorer screen has `Save View` for saving the visible page as an image
  when URL extraction fails, plus `Crawl` for starting from that rendered session.
- The app scans `Pictures/MJLocalArchive` on launch, on tab change, and through
  the `Import` option, so gallery files can reappear even if the app archive was
  empty.
- Saves WebView screen captures by default, so the app can still keep images
  when the page does not expose downloadable thumbnail URLs.
- Crawls these tabs through the device WebView session:
  - `https://www.midjourney.com/explore?tab=styles_top`
  - `https://www.midjourney.com/explore?tab=top`
  - `https://www.midjourney.com/explore?tab=video_top`
- Stores downloaded images and WebView captures in the Android Gallery album:
  `Pictures/MJLocalArchive`.
- Stores video-tab results as thumbnail images only.
- Skips duplicate source URLs.
- Remembers deleted source keys so deleted items are not re-added on the next crawl.
- Deletes both the visible item and the stored file when the `X` button is tapped.

Options:

- Turn automatic crawl on or off.
- Pick the active source with the visible Images, Styles, and Videos tabs.
- Change scroll depth from 1 to 30 steps.
- Limit newly saved thumbnails per tab from 10 to 300.
- Turn WebView capture saving on or off.
- Turn direct image URL downloads on or off.
- Capture once after a tab loads.
- Capture again after each scroll step.
- Show the WebView while crawling so the actual signed-in session is rendered
  before screen capture.
- Try PixelCopy screen capture when the WebView is visible.
- Try WebView draw capture as a fallback.
- Try full-page draw capture as another fallback.
- Hide page buttons before capture.
- Clear the WebView cache before a crawl when the page appears stale.
- Tune page-load wait and scroll-pause wait times.
- Start a crawl manually.
- Save the currently visible Explorer screen.
- Import existing images from the Gallery `MJLocalArchive` album.
- Clean missing or duplicate archive entries.
- Reset delete memory so previously deleted sources can be crawled again.
- Delete all saved thumbnails that the app has recorded.
- Tap an image to open a full-screen preview with pinch zoom and drag.

The app does not include challenge-solving or anti-bot bypass code. If Midjourney,
Cloudflare, or login blocks the embedded WebView, the app keeps the existing local
archive unchanged and shows a status message. Use the `Explorer` button to open the
same WebView session and sign in normally if needed.

## Build APK

The GitHub workflow `Android APK` builds a debug APK and uploads it as an artifact:

```bash
gradle --no-daemon :app:assembleDebug
```

Local output path when Android SDK and Gradle are installed:

```text
app/build/outputs/apk/debug/app-debug.apk
```
