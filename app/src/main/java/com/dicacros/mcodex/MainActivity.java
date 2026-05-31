package com.dicacros.mcodex;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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
import android.widget.CheckBox;
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
    private static final String PREFS_NAME = "mj_local_archive_options";
    private static final int DEFAULT_SCROLL_STEPS = 10;
    private static final int DEFAULT_MAX_PER_TAB = 80;
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

    private static final String CLEAN_PAGE_JS =
            "(function(){"
                    + "const hide=function(el){if(el){el.style.setProperty('display','none','important');}};"
                    + "document.querySelectorAll('nav,header,[role=\"banner\"]').forEach(hide);"
                    + "document.querySelectorAll('[style]').forEach(function(el){"
                    + "const s=getComputedStyle(el);"
                    + "if((s.position==='fixed'||s.position==='sticky')&&(el.offsetHeight<160||el.offsetWidth<240)){hide(el);}"
                    + "});"
                    + "document.querySelectorAll('a,button,[role=\"button\"]').forEach(function(el){"
                    + "const t=(el.innerText||el.textContent||'').trim().toLowerCase();"
                    + "if(t==='create'||t==='updates'||t==='update'||t==='menu'||t==='크리에이트'||t==='업데이트'){hide(el);}"
                    + "});"
                    + "return true;"
                    + "})();";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);
    private final ArrayList<ArchiveItem> items = new ArrayList<>();
    private final Set<String> itemKeys = new HashSet<>();
    private final Set<String> deletedKeys = new HashSet<>();
    private final Set<String> downloadingKeys = new HashSet<>();

    private File archiveDir;
    private File legacyMediaDir;
    private File photosDir;
    private File archiveFile;
    private SharedPreferences prefs;
    private WebView webView;
    private LinearLayout uiRoot;
    private LinearLayout optionsPanel;
    private LinearLayout sessionBar;
    private GridLayout grid;
    private TextView statusText;
    private TextView countText;
    private TextView optionsSummaryText;
    private TextView scrollValueText;
    private TextView maxPerTabValueText;
    private CheckBox autoStartCheck;
    private CheckBox captureScreensCheck;
    private CheckBox stylesCheck;
    private CheckBox imagesCheck;
    private CheckBox videosCheck;
    private String userAgent;
    private int targetIndex;
    private int scrollStep;
    private int pendingDownloads;
    private int scrollSteps;
    private int maxPerTab;
    private final int[] queuedByTab = new int[KINDS.length];
    private boolean autoStart;
    private boolean captureScreens;
    private boolean includeStyles;
    private boolean includeImages;
    private boolean includeVideos;
    private boolean crawling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        archiveDir = new File(getFilesDir(), "midjourney_archive");
        legacyMediaDir = new File(archiveDir, "media");
        File externalRoot = getExternalFilesDir(null);
        photosDir = new File(externalRoot != null ? externalRoot : archiveDir, "MJLocalArchive");
        archiveFile = new File(archiveDir, "archive.json");
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadOptions();
        loadArchive();
        buildUi();
        configureWebView();
        renderArchive();
        if (autoStart) {
            mainHandler.postDelayed(this::startCrawl, 600);
        } else {
            setStatus("Ready. Auto crawl is off.");
        }
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

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(10), 0, 0);
        uiRoot.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button crawlButton = smallButton("Crawl");
        crawlButton.setOnClickListener(v -> startCrawl());
        toolbar.addView(crawlButton);

        Button optionsButton = smallButton("Options");
        optionsButton.setOnClickListener(v -> toggleOptions());
        toolbar.addView(optionsButton);

        Button sessionButton = smallButton("Session");
        sessionButton.setOnClickListener(v -> showSession(true));
        toolbar.addView(sessionButton);

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(210, 216, 225));
        statusText.setTextSize(13f);
        statusText.setPadding(0, dp(10), 0, dp(8));
        uiRoot.addView(statusText);

        optionsPanel = buildOptionsPanel();
        uiRoot.addView(optionsPanel);

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
        setStatus(autoStart ? "Ready. Crawling starts automatically." : "Ready. Auto crawl is off.");
    }

    private LinearLayout buildOptionsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(rounded(Color.rgb(24, 29, 38), dp(8)));
        panel.setVisibility(View.GONE);

        LinearLayout checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.VERTICAL);
        panel.addView(checks);

        autoStartCheck = optionCheck("Auto crawl on launch", autoStart);
        captureScreensCheck = optionCheck("Save WebView captures", captureScreens);
        stylesCheck = optionCheck("Crawl styles", includeStyles);
        imagesCheck = optionCheck("Crawl images", includeImages);
        videosCheck = optionCheck("Crawl video thumbnails", includeVideos);
        checks.addView(autoStartCheck);
        checks.addView(captureScreensCheck);
        checks.addView(stylesCheck);
        checks.addView(imagesCheck);
        checks.addView(videosCheck);

        View.OnClickListener optionChanged = v -> {
            syncOptionsFromUi();
            saveOptions();
            updateOptionsSummary();
        };
        autoStartCheck.setOnClickListener(optionChanged);
        captureScreensCheck.setOnClickListener(optionChanged);
        stylesCheck.setOnClickListener(optionChanged);
        imagesCheck.setOnClickListener(optionChanged);
        videosCheck.setOnClickListener(optionChanged);

        panel.addView(numberOptionRow("Scroll steps", () -> changeScrollSteps(-1), () -> changeScrollSteps(1), true));
        panel.addView(numberOptionRow("Max new per tab", () -> changeMaxPerTab(-10), () -> changeMaxPerTab(10), false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);

        Button cleanButton = smallButton("Clean");
        cleanButton.setOnClickListener(v -> compactArchive());
        actions.addView(cleanButton);

        Button resetDeletesButton = smallButton("Reset X");
        resetDeletesButton.setOnClickListener(v -> resetDeletedMemory());
        actions.addView(resetDeletesButton);

        Button clearButton = smallButton("Delete all");
        clearButton.setOnClickListener(v -> confirmDeleteAll());
        actions.addView(clearButton);

        panel.addView(actions);

        optionsSummaryText = new TextView(this);
        optionsSummaryText.setTextColor(Color.rgb(180, 187, 199));
        optionsSummaryText.setTextSize(12f);
        optionsSummaryText.setPadding(0, dp(8), 0, 0);
        panel.addView(optionsSummaryText);

        updateOptionsSummary();
        return panel;
    }

    private LinearLayout numberOptionRow(String label, Runnable minus, Runnable plus, boolean scrollRow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(210, 216, 225));
        labelView.setTextSize(13f);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button minusButton = smallButton("-");
        minusButton.setOnClickListener(v -> minus.run());
        row.addView(minusButton, new LinearLayout.LayoutParams(dp(42), dp(34)));

        TextView valueView = new TextView(this);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(13f);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));

        Button plusButton = smallButton("+");
        plusButton.setOnClickListener(v -> plus.run());
        row.addView(plusButton, new LinearLayout.LayoutParams(dp(42), dp(34)));

        if (scrollRow) {
            scrollValueText = valueView;
        } else {
            maxPerTabValueText = valueView;
        }
        updateNumberLabels();
        return row;
    }

    private CheckBox optionCheck(String text, boolean checked) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextColor(Color.rgb(222, 227, 235));
        checkBox.setTextSize(13f);
        checkBox.setChecked(checked);
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(79, 141, 247)));
        return checkBox;
    }

    private void loadOptions() {
        autoStart = prefs.getBoolean("autoStart", true);
        captureScreens = prefs.getBoolean("captureScreens", true);
        includeStyles = prefs.getBoolean("includeStyles", true);
        includeImages = prefs.getBoolean("includeImages", true);
        includeVideos = prefs.getBoolean("includeVideos", true);
        scrollSteps = clamp(prefs.getInt("scrollSteps", DEFAULT_SCROLL_STEPS), 1, 30);
        maxPerTab = clamp(prefs.getInt("maxPerTab", DEFAULT_MAX_PER_TAB), 10, 300);
    }

    private void syncOptionsFromUi() {
        if (autoStartCheck == null) {
            return;
        }
        autoStart = autoStartCheck.isChecked();
        captureScreens = captureScreensCheck.isChecked();
        includeStyles = stylesCheck.isChecked();
        includeImages = imagesCheck.isChecked();
        includeVideos = videosCheck.isChecked();
    }

    private void saveOptions() {
        prefs.edit()
                .putBoolean("autoStart", autoStart)
                .putBoolean("captureScreens", captureScreens)
                .putBoolean("includeStyles", includeStyles)
                .putBoolean("includeImages", includeImages)
                .putBoolean("includeVideos", includeVideos)
                .putInt("scrollSteps", scrollSteps)
                .putInt("maxPerTab", maxPerTab)
                .apply();
    }

    private void updateOptionsSummary() {
        updateNumberLabels();
        if (optionsSummaryText == null) {
            return;
        }
        String tabs = "";
        if (includeStyles) {
            tabs += "styles ";
        }
        if (includeImages) {
            tabs += "images ";
        }
        if (includeVideos) {
            tabs += "videos ";
        }
        if (tabs.length() == 0) {
            tabs = "none";
        }
        optionsSummaryText.setText("Tabs: " + tabs.trim()
                + "  |  capture " + (captureScreens ? "on" : "off")
                + "  |  scroll " + scrollSteps
                + "  |  max " + maxPerTab
                + "  |  deleted memory " + deletedKeys.size());
    }

    private void updateNumberLabels() {
        if (scrollValueText != null) {
            scrollValueText.setText(String.valueOf(scrollSteps));
        }
        if (maxPerTabValueText != null) {
            maxPerTabValueText.setText(String.valueOf(maxPerTab));
        }
    }

    private void changeScrollSteps(int delta) {
        scrollSteps = clamp(scrollSteps + delta, 1, 30);
        saveOptions();
        updateOptionsSummary();
    }

    private void changeMaxPerTab(int delta) {
        maxPerTab = clamp(maxPerTab + delta, 10, 300);
        saveOptions();
        updateOptionsSummary();
    }

    private boolean hasEnabledTab() {
        return includeStyles || includeImages || includeVideos;
    }

    private boolean isTabEnabled(int index) {
        if (index == 0) {
            return includeStyles;
        }
        if (index == 1) {
            return includeImages;
        }
        return includeVideos;
    }

    private int nextEnabledTab(int afterIndex) {
        int next = afterIndex + 1;
        while (next < URLS.length && !isTabEnabled(next)) {
            next++;
        }
        return next;
    }

    private void resetDeletedMemory() {
        int count = deletedKeys.size();
        deletedKeys.clear();
        saveArchive();
        updateOptionsSummary();
        setStatus("Reset " + count + " deleted keys. Deleted sources can be crawled again.");
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all local files?")
                .setMessage("Saved thumbnails will be removed from this app's private storage. Their source keys stay in delete memory, so they are not re-added until Reset X is used.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteAllLocalArchive())
                .show();
    }

    private void deleteAllLocalArchive() {
        crawling = false;
        if (webView != null) {
            webView.stopLoading();
        }
        for (ArchiveItem item : new ArrayList<>(items)) {
            deletedKeys.add(item.key);
            deleteFileQuietly(new File(item.localPath));
        }
        deletedKeys.addAll(downloadingKeys);
        downloadingKeys.clear();
        pendingDownloads = 0;
        items.clear();
        itemKeys.clear();
        deleteRecursive(photosDir);
        deleteRecursive(legacyMediaDir);
        photosDir.mkdirs();
        saveArchive();
        renderArchive();
        updateOptionsSummary();
        setStatus("Deleted all saved thumbnails from device storage.");
    }

    private void compactArchive() {
        int removed = 0;
        HashSet<String> seen = new HashSet<>();
        ArrayList<ArchiveItem> kept = new ArrayList<>();
        for (ArchiveItem item : items) {
            File file = new File(item.localPath);
            if (item.key == null || item.key.length() == 0 || seen.contains(item.key) || !file.exists() || file.length() == 0) {
                deleteFileQuietly(file);
                removed++;
                continue;
            }
            seen.add(item.key);
            kept.add(item);
        }
        items.clear();
        items.addAll(kept);
        itemKeys.clear();
        itemKeys.addAll(seen);
        saveArchive();
        renderArchive();
        setStatus("Cleaned archive. Removed " + removed + " missing or duplicate entries.");
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

    private void toggleOptions() {
        if (optionsPanel == null) {
            return;
        }
        optionsPanel.setVisibility(optionsPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void startCrawl() {
        if (crawling) {
            setStatus("Crawl already running.");
            return;
        }
        syncOptionsFromUi();
        saveOptions();
        if (!hasEnabledTab()) {
            setStatus("Turn on at least one crawl tab in Options.");
            return;
        }
        crawling = true;
        for (int i = 0; i < queuedByTab.length; i++) {
            queuedByTab[i] = 0;
        }
        targetIndex = nextEnabledTab(-1);
        scrollStep = 0;
        setStatus("Starting device crawl...");
        loadTarget();
    }

    private void loadTarget() {
        if (!crawling) {
            return;
        }
        targetIndex = nextEnabledTab(targetIndex - 1);
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

        if (scrollStep < scrollSteps) {
            scrollStep++;
            webView.evaluateJavascript(SCROLL_JS, value -> {
                setStatus("Crawling " + LABELS[targetIndex] + " " + scrollStep + "/" + scrollSteps);
                mainHandler.postDelayed(MainActivity.this::crawlStep, 1300);
            });
            return;
        }

        targetIndex = nextEnabledTab(targetIndex);
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

                if (looksBlocked(pageTitle, pageText)) {
                    setStatus(label + " is blocked by a challenge page. Open it normally in the WebView session.");
                    return;
                }

                if (urls != null) {
                    for (int i = 0; i < urls.length(); i++) {
                        if (queuedByTab[kindIndex] >= maxPerTab) {
                            break;
                        }
                        String rawUrl = urls.optString(i, "");
                        String sourceUrl = cleanSourceUrl(rawUrl);
                        if (sourceUrl == null) {
                            continue;
                        }
                        String key = keyForUrl(sourceUrl);
                        if (!reserveDownloadKey(key, kindIndex)) {
                            continue;
                        }
                        queued++;
                        submitDownload(kind, sourceUrl, key);
                    }
                }

                if (captureScreens) {
                    captureVisiblePage(kind, label, kindIndex);
                }

                if (queued > 0) {
                    setStatus(label + ": queued " + queued + " new thumbnails.");
                } else if (!captureScreens) {
                    setStatus(label + ": no downloadable thumbnails found.");
                }
            } catch (JSONException e) {
                setStatus("Could not read page data yet.");
            }
        });
    }

    private void captureVisiblePage(String kind, String label, int kindIndex) {
        if (queuedByTab[kindIndex] >= maxPerTab || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            return;
        }
        webView.evaluateJavascript(CLEAN_PAGE_JS, ignored -> {
            try {
                int width = webView.getWidth();
                int height = webView.getHeight();
                if (width <= 0 || height <= 0) {
                    return;
                }

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                Canvas canvas = new Canvas(bitmap);
                webView.draw(canvas);

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
                bitmap.recycle();
                byte[] bytes = output.toByteArray();
                String key = sha256(bytes);
                if (itemKeys.contains(key) || deletedKeys.contains(key) || downloadingKeys.contains(key)) {
                    return;
                }
                queuedByTab[kindIndex]++;
                saveCapturedBytes(kind, label, key, bytes);
            } catch (Exception ignoredCaptureError) {
                setStatus(label + ": WebView capture failed.");
            }
        });
    }

    private void saveCapturedBytes(String kind, String label, String key, byte[] bytes) throws IOException {
        if (!photosDir.exists() && !photosDir.mkdirs()) {
            throw new IOException("photo folder unavailable");
        }
        String id = kind + "-capture-" + key.substring(0, Math.min(16, key.length()));
        File target = new File(photosDir, id + ".jpg");
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(bytes);
        }

        ArchiveItem item = new ArchiveItem();
        item.id = id;
        item.kind = kind;
        item.sourceUrl = "webview-capture:" + kind;
        item.key = key;
        item.localPath = target.getAbsolutePath();
        item.savedAt = System.currentTimeMillis();
        itemKeys.add(item.key);
        items.add(item);
        saveArchive();
        renderArchive();
        setStatus(label + ": saved WebView capture.");
    }

    private boolean reserveDownloadKey(String key, int kindIndex) {
        if (key == null || key.length() == 0) {
            return false;
        }
        if (queuedByTab[kindIndex] >= maxPerTab) {
            return false;
        }
        if (itemKeys.contains(key) || deletedKeys.contains(key) || downloadingKeys.contains(key)) {
            return false;
        }
        downloadingKeys.add(key);
        queuedByTab[kindIndex]++;
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
        if (!photosDir.exists() && !photosDir.mkdirs()) {
            throw new IOException("storage unavailable");
        }

        String ext = extensionForUrl(sourceUrl);
        File target = new File(photosDir, kind + "-" + key.substring(0, Math.min(24, key.length())) + "." + ext);
        if (target.exists() && target.length() > 0) {
            return target;
        }

        File partial = new File(photosDir, target.getName() + ".part");
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
                + "  |  " + formatBytes(directorySize(photosDir) + directorySize(legacyMediaDir))
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
                || combined.contains("verify you are human")
                || combined.contains("checking your browser");
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
        return sha256(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
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

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private long directorySize(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                total += directorySize(child);
            }
        }
        return total;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
