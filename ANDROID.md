# Android local archive app

This repository includes a native Android app under `app/`.

Behavior:

- Starts a Midjourney Explore crawl when the app opens.
- Includes an in-app `Options` panel for crawl control.
- Crawls these tabs through the device WebView session:
  - `https://www.midjourney.com/explore?tab=styles_top`
  - `https://www.midjourney.com/explore?tab=top`
  - `https://www.midjourney.com/explore?tab=video_top`
- Stores downloaded image files in the app's private internal storage.
- Stores video-tab results as thumbnail images only.
- Skips duplicate source URLs.
- Remembers deleted source keys so deleted items are not re-added on the next crawl.
- Deletes both the visible item and the stored file when the `X` button is tapped.

Options:

- Turn automatic crawl on or off.
- Enable or disable styles, images, and video thumbnail tabs independently.
- Change scroll depth from 1 to 30 steps.
- Limit newly saved thumbnails per tab from 10 to 300.
- Start a crawl manually.
- Clean missing or duplicate archive entries.
- Reset delete memory so previously deleted sources can be crawled again.
- Delete all locally saved thumbnails from the app's private storage.

The app does not include challenge-solving or anti-bot bypass code. If Midjourney,
Cloudflare, or login blocks the embedded WebView, the app keeps the existing local
archive unchanged and shows a status message. Use the `Session` button to open the
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
