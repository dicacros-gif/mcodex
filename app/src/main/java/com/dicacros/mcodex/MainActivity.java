package com.dicacros.mcodex;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int SCROLL_STEPS = 10;
    private static final int MAX_ASSET_BYTES = 25 * 1024 * 1024;
    private static final String[] KINDS = {"styles", "images", "videos"};
    private static final String[] LABELS = {"Styles", "Images", "Videos"};
    private static final String[] URLS = {
            "https://www.midjourney.com/explore?tab=styles_top",
            "https://www.midjourney.com/explore?tab=top",
            "https://www.midjourney.com/explore?tab=video_top"
    };

    private static final String SCROLL_JS =
            "(function(){"
                    + "window.scrollBy(0, Math.max(800, Math.floor(window.innerHeight*0.9)));"
                    + "return Math.round(window.scrollY||document.documentElement.scrollTop||0);"
                    + "})();";

    private static final String EXTRACT_JS =
            "(function(){"
                    + "const urls=new Set();"
                    + "const addOne=function(raw){try{"
                    + "if(!raw)return;"
                    + "raw=String(raw).trim();"
                    + "if(!raw)return;"
                    + "if(raw.indexOf(',')>=0){raw.split(',').forEach(function(part){addOne(part.trim().split(/\\s+/)[0]);});return;}"
                    + "raw=raw.replace(/^url\\([\"']?/,'').replace(/[\"']?\\)$/,'');"
                    + "const u=new URL(raw,location.href);"
                    + "if(!/^https?:$/.test(u.protocol))return;"
                    + "const href=u.href;"
                    + "if(!/(midjourney|discordapp|discord|cloudfront)/i.test(href))return;"
                    + "if(!/\\.(webp|png|jpe?g)(\\?|#|$)/i.test(href))return;"
                    + "urls.add(href);"
                    + "}catch(e){}};"
                    + "document.querySelectorAll('img').forEach(function(img){addOne(img.currentSrc||img.src);addOne(img.srcset);addOne(img.getAttribute('src'));addOne(img.getAttribute('data-src'));});"
                    + "document.querySelectorAll('source').forEach(function(source){addOne(source.srcset);addOne(source.src);});"
                    + "document.querySelectorAll('video').forEach(function(video){addOne(video.poster);addOne(video.currentSrc||video.src);});"
                    + "document.querySelectorAll('[style]').forEach(function(el){const bg=getComputedStyle(el).backgroundImage;if(bg&&bg!=='none'){addOne(bg);}});"
                    + "return {urls:Array.from(urls),title:document.title||'',href:location.href,text:(document.body&&document.body.innerText?document.body.innerText.slice(0,800):'')};"
                    + "})();";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);
    private final ArrayList<ArchiveItem> items = new ArrayList<>();
    private final Set<String> itemKeys = new HashSet<>();
    private final Set<String> deletedKeys = new HashSet<>();
    private final Set<String> downloadingKeys = new HashSet<>();

    private File archiveDir;
    private File mediaDir;
    private File archiveFile;
    private WebView webView;
    private LinearLayout uiRoot;
    private LinearLayout sessionBar;
    private GridLayout grid;
    private TextView statusText;
    private TextView countText;
    private String userAgent;
    private int targetIndex;
    private int scrollStep;
    private int pendingDownloads;
    private boolean crawling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        archiveDir = new File(getFilesDir(), "midjourney_archive");
        mediaDir = new File(archiveDir, "media");
        archiveFile = new File(archiveDir, "archive.json");
        loadArchive();
        buildUi();
        configureWebView();
        renderArchive();
        mainHandler.postDelayed(this::startCrawl, 600);
    }

    @Override
    protected void onDestroy() {
        downloadExecutor.shutdownNow();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout frame = new FrameLayout(this);

        webView = new WebView(this);
        webView.setAlpha(0.01f);
        frame.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        uiRoot = new LinearLayout(this);
        uiRoot.setOrientation(LinearLayout.VERTICAL);
        uiRoot.setBackgroundColor(Color.rgb(15, 17, 21));
        uiRoot.setPadding(dp(12), dp(12), dp(12), 0);
        frame.addView(uiRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        uiRoot.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("MJ Local Archive");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(title);

        countText = new TextView(this);
        countText.setTextColor(Color.rgb(180, 187, 199));
        countText.setTextSize(13f);
        titleBox.addView(countText);

        Button refreshButton = smallButton("Refresh");
        refreshButton.setOnClickListener(v -> startCrawl());
        header.addView(refreshButton);

        Button sessionButton = smallButton("Session");
        sessionButton.setOnClickListener(v -> showSession(true));
        header.addView(sessionButton);

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(210, 216, 225));
        statusText.setTextSize(13f);
        statusText.setPadding(0, dp(10), 0, dp(8));
        uiRoot.addView(statusText);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        grid = new GridLayout(this);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        scrollView.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        uiRoot.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        sessionBar = new LinearLayout(this);
        sessionBar.setOrientation(LinearLayout.HORIZONTAL);
        sessionBar.setGravity(Gravity.CENTER_VERTICAL);
        sessionBar.setPadding(dp(10), dp(10), dp(10), dp(10));
        sessionBar.setBackgroundColor(Color.rgb(15, 17, 21));
        sessionBar.setVisibility(View.GONE);
        Button backButton = smallButton("Back");
        backButton.setOnClickListener(v -> showSession(false));
        sessionBar.addView(backButton);
        TextView sessionText = new TextView(this);
        sessionText.setText("Midjourney session");
        sessionText.setTextColor(Color.WHITE);
        sessionText.setTextSize(16f);
        sessionText.setTypeface(Typeface.DEFAULT_BOLD);
        sessionText.setPadding(dp(12), 0, 0, 0);
        sessionBar.addView(sessionText);
        FrameLayout.LayoutParams sessionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        frame.addView(sessionBar, sessionParams);

        setContentView(frame);
        setStatus("Ready. Crawling starts automatically.");
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = rounded(Color.rgb(37, 44, 56), dp(6));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38)
        );
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        userAgent = WebSettings.getDefaultUserAgent(this);
        settings.setUserAgentString(userAgent);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!crawling || targetIndex >= URLS.length) {
                    return;
                }
                setStatus("Loaded " + LABELS[targetIndex] + ". Reading visible thumbnails...");
                scrollStep = 0;
                mainHandler.postDelayed(MainActivity.this::crawlStep, 2200);
            }
        });
    }

    private void showSession(boolean show) {
        if (show) {
            uiRoot.setVisibility(View.GONE);
            webView.setAlpha(1f);
            webView.bringToFront();
            sessionBar.setVisibility(View.VISIBLE);
            sessionBar.bringToFront();
        } else {
            webView.setAlpha(0.01f);
            uiRoot.setVisibility(View.VISIBLE);
            uiRoot.bringToFront();
            sessionBar.setVisibility(View.GONE);
        }
    }

    private void startCrawl() {
        if (crawling) {
            setStatus("Crawl already running.");
            return;
        }
        crawling = true;
        targetIndex = 0;
        scrollStep = 0;
        setStatus("Starting device crawl...");
        loadTarget();
    }

    private void loadTarget() {
        if (!crawling) {
            return;
        }
        if (targetIndex >= URLS.length) {
            crawling = false;
            saveArchive();
            setStatus("Crawl complete. " + items.size() + " saved, " + pendingDownloads + " downloads still finishing.");
            return;
        }
        setStatus("Opening " + LABELS[targetIndex] + " tab...");
        webView.loadUrl(URLS[targetIndex]);
    }

    private void crawlStep() {
        if (!crawling || targetIndex >= URLS.length) {
            return;
        }

        extractVisibleUrls();

        if (scrollStep < SCROLL_STEPS) {
            scrollStep++;
            webView.evaluateJavascript(SCROLL_JS, value -> {
                setStatus("Crawling " + LABELS[targetIndex] + " " + scrollStep + "/" + SCROLL_STEPS);
                mainHandler.postDelayed(MainActivity.this::crawlStep, 1300);
            });
            return;
        }

        targetIndex++;
        scrollStep = 0;
        mainHandler.postDelayed(this::loadTarget, 700);
    }

    private void extractVisibleUrls() {
        final int kindIndex = targetIndex;
        final String kind = KINDS[kindIndex];
        final String label = LABELS[kindIndex];
        webView.evaluateJavascript(EXTRACT_JS, result -> {
            try {
                JSONObject payload = new JSONObject(result);
                JSONArray urls = payload.optJSONArray("urls");
                String pageTitle = payload.optString("title", "");
                String pageText = payload.optString("text", "");
                int queued = 0;

                if (urls != null) {
                    for (int i = 0; i < urls.length(); i++) {
                        String rawUrl = urls.optString(i, "");
                        String sourceUrl = cleanSourceUrl(rawUrl);
                        if (sourceUrl == null) {
                            continue;
                        }
                        String key = keyForUrl(sourceUrl);
                        if (!reserveDownloadKey(key)) {
                            continue;
                        }
                        queued++;
                        submitDownload(kind, sourceUrl, key);
                    }
                }

                if (queued == 0 && looksBlocked(pageTitle, pageText)) {
                    setStatus(label + " needs a normal WebView session. Tap Session if sign-in is required.");
                } else if (queued > 0) {
                    setStatus(label + ": queued " + queued + " new thumbnails.");
                }
            } catch (JSONException e) {
                setStatus("Could not read page data yet.");
            }
        });
    }

    private boolean reserveDownloadKey(String key) {
        if (key == null || key.length() == 0) {
            return false;
        }
        if (itemKeys.contains(key) || deletedKeys.contains(key) || downloadingKeys.contains(key)) {
            return false;
        }
        downloadingKeys.add(key);
        pendingDownloads++;
        updateCounts();
        return true;
    }

    private void submitDownload(String kind, String sourceUrl, String key) {
        downloadExecutor.execute(() -> {
            try {
                File file = downloadToFile(kind, sourceUrl, key);
                ArchiveItem item = new ArchiveItem();
                item.id = kind + "-" + key.substring(0, Math.min(16, key.length()));
                item.kind = kind;
                item.sourceUrl = sourceUrl;
                item.key = key;
                item.localPath = file.getAbsolutePath();
                item.savedAt = System.currentTimeMillis();
                mainHandler.post(() -> finishDownload(item, key, null));
            } catch (Exception e) {
                mainHandler.post(() -> finishDownload(null, key, e.getMessage()));
            }
        });
    }

    private void finishDownload(ArchiveItem item, String key, String error) {
        downloadingKeys.remove(key);
        pendingDownloads = Math.max(0, pendingDownloads - 1);

        if (item == null) {
            updateCounts();
            if (error != null && error.length() > 0) {
                setStatus("Skipped one thumbnail: " + error);
            }
            return;
        }

        if (deletedKeys.contains(item.key) || itemKeys.contains(item.key)) {
            deleteFileQuietly(new File(item.localPath));
            updateCounts();
            return;
        }

        itemKeys.add(item.key);
        items.add(item);
        saveArchive();
        renderArchive();
        setStatus("Saved " + item.kind + " thumbnail. " + pendingDownloads + " downloads pending.");
    }

    private File downloadToFile(String kind, String sourceUrl, String key) throws IOException {
        File kindDir = new File(mediaDir, kind);
        if (!kindDir.exists() && !kindDir.mkdirs()) {
            throw new IOException("storage unavailable");
        }

        String ext = extensionForUrl(sourceUrl);
        File target = new File(kindDir, kind + "-" + key.substring(0, Math.min(24, key.length())) + "." + ext);
        if (target.exists() && target.length() > 0) {
            return target;
        }

        File partial = new File(kindDir, target.getName() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        String cookie = CookieManager.getInstance().getCookie(sourceUrl);
        if (cookie != null && cookie.length() > 0) {
            connection.setRequestProperty("Cookie", cookie);
        }

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            int length = connection.getContentLength();
            if (length > MAX_ASSET_BYTES) {
                throw new IOException("asset too large");
            }
            String contentType = connection.getContentType();
            if (contentType != null
                    && !contentType.toLowerCase(Locale.US).contains("image")
                    && !hasImageExtension(sourceUrl)) {
                throw new IOException("not an image");
            }

            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ASSET_BYTES) {
                        throw new IOException("asset too large");
                    }
                    output.write(buffer, 0, read);
                }
            }

            if (target.exists()) {
                deleteFileQuietly(target);
            }
            if (!partial.renameTo(target)) {
                throw new IOException("could not store file");
            }
            return target;
        } finally {
            connection.disconnect();
            if (partial.exists() && !target.exists()) {
                deleteFileQuietly(partial);
            }
        }
    }

    private void deleteItem(ArchiveItem item) {
        deletedKeys.add(item.key);
        itemKeys.remove(item.key);
        items.remove(item);
        deleteFileQuietly(new File(item.localPath));
        saveArchive();
        renderArchive();
        setStatus("Deleted one saved thumbnail from device storage.");
    }

    private void renderArchive() {
        if (grid == null) {
            return;
        }
        grid.removeAllViews();

        int widthPx = getResources().getDisplayMetrics().widthPixels - dp(24);
        int widthDp = (int) (widthPx / getResources().getDisplayMetrics().density);
        int columns = widthDp >= 700 ? 3 : 2;
        if (widthDp < 340) {
            columns = 1;
        }
        grid.setColumnCount(columns);

        int cardWidth = Math.max(dp(150), (widthPx - dp(10) * columns) / columns);
        int imageHeight = columns == 1 ? dp(320) : dp(230);

        ArrayList<ArchiveItem> ordered = new ArrayList<>(items);
        Collections.sort(ordered, (a, b) -> Long.compare(b.savedAt, a.savedAt));

        for (ArchiveItem item : ordered) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(rounded(Color.rgb(26, 30, 38), dp(8)));
            card.setPadding(dp(6), dp(6), dp(6), dp(6));

            ImageView imageView = new ImageView(this);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackgroundColor(Color.rgb(10, 12, 16));
            Bitmap bitmap = decodeThumbnail(item.localPath, cardWidth, imageHeight);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageURI(Uri.fromFile(new File(item.localPath)));
            }
            card.addView(imageView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    imageHeight
            ));

            LinearLayout meta = new LinearLayout(this);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            meta.setPadding(0, dp(6), 0, 0);

            TextView kindText = new TextView(this);
            kindText.setText(item.kind);
            kindText.setTextColor(Color.rgb(210, 216, 225));
            kindText.setTextSize(12f);
            meta.addView(kindText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button deleteButton = smallButton("X");
            deleteButton.setContentDescription("Delete saved thumbnail");
            deleteButton.setOnClickListener(v -> deleteItem(item));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(42), dp(34));
            deleteButton.setLayoutParams(deleteParams);
            meta.addView(deleteButton);

            card.addView(meta);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardWidth;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(dp(5), dp(5), dp(5), dp(9));
            card.setLayoutParams(params);
            grid.addView(card);
        }

        updateCounts();
    }

    private Bitmap decodeThumbnail(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        int sample = 1;
        while ((bounds.outWidth / sample) > reqWidth * 2 || (bounds.outHeight / sample) > reqHeight * 2) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, options);
    }

    private void updateCounts() {
        int images = 0;
        int styles = 0;
        int videos = 0;
        for (ArchiveItem item : items) {
            if ("images".equals(item.kind)) {
                images++;
            } else if ("styles".equals(item.kind)) {
                styles++;
            } else if ("videos".equals(item.kind)) {
                videos++;
            }
        }
        countText.setText(items.size() + " saved  |  images " + images + "  styles " + styles + "  videos " + videos
                + "  |  pending " + pendingDownloads);
    }

    private void setStatus(String message) {
        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private void loadArchive() {
        if (!archiveFile.exists()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(readText(archiveFile));
            JSONArray deleted = root.optJSONArray("deletedKeys");
            if (deleted != null) {
                for (int i = 0; i < deleted.length(); i++) {
                    String key = deleted.optString(i, "");
                    if (key.length() > 0) {
                        deletedKeys.add(key);
                    }
                }
            }

            JSONArray savedItems = root.optJSONArray("items");
            if (savedItems == null) {
                return;
            }
            for (int i = 0; i < savedItems.length(); i++) {
                JSONObject obj = savedItems.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                ArchiveItem item = ArchiveItem.fromJson(obj);
                if (item == null || item.key == null || deletedKeys.contains(item.key)) {
                    continue;
                }
                File file = new File(item.localPath);
                if (!file.exists() || file.length() == 0) {
                    continue;
                }
                if (itemKeys.contains(item.key)) {
                    deleteFileQuietly(file);
                    continue;
                }
                itemKeys.add(item.key);
                items.add(item);
            }
        } catch (Exception ignored) {
            items.clear();
            itemKeys.clear();
        }
    }

    private void saveArchive() {
        if (!archiveDir.exists() && !archiveDir.mkdirs()) {
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("updatedAt", System.currentTimeMillis());
            JSONArray savedItems = new JSONArray();
            for (ArchiveItem item : items) {
                savedItems.put(item.toJson());
            }
            root.put("items", savedItems);

            JSONArray deleted = new JSONArray();
            for (String key : deletedKeys) {
                deleted.put(key);
            }
            root.put("deletedKeys", deleted);

            File temp = new File(archiveDir, "archive.json.tmp");
            writeText(temp, root.toString(2));
            if (archiveFile.exists() && !archiveFile.delete()) {
                deleteFileQuietly(temp);
                return;
            }
            if (!temp.renameTo(archiveFile)) {
                deleteFileQuietly(temp);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean looksBlocked(String title, String text) {
        String combined = (title + "\n" + text).toLowerCase(Locale.US);
        return combined.contains("cloudflare")
                || combined.contains("just a moment")
                || combined.contains("attention required")
                || combined.contains("sign in")
                || combined.contains("login")
                || combined.contains("verify you are human");
    }

    private String cleanSourceUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() == 0 || trimmed.startsWith("data:") || trimmed.startsWith("blob:")) {
            return null;
        }
        try {
            URL url = new URL(trimmed);
            String protocol = url.getProtocol().toLowerCase(Locale.US);
            if (!"https".equals(protocol) && !"http".equals(protocol)) {
                return null;
            }
            if (!hasImageExtension(trimmed)) {
                return null;
            }
            return trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    private String keyForUrl(String sourceUrl) {
        try {
            URL url = new URL(sourceUrl);
            String normalized = url.getProtocol().toLowerCase(Locale.US) + "://"
                    + url.getHost().toLowerCase(Locale.US)
                    + url.getPath();
            return sha256(normalized);
        } catch (Exception e) {
            return sha256(sourceUrl);
        }
    }

    private String extensionForUrl(String sourceUrl) {
        try {
            URL url = new URL(sourceUrl);
            String path = url.getPath().toLowerCase(Locale.US);
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String ext = path.substring(dot + 1);
                if ("jpeg".equals(ext)) {
                    return "jpg";
                }
                if ("jpg".equals(ext) || "png".equals(ext) || "webp".equals(ext)) {
                    return ext;
                }
            }
        } catch (Exception ignored) {
        }
        return "webp";
    }

    private boolean hasImageExtension(String sourceUrl) {
        String lower = sourceUrl.toLowerCase(Locale.US);
        return lower.contains(".webp")
                || lower.contains(".jpg")
                || lower.contains(".jpeg")
                || lower.contains(".png");
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format(Locale.US, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    private String readText(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private void writeText(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void deleteFileQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class ArchiveItem {
        String id;
        String kind;
        String sourceUrl;
        String key;
        String localPath;
        long savedAt;

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("kind", kind);
            obj.put("sourceUrl", sourceUrl);
            obj.put("key", key);
            obj.put("localPath", localPath);
            obj.put("savedAt", savedAt);
            return obj;
        }

        static ArchiveItem fromJson(JSONObject obj) {
            ArchiveItem item = new ArchiveItem();
            item.id = obj.optString("id", "");
            item.kind = obj.optString("kind", "");
            item.sourceUrl = obj.optString("sourceUrl", "");
            item.key = obj.optString("key", "");
            item.localPath = obj.optString("localPath", "");
            item.savedAt = obj.optLong("savedAt", 0L);
            if (item.id.length() == 0 || item.kind.length() == 0 || item.key.length() == 0 || item.localPath.length() == 0) {
                return null;
            }
            return item;
        }
    }
}
