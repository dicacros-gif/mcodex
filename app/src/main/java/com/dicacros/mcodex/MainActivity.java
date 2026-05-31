package com.dicacros.mcodex;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.ScaleGestureDetector;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "mj_local_archive_options";
    private static final int DEFAULT_SCROLL_STEPS = 10;
    private static final int DEFAULT_MAX_PER_TAB = 80;
    private static final int MAX_ASSET_BYTES = 25 * 1024 * 1024;
    private static final int GALLERY_PERMISSION_REQUEST = 71;
    private static final String MAIN_GALLERY_DEFAULT_KEY = "mainGalleryDefaultV3";
    private static final String[] KINDS = {"styles", "images", "videos"};
    private static final String[] LABELS = {"Styles", "Images", "Videos"};
    private static final int[] DISPLAY_KIND_ORDER = {1, 0, 2};
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
                    + "const isVideo=/video_top/.test(location.href);"
                    + "const urls=new Set();"
                    + "const addOne=function(raw){try{"
                    + "if(!raw)return;"
                    + "raw=String(raw).trim();"
                    + "if(!raw)return;"
                    + "raw=raw.replace(/&amp;/g,'&').replace(/\\\\\\//g,'/');"
                    + "if(raw.indexOf(',')>=0){raw.split(',').forEach(function(part){addOne(part.trim().split(/\\s+/)[0]);});}"
                    + "raw=raw.replace(/^url\\([\"']?/,'').replace(/[\"']?\\)$/,'');"
                    + "const u=new URL(raw,location.href);"
                    + "if(!/^https?:$/.test(u.protocol))return;"
                    + "const href=u.href;"
                    + "if(!/(midjourney|discordapp|discord|cloudfront)/i.test(href))return;"
                    + "if(!/\\.(webp|png|jpe?g)(\\?|#|$)/i.test(href))return;"
                    + "urls.add(href);"
                    + "}catch(e){}};"
                    + "const addUuid=function(raw){try{"
                    + "const text=String(raw||'');"
                    + "const matches=text.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ig)||[];"
                    + "matches.forEach(function(id){"
                    + "if(isVideo){addOne('https://cdn.midjourney.com/video/'+id+'/0_640_N.webp');}"
                    + "else{[0,1,2,3].forEach(function(n){addOne('https://cdn.midjourney.com/'+id+'/0_'+n+'_384_N.webp');addOne('https://cdn.midjourney.com/'+id+'/0_'+n+'.webp');});}"
                    + "});"
                    + "}catch(e){}};"
                    + "document.querySelectorAll('img').forEach(function(img){addOne(img.currentSrc||img.src);addOne(img.srcset);addOne(img.getAttribute('src'));addOne(img.getAttribute('data-src'));});"
                    + "document.querySelectorAll('source').forEach(function(source){addOne(source.srcset);addOne(source.src);});"
                    + "document.querySelectorAll('video').forEach(function(video){addOne(video.poster);addOne(video.currentSrc||video.src);});"
                    + "document.querySelectorAll('a').forEach(function(a){addOne(a.href);addUuid(a.href);});"
                    + "document.querySelectorAll('*').forEach(function(el){try{Array.from(el.getAttributeNames()).forEach(function(name){const value=el.getAttribute(name);addOne(value);addUuid(value);});}catch(e){}});"
                    + "document.querySelectorAll('[style]').forEach(function(el){const bg=getComputedStyle(el).backgroundImage;if(bg&&bg!=='none'){addOne(bg);}});"
                    + "try{performance.getEntriesByType('resource').forEach(function(e){addOne(e.name);addUuid(e.name);});}catch(e){}"
                    + "const html=document.documentElement?document.documentElement.innerHTML:'';"
                    + "addUuid(html);"
                    + "(html.match(/https?:\\\\?\\/\\\\?\\/[^\\\"'<>\\s]+/g)||[]).forEach(function(u){addOne(u);});"
                    + "return {urls:Array.from(urls),title:document.title||'',href:location.href,text:(document.body&&document.body.innerText?document.body.innerText.slice(0,1200):'')};"
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
                    + "if(t==='create'||t==='updates'||t==='update'||t==='menu'||t==='\\ud06c\\ub9ac\\uc5d0\\uc774\\ud2b8'||t==='\\uc5c5\\ub370\\uc774\\ud2b8'){hide(el);}"
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
    private File galleryFallbackDir;
    private File archiveFile;
    private SharedPreferences prefs;
    private WebView webView;
    private LinearLayout uiRoot;
    private LinearLayout optionsPanel;
    private LinearLayout sessionBar;
    private LinearLayout kindTabs;
    private FrameLayout previewOverlay;
    private ZoomImageView previewImage;
    private GridLayout grid;
    private TextView statusText;
    private TextView countText;
    private TextView optionsSummaryText;
    private TextView scrollValueText;
    private TextView maxPerTabValueText;
    private TextView pageWaitValueText;
    private TextView scrollPauseValueText;
    private CheckBox autoStartCheck;
    private CheckBox downloadUrlsCheck;
    private CheckBox captureScreensCheck;
    private CheckBox captureOnLoadCheck;
    private CheckBox captureEachScrollCheck;
    private CheckBox showWebViewWhileCrawlingCheck;
    private CheckBox pixelCopyCaptureCheck;
    private CheckBox drawCaptureCheck;
    private CheckBox fullPageCaptureCheck;
    private CheckBox stripPageChromeCheck;
    private CheckBox clearCacheBeforeCrawlCheck;
    private String userAgent;
    private int targetIndex;
    private int scrollStep;
    private int pendingDownloads;
    private int scrollSteps;
    private int maxPerTab;
    private int pageWaitMs;
    private int scrollPauseMs;
    private final int[] queuedByTab = new int[KINDS.length];
    private final Button[] kindTabButtons = new Button[KINDS.length];
    private boolean autoStart;
    private boolean downloadUrls;
    private boolean captureScreens;
    private boolean captureOnLoad;
    private boolean captureEachScroll;
    private boolean showWebViewWhileCrawling;
    private boolean pixelCopyCapture;
    private boolean drawCapture;
    private boolean fullPageCapture;
    private boolean stripPageChrome;
    private boolean clearCacheBeforeCrawl;
    private boolean includeStyles;
    private boolean includeImages;
    private boolean includeVideos;
    private String activeKind;
    private boolean crawling;
    private long externalSessionStartedAt;
    private boolean pendingExternalImport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        archiveDir = new File(getFilesDir(), "midjourney_archive");
        legacyMediaDir = new File(archiveDir, "media");
        File externalRoot = getExternalFilesDir(null);
        photosDir = new File(externalRoot != null ? externalRoot : archiveDir, "MJLocalArchive");
        galleryFallbackDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MJLocalArchive");
        archiveFile = new File(archiveDir, "archive.json");
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadOptions();
        migrateMainGalleryDefault();
        loadArchive();
        buildUi();
        configureWebView();
        ensureGalleryReadPermission();
        importGalleryFolder();
        renderArchive();
        if (autoStart) {
            mainHandler.postDelayed(this::startCrawl, 600);
        } else {
            setStatus("Saved gallery is ready. Choose a tab, then use Explorer or Crawl.");
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GALLERY_PERMISSION_REQUEST) {
            if (pendingExternalImport) {
                pendingExternalImport = false;
                if (hasGalleryReadPermission()) {
                    int count = importRecentExternalImages();
                    renderArchive();
                    setStatus("Imported " + Math.max(0, count) + " recent screenshots/downloads for " + displayKind(activeKind) + ".");
                } else {
                    renderArchive();
                    setStatus("Gallery permission was not granted. Import Shots cannot read screenshots.");
                }
                return;
            }
            int count = importGalleryFolder();
            renderArchive();
            if (hasGalleryReadPermission()) {
                setStatus("Gallery permission ready. Imported " + count + " saved images.");
            } else {
                setStatus("Gallery permission was not granted. New captures still save inside the app album.");
            }
        }
    }

    private void ensureGalleryReadPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasGalleryReadPermission()) {
            return;
        }
        requestPermissions(galleryReadPermissions(), GALLERY_PERMISSION_REQUEST);
    }

    private boolean hasGalleryReadPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String permission : galleryReadPermissions()) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private String[] galleryReadPermissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            };
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
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

        Button explorerButton = smallButton("Explorer");
        explorerButton.setOnClickListener(v -> openExplorerSession());
        toolbar.addView(explorerButton);

        kindTabs = buildKindTabs();
        uiRoot.addView(kindTabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

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
        Button saveViewButton = smallButton("Save View");
        saveViewButton.setOnClickListener(v -> saveVisibleSession());
        sessionBar.addView(saveViewButton);
        Button sessionCrawlButton = smallButton("Crawl");
        sessionCrawlButton.setOnClickListener(v -> startCrawl());
        sessionBar.addView(sessionCrawlButton);
        TextView sessionText = new TextView(this);
        sessionText.setText("Midjourney Explorer");
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

        previewOverlay = buildPreviewOverlay();
        frame.addView(previewOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(frame);
        setStatus(autoStart ? "Ready. Crawling starts automatically." : "Ready. Auto crawl is off.");
    }

    private FrameLayout buildPreviewOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setVisibility(View.GONE);

        previewImage = new ZoomImageView(this);
        previewImage.setBackgroundColor(Color.BLACK);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        overlay.addView(previewImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        Button close = smallButton("X");
        close.setTextSize(16f);
        close.setOnClickListener(v -> hidePreview());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(52), dp(44), Gravity.TOP | Gravity.RIGHT);
        closeParams.setMargins(0, dp(14), dp(14), 0);
        overlay.addView(close, closeParams);
        return overlay;
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
        downloadUrlsCheck = optionCheck("Method: download image URLs", downloadUrls);
        captureScreensCheck = optionCheck("Method: save WebView captures", captureScreens);
        captureOnLoadCheck = optionCheck("Capture after tab loads", captureOnLoad);
        captureEachScrollCheck = optionCheck("Capture each scroll", captureEachScroll);
        showWebViewWhileCrawlingCheck = optionCheck("Show WebView while crawling", showWebViewWhileCrawling);
        pixelCopyCaptureCheck = optionCheck("Capture method: PixelCopy screen", pixelCopyCapture);
        drawCaptureCheck = optionCheck("Capture method: WebView draw", drawCapture);
        fullPageCaptureCheck = optionCheck("Capture method: full page draw", fullPageCapture);
        stripPageChromeCheck = optionCheck("Hide page buttons before capture", stripPageChrome);
        clearCacheBeforeCrawlCheck = optionCheck("Clear WebView cache before crawl", clearCacheBeforeCrawl);
        checks.addView(autoStartCheck);
        checks.addView(downloadUrlsCheck);
        checks.addView(captureScreensCheck);
        checks.addView(captureOnLoadCheck);
        checks.addView(captureEachScrollCheck);
        checks.addView(showWebViewWhileCrawlingCheck);
        checks.addView(pixelCopyCaptureCheck);
        checks.addView(drawCaptureCheck);
        checks.addView(fullPageCaptureCheck);
        checks.addView(stripPageChromeCheck);
        checks.addView(clearCacheBeforeCrawlCheck);

        View.OnClickListener optionChanged = v -> {
            syncOptionsFromUi();
            saveOptions();
            updateOptionsSummary();
        };
        autoStartCheck.setOnClickListener(optionChanged);
        downloadUrlsCheck.setOnClickListener(optionChanged);
        captureScreensCheck.setOnClickListener(optionChanged);
        captureOnLoadCheck.setOnClickListener(optionChanged);
        captureEachScrollCheck.setOnClickListener(optionChanged);
        showWebViewWhileCrawlingCheck.setOnClickListener(optionChanged);
        pixelCopyCaptureCheck.setOnClickListener(optionChanged);
        drawCaptureCheck.setOnClickListener(optionChanged);
        fullPageCaptureCheck.setOnClickListener(optionChanged);
        stripPageChromeCheck.setOnClickListener(optionChanged);
        clearCacheBeforeCrawlCheck.setOnClickListener(optionChanged);

        panel.addView(numberOptionRow("Scroll steps", () -> changeScrollSteps(-1), () -> changeScrollSteps(1), 0));
        panel.addView(numberOptionRow("Max new per tab", () -> changeMaxPerTab(-10), () -> changeMaxPerTab(10), 1));
        panel.addView(numberOptionRow("Page wait sec", () -> changePageWait(-500), () -> changePageWait(500), 2));
        panel.addView(numberOptionRow("Scroll pause sec", () -> changeScrollPause(-250), () -> changeScrollPause(250), 3));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);

        Button cleanButton = smallButton("Clean");
        cleanButton.setOnClickListener(v -> compactArchive());
        actions.addView(cleanButton);

        Button importButton = smallButton("Import");
        importButton.setOnClickListener(v -> {
            ensureGalleryReadPermission();
            int count = importGalleryFolder();
            renderArchive();
            setStatus("Imported " + count + " images from Gallery/MJLocalArchive.");
        });
        actions.addView(importButton);

        Button resetDeletesButton = smallButton("Reset X");
        resetDeletesButton.setOnClickListener(v -> resetDeletedMemory());
        actions.addView(resetDeletesButton);

        Button clearButton = smallButton("Delete all");
        clearButton.setOnClickListener(v -> confirmDeleteAll());
        actions.addView(clearButton);

        panel.addView(actions);

        LinearLayout externalActions = new LinearLayout(this);
        externalActions.setOrientation(LinearLayout.HORIZONTAL);
        externalActions.setGravity(Gravity.CENTER_VERTICAL);
        externalActions.setPadding(0, dp(8), 0, 0);

        Button midjourneyAppButton = smallButton("MJ App");
        midjourneyAppButton.setOnClickListener(v -> openMidjourneyOutside(true));
        externalActions.addView(midjourneyAppButton);

        Button browserButton = smallButton("Browser");
        browserButton.setOnClickListener(v -> openMidjourneyOutside(false));
        externalActions.addView(browserButton);

        Button importShotsButton = smallButton("Import Shots");
        importShotsButton.setOnClickListener(v -> {
            int count = importRecentExternalImages();
            renderArchive();
            if (count >= 0) {
                setStatus("Imported " + count + " recent screenshots/downloads for " + displayKind(activeKind) + ".");
            }
        });
        externalActions.addView(importShotsButton);

        panel.addView(externalActions);

        optionsSummaryText = new TextView(this);
        optionsSummaryText.setTextColor(Color.rgb(180, 187, 199));
        optionsSummaryText.setTextSize(12f);
        optionsSummaryText.setPadding(0, dp(8), 0, 0);
        panel.addView(optionsSummaryText);

        updateOptionsSummary();
        return panel;
    }

    private LinearLayout buildKindTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, dp(10), 0, 0);

        for (int index : DISPLAY_KIND_ORDER) {
            Button button = new Button(this);
            button.setText(LABELS[index]);
            button.setAllCaps(false);
            button.setTextSize(13f);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setPadding(dp(8), 0, dp(8), 0);
            button.setOnClickListener(v -> {
                activeKind = KINDS[index];
                saveOptions();
                updateKindTabs();
                importGalleryFolder();
                renderArchive();
            });
            kindTabButtons[index] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            params.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(button, params);
        }

        updateKindTabs();
        return tabs;
    }

    private void updateKindTabs() {
        for (int i = 0; i < kindTabButtons.length; i++) {
            Button button = kindTabButtons[i];
            if (button == null) {
                continue;
            }
            boolean selected = KINDS[i].equals(activeKind);
            button.setTextColor(selected ? Color.WHITE : Color.rgb(188, 196, 209));
            button.setBackground(rounded(selected ? Color.rgb(61, 105, 178) : Color.rgb(31, 36, 47), dp(8)));
        }
    }

    private LinearLayout numberOptionRow(String label, Runnable minus, Runnable plus, int valueKind) {
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

        if (valueKind == 0) {
            scrollValueText = valueView;
        } else if (valueKind == 1) {
            maxPerTabValueText = valueView;
        } else if (valueKind == 2) {
            pageWaitValueText = valueView;
        } else {
            scrollPauseValueText = valueView;
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
        autoStart = prefs.getBoolean("autoStart", false);
        downloadUrls = prefs.getBoolean("downloadUrls", true);
        captureScreens = prefs.getBoolean("captureScreens", true);
        captureOnLoad = prefs.getBoolean("captureOnLoad", true);
        captureEachScroll = prefs.getBoolean("captureEachScroll", true);
        showWebViewWhileCrawling = prefs.getBoolean("showWebViewWhileCrawling", true);
        pixelCopyCapture = prefs.getBoolean("pixelCopyCapture", true);
        drawCapture = prefs.getBoolean("drawCapture", true);
        fullPageCapture = prefs.getBoolean("fullPageCapture", false);
        stripPageChrome = prefs.getBoolean("stripPageChrome", true);
        clearCacheBeforeCrawl = prefs.getBoolean("clearCacheBeforeCrawl", false);
        includeStyles = prefs.getBoolean("includeStyles", true);
        includeImages = prefs.getBoolean("includeImages", true);
        includeVideos = prefs.getBoolean("includeVideos", true);
        scrollSteps = clamp(prefs.getInt("scrollSteps", DEFAULT_SCROLL_STEPS), 1, 30);
        maxPerTab = clamp(prefs.getInt("maxPerTab", DEFAULT_MAX_PER_TAB), 10, 300);
        pageWaitMs = clamp(prefs.getInt("pageWaitMs", 2200), 800, 8000);
        scrollPauseMs = clamp(prefs.getInt("scrollPauseMs", 1300), 500, 5000);
        externalSessionStartedAt = prefs.getLong("externalSessionStartedAt", 0L);
        activeKind = prefs.getString("activeKind", "images");
        if (!"images".equals(activeKind) && !"styles".equals(activeKind) && !"videos".equals(activeKind)) {
            activeKind = "images";
        }
    }

    private void migrateMainGalleryDefault() {
        if (prefs.getBoolean(MAIN_GALLERY_DEFAULT_KEY, false)) {
            return;
        }
        autoStart = false;
        prefs.edit()
                .putBoolean("autoStart", false)
                .putBoolean(MAIN_GALLERY_DEFAULT_KEY, true)
                .apply();
    }

    private void syncOptionsFromUi() {
        if (autoStartCheck == null) {
            return;
        }
        autoStart = autoStartCheck.isChecked();
        downloadUrls = downloadUrlsCheck.isChecked();
        captureScreens = captureScreensCheck.isChecked();
        captureOnLoad = captureOnLoadCheck.isChecked();
        captureEachScroll = captureEachScrollCheck.isChecked();
        showWebViewWhileCrawling = showWebViewWhileCrawlingCheck.isChecked();
        pixelCopyCapture = pixelCopyCaptureCheck.isChecked();
        drawCapture = drawCaptureCheck.isChecked();
        fullPageCapture = fullPageCaptureCheck.isChecked();
        stripPageChrome = stripPageChromeCheck.isChecked();
        clearCacheBeforeCrawl = clearCacheBeforeCrawlCheck.isChecked();
    }

    private void saveOptions() {
        prefs.edit()
                .putBoolean("autoStart", autoStart)
                .putBoolean("downloadUrls", downloadUrls)
                .putBoolean("captureScreens", captureScreens)
                .putBoolean("captureOnLoad", captureOnLoad)
                .putBoolean("captureEachScroll", captureEachScroll)
                .putBoolean("showWebViewWhileCrawling", showWebViewWhileCrawling)
                .putBoolean("pixelCopyCapture", pixelCopyCapture)
                .putBoolean("drawCapture", drawCapture)
                .putBoolean("fullPageCapture", fullPageCapture)
                .putBoolean("stripPageChrome", stripPageChrome)
                .putBoolean("clearCacheBeforeCrawl", clearCacheBeforeCrawl)
                .putBoolean("includeStyles", includeStyles)
                .putBoolean("includeImages", includeImages)
                .putBoolean("includeVideos", includeVideos)
                .putInt("scrollSteps", scrollSteps)
                .putInt("maxPerTab", maxPerTab)
                .putInt("pageWaitMs", pageWaitMs)
                .putInt("scrollPauseMs", scrollPauseMs)
                .putLong("externalSessionStartedAt", externalSessionStartedAt)
                .putString("activeKind", activeKind)
                .apply();
    }

    private void updateOptionsSummary() {
        updateNumberLabels();
        if (optionsSummaryText == null) {
            return;
        }
        String methods = "";
        if (downloadUrls) {
            methods += "URL ";
        }
        if (captureScreens && captureOnLoad) {
            methods += "load-capture ";
        }
        if (captureScreens && captureEachScroll) {
            methods += "scroll-capture ";
        }
        if (captureScreens && fullPageCapture) {
            methods += "full-page ";
        }
        if (methods.length() == 0) {
            methods = "none";
        }
        optionsSummaryText.setText("Active tab: " + displayKind(activeKind)
                + "  |  methods " + methods.trim()
                + "  |  screen " + (showWebViewWhileCrawling ? "visible" : "hidden")
                + "  |  scroll " + scrollSteps
                + "  |  max " + maxPerTab
                + "  |  wait " + formatSeconds(pageWaitMs)
                + "/" + formatSeconds(scrollPauseMs)
                + "  |  external " + (externalSessionStartedAt > 0 ? "ready" : "not opened")
                + "  |  deleted memory " + deletedKeys.size());
    }

    private void updateNumberLabels() {
        if (scrollValueText != null) {
            scrollValueText.setText(String.valueOf(scrollSteps));
        }
        if (maxPerTabValueText != null) {
            maxPerTabValueText.setText(String.valueOf(maxPerTab));
        }
        if (pageWaitValueText != null) {
            pageWaitValueText.setText(formatSeconds(pageWaitMs));
        }
        if (scrollPauseValueText != null) {
            scrollPauseValueText.setText(formatSeconds(scrollPauseMs));
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

    private void changePageWait(int delta) {
        pageWaitMs = clamp(pageWaitMs + delta, 800, 8000);
        saveOptions();
        updateOptionsSummary();
    }

    private void changeScrollPause(int delta) {
        scrollPauseMs = clamp(scrollPauseMs + delta, 500, 5000);
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

    private int indexForKind(String kind) {
        for (int i = 0; i < KINDS.length; i++) {
            if (KINDS[i].equals(kind)) {
                return i;
            }
        }
        return -1;
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
            deleteStoredImage(item);
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
        HashSet<String> seenRefs = new HashSet<>();
        ArrayList<ArchiveItem> kept = new ArrayList<>();
        for (ArchiveItem item : items) {
            String ref = storedReference(item);
            if (item.key == null || item.key.length() == 0 || seen.contains(item.key) || (ref.length() > 0 && seenRefs.contains(ref))) {
                removed++;
                continue;
            }
            if (!storedImageExists(item) && !isPermissionPendingMediaItem(item)) {
                deleteStoredImage(item);
                removed++;
                continue;
            }
            seen.add(item.key);
            if (ref.length() > 0) {
                seenRefs.add(ref);
            }
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

    private String storedReference(ArchiveItem item) {
        if (item == null) {
            return "";
        }
        if (item.localUri != null && item.localUri.length() > 0) {
            return "uri:" + item.localUri;
        }
        if (item.localPath != null && item.localPath.length() > 0) {
            return "path:" + item.localPath;
        }
        return "";
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
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36";
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
                mainHandler.postDelayed(() -> {
                    if (captureScreens && captureOnLoad) {
                        captureVisiblePage(KINDS[targetIndex], LABELS[targetIndex], targetIndex);
                    }
                    mainHandler.postDelayed(MainActivity.this::crawlStep, 350);
                }, pageWaitMs);
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

    private void openExplorerSession() {
        syncOptionsFromUi();
        setOnlyActiveKindEnabled();
        saveOptions();
        int index = indexForKind(activeKind);
        if (index < 0) {
            index = 1;
        }
        showSession(true);
        setStatus("Opening " + LABELS[index] + " Explorer...");
        webView.stopLoading();
        webView.clearHistory();
        if (clearCacheBeforeCrawl) {
            webView.clearCache(false);
        }
        webView.loadUrl(URLS[index]);
    }

    private void saveVisibleSession() {
        syncOptionsFromUi();
        saveOptions();
        int index = indexForKind(activeKind);
        if (index < 0) {
            index = 1;
        }
        queuedByTab[index] = 0;
        setStatus("Saving the visible " + LABELS[index] + " Explorer screen...");
        sessionBar.setVisibility(View.GONE);
        int finalIndex = index;
        mainHandler.postDelayed(() -> {
            captureVisiblePage(KINDS[finalIndex], LABELS[finalIndex], finalIndex);
            mainHandler.postDelayed(() -> sessionBar.setVisibility(View.VISIBLE), 900);
        }, 120);
    }

    private void openMidjourneyOutside(boolean preferApp) {
        syncOptionsFromUi();
        saveOptions();
        int index = indexForKind(activeKind);
        if (index < 0) {
            index = 1;
        }

        rememberExternalLaunch();
        Uri uri = Uri.parse(URLS[index]);
        Intent intent = externalViewIntent(uri);
        String packageName = preferApp ? findMidjourneyHandlerPackage(intent) : findBrowserHandlerPackage(intent);
        if (packageName != null) {
            intent.setPackage(packageName);
        }

        try {
            startActivity(intent);
            setStatus((preferApp ? "Opened Midjourney app/browser. " : "Opened external browser. ")
                    + "Take screenshots or save images, then return and tap Import Shots.");
        } catch (Exception firstError) {
            try {
                intent.setPackage(null);
                startActivity(Intent.createChooser(intent, "Open Midjourney"));
                setStatus("Opened chooser. After screenshots/downloads, return and tap Import Shots.");
            } catch (Exception secondError) {
                setStatus("No external app or browser could open Midjourney.");
            }
        }
    }

    private Intent externalViewIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void rememberExternalLaunch() {
        externalSessionStartedAt = System.currentTimeMillis();
        prefs.edit().putLong("externalSessionStartedAt", externalSessionStartedAt).apply();
        updateOptionsSummary();
    }

    private String findMidjourneyHandlerPackage(Intent baseIntent) {
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(baseIntent, 0);
        for (ResolveInfo info : handlers) {
            String label = resolveLabel(info);
            String packageName = info.activityInfo == null ? "" : info.activityInfo.packageName;
            String combined = (label + " " + packageName).toLowerCase(Locale.US);
            if (combined.contains("midjourney")) {
                return packageName;
            }
        }
        return null;
    }

    private String findBrowserHandlerPackage(Intent baseIntent) {
        String[] preferred = {
                "com.android.chrome",
                "com.sec.android.app.sbrowser",
                "org.mozilla.firefox",
                "com.brave.browser",
                "com.microsoft.emmx",
                "com.nhn.android.search"
        };
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(baseIntent, 0);
        for (String candidate : preferred) {
            for (ResolveInfo info : handlers) {
                String packageName = info.activityInfo == null ? "" : info.activityInfo.packageName;
                if (candidate.equals(packageName)) {
                    return packageName;
                }
            }
        }
        for (ResolveInfo info : handlers) {
            String label = resolveLabel(info);
            String packageName = info.activityInfo == null ? "" : info.activityInfo.packageName;
            String combined = (label + " " + packageName).toLowerCase(Locale.US);
            if (!getPackageName().equals(packageName) && !combined.contains("midjourney")) {
                return packageName;
            }
        }
        return null;
    }

    private String resolveLabel(ResolveInfo info) {
        try {
            CharSequence label = info.loadLabel(getPackageManager());
            return label == null ? "" : label.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void showCrawlerWebView() {
        uiRoot.setVisibility(View.GONE);
        webView.setAlpha(1f);
        webView.bringToFront();
        sessionBar.setVisibility(View.GONE);
    }

    private void showGalleryView() {
        webView.setAlpha(0.01f);
        uiRoot.setVisibility(View.VISIBLE);
        uiRoot.bringToFront();
        sessionBar.setVisibility(View.GONE);
        if (previewOverlay != null && previewOverlay.getVisibility() == View.VISIBLE) {
            previewOverlay.bringToFront();
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
        setOnlyActiveKindEnabled();
        saveOptions();
        if (!downloadUrls && !(captureScreens && (captureOnLoad || captureEachScroll))) {
            setStatus("Turn on at least one method in Options.");
            return;
        }
        if (captureScreens && !pixelCopyCapture && !drawCapture && !fullPageCapture) {
            setStatus("Turn on at least one capture method in Options.");
            return;
        }
        if (!hasEnabledTab()) {
            setStatus("Select Images, Styles, or Videos.");
            return;
        }
        if (clearCacheBeforeCrawl) {
            webView.clearCache(false);
        }
        crawling = true;
        if (captureScreens && showWebViewWhileCrawling) {
            showCrawlerWebView();
        }
        for (int i = 0; i < queuedByTab.length; i++) {
            queuedByTab[i] = 0;
        }
        targetIndex = nextEnabledTab(-1);
        scrollStep = 0;
        setStatus("Starting device crawl...");
        loadTarget();
    }

    private void setOnlyActiveKindEnabled() {
        includeStyles = "styles".equals(activeKind);
        includeImages = "images".equals(activeKind);
        includeVideos = "videos".equals(activeKind);
        updateOptionsSummary();
    }

    private void loadTarget() {
        if (!crawling) {
            return;
        }
        targetIndex = nextEnabledTab(targetIndex - 1);
        if (targetIndex >= URLS.length) {
            crawling = false;
            importGalleryFolder();
            saveArchive();
            showGalleryView();
            setStatus("Crawl complete. " + items.size() + " saved, " + pendingDownloads + " downloads still finishing.");
            return;
        }
        setStatus("Opening " + LABELS[targetIndex] + " tab...");
        webView.stopLoading();
        webView.loadUrl(URLS[targetIndex]);
    }

    private void crawlStep() {
        if (!crawling || targetIndex >= URLS.length) {
            return;
        }

        extractVisibleUrls(captureScreens && captureEachScroll);

        if (scrollStep < scrollSteps) {
            scrollStep++;
            webView.evaluateJavascript(SCROLL_JS, value -> {
                setStatus("Crawling " + LABELS[targetIndex] + " " + scrollStep + "/" + scrollSteps);
                mainHandler.postDelayed(MainActivity.this::crawlStep, scrollPauseMs);
            });
            return;
        }

        targetIndex = nextEnabledTab(targetIndex);
        scrollStep = 0;
        mainHandler.postDelayed(this::loadTarget, 700);
    }

    private void extractVisibleUrls(boolean allowCapture) {
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

                if (downloadUrls && urls != null) {
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

                if (allowCapture) {
                    captureVisiblePage(kind, label, kindIndex);
                }

                if (queued > 0) {
                    setStatus(label + ": queued " + queued + " new thumbnails.");
                } else if (!allowCapture && !downloadUrls) {
                    setStatus(label + ": no active method for this step.");
                } else if (!allowCapture) {
                    setStatus(label + ": no downloadable thumbnails found. Try capture methods in Options.");
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
        Runnable capture = () -> {
            if (pixelCopyCapture && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && webView.getAlpha() >= 0.9f) {
                if (requestPixelCopyCapture(kind, label, kindIndex)) {
                    return;
                }
            }
            fallbackCapture(kind, label, kindIndex);
        };
        if (stripPageChrome) {
            webView.evaluateJavascript(CLEAN_PAGE_JS, ignored -> mainHandler.postDelayed(capture, 180));
        } else {
            capture.run();
        }
    }

    private boolean requestPixelCopyCapture(String kind, String label, int kindIndex) {
        try {
            int[] location = new int[2];
            webView.getLocationInWindow(location);
            Rect rect = new Rect(location[0], location[1], location[0] + webView.getWidth(), location[1] + webView.getHeight());
            Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
            PixelCopy.request(getWindow(), rect, bitmap, result -> {
                if (result == PixelCopy.SUCCESS) {
                    saveCapturedBitmap(kind, label, kindIndex, bitmap, "pixel");
                } else {
                    bitmap.recycle();
                    fallbackCapture(kind, label, kindIndex);
                }
            }, mainHandler);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void fallbackCapture(String kind, String label, int kindIndex) {
        if (queuedByTab[kindIndex] >= maxPerTab) {
            return;
        }
        if (drawCapture) {
            Bitmap visible = drawVisibleWebViewBitmap();
            if (visible != null && saveCapturedBitmap(kind, label, kindIndex, visible, "draw")) {
                return;
            }
        }
        if (fullPageCapture) {
            Bitmap full = drawFullPageBitmap();
            if (full != null) {
                saveCapturedBitmap(kind, label, kindIndex, full, "full");
            }
        }
    }

    private Bitmap drawVisibleWebViewBitmap() {
        try {
            int width = webView.getWidth();
            int height = webView.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap drawFullPageBitmap() {
        try {
            Picture picture = webView.capturePicture();
            if (picture == null || picture.getWidth() <= 0 || picture.getHeight() <= 0) {
                return null;
            }
            int width = Math.min(webView.getWidth() > 0 ? webView.getWidth() : picture.getWidth(), 1440);
            int height = Math.min(Math.max(webView.getHeight(), picture.getHeight() * width / Math.max(1, picture.getWidth())), 6000);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            float scale = width / (float) picture.getWidth();
            canvas.scale(scale, scale);
            picture.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean saveCapturedBitmap(String kind, String label, int kindIndex, Bitmap bitmap, String method) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
            bitmap.recycle();
            byte[] bytes = output.toByteArray();
            String key = sha256(bytes);
            if (itemKeys.contains(key) || deletedKeys.contains(key) || downloadingKeys.contains(key)) {
                return false;
            }
            queuedByTab[kindIndex]++;
            saveImageBytes(kind, label, key, bytes, "jpg", "image/jpeg", "webview-" + method);
            return true;
        } catch (Exception ignoredCaptureError) {
            setStatus(label + ": capture failed.");
            return false;
        }
    }

    private void saveImageBytes(String kind, String label, String key, byte[] bytes, String ext, String mimeType, String sourcePrefix) throws IOException {
        StoredImage stored = saveBytesToGallery(kind, key, bytes, ext, mimeType);
        String id = kind + "-" + sourcePrefix + "-" + key.substring(0, Math.min(16, key.length()));

        ArchiveItem item = new ArchiveItem();
        item.id = id;
        item.kind = kind;
        item.sourceUrl = sourcePrefix + ":" + kind;
        item.key = key;
        item.localPath = stored.path;
        item.localUri = stored.uri;
        item.savedAt = System.currentTimeMillis();
        itemKeys.add(item.key);
        items.add(item);
        saveArchive();
        renderArchive();
        setStatus(label + ": saved to Gallery/MJLocalArchive.");
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
                StoredImage stored = downloadToStorage(kind, sourceUrl, key);
                ArchiveItem item = new ArchiveItem();
                item.id = kind + "-" + key.substring(0, Math.min(16, key.length()));
                item.kind = kind;
                item.sourceUrl = sourceUrl;
                item.key = key;
                item.localPath = stored.path;
                item.localUri = stored.uri;
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
            deleteStoredImage(item);
            updateCounts();
            return;
        }

        itemKeys.add(item.key);
        items.add(item);
        saveArchive();
        renderArchive();
        setStatus("Saved " + item.kind + " thumbnail. " + pendingDownloads + " downloads pending.");
    }

    private StoredImage downloadToStorage(String kind, String sourceUrl, String key) throws IOException {
        String ext = extensionForUrl(sourceUrl);
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

            ByteArrayOutputStream output = new ByteArrayOutputStream(length > 0 ? length : 64 * 1024);
            try (InputStream input = connection.getInputStream()) {
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
            return saveBytesToGallery(kind, key, output.toByteArray(), ext, mimeTypeForExt(ext));
        } finally {
            connection.disconnect();
        }
    }

    private StoredImage saveBytesToGallery(String kind, String key, byte[] bytes, String ext, String mimeType) throws IOException {
        String safeExt = ("png".equals(ext) || "webp".equals(ext) || "jpg".equals(ext)) ? ext : "jpg";
        String fileName = kind + "-" + key.substring(0, Math.min(24, key.length())) + "." + safeExt;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MJLocalArchive");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream output = resolver.openOutputStream(uri)) {
                    if (output == null) {
                        throw new IOException("gallery output unavailable");
                    }
                    output.write(bytes);
                } catch (IOException e) {
                    resolver.delete(uri, null, null);
                    throw e;
                }

                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return new StoredImage("", uri.toString());
            }
        }

        File targetDir = galleryFallbackDir;
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            targetDir = photosDir;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("gallery folder unavailable");
        }
        File target = new File(targetDir, fileName);
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(bytes);
        }
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)));
        return new StoredImage(target.getAbsolutePath(), Uri.fromFile(target).toString());
    }

    private void deleteItem(ArchiveItem item) {
        deletedKeys.add(item.key);
        itemKeys.remove(item.key);
        items.remove(item);
        deleteStoredImage(item);
        saveArchive();
        renderArchive();
        setStatus("Deleted one saved thumbnail from device storage.");
    }

    private void renderArchive() {
        if (grid == null) {
            return;
        }
        updateKindTabs();
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

        String lastDay = "";
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.KOREA);
        int visibleCount = 0;

        for (ArchiveItem item : ordered) {
            if (!activeKind.equals(item.kind)) {
                continue;
            }
            visibleCount++;

            String day = dayFormat.format(new Date(item.savedAt));
            if (!day.equals(lastDay)) {
                lastDay = day;
                TextView dayHeader = new TextView(this);
                dayHeader.setText("TOP DAY  " + day);
                dayHeader.setTextColor(Color.WHITE);
                dayHeader.setTextSize(16f);
                dayHeader.setTypeface(Typeface.DEFAULT_BOLD);
                dayHeader.setPadding(dp(5), dp(16), dp(5), dp(8));
                GridLayout.LayoutParams headerParams = new GridLayout.LayoutParams();
                headerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                headerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                headerParams.columnSpec = GridLayout.spec(0, columns);
                dayHeader.setLayoutParams(headerParams);
                grid.addView(dayHeader);
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(rounded(Color.rgb(26, 30, 38), dp(8)));
            card.setPadding(dp(6), dp(6), dp(6), dp(6));
            card.setOnClickListener(v -> showPreview(item));

            ImageView imageView = new ImageView(this);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackgroundColor(Color.rgb(10, 12, 16));
            Bitmap bitmap = decodeThumbnail(item, cardWidth, imageHeight);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageURI(uriForItem(item));
            }
            card.addView(imageView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    imageHeight
            ));
            imageView.setOnClickListener(v -> showPreview(item));

            LinearLayout meta = new LinearLayout(this);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            meta.setPadding(0, dp(6), 0, 0);

            TextView kindText = new TextView(this);
            kindText.setText(displayKind(item.kind) + "  " + timeFormat.format(new Date(item.savedAt)));
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

        if (visibleCount == 0) {
            TextView empty = new TextView(this);
            empty.setText(displayKind(activeKind) + " saved items are empty. Use Explorer/Crawl, or Options > MJ App/Browser then Import Shots.");
            empty.setTextColor(Color.rgb(180, 187, 199));
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, dp(36));
            GridLayout.LayoutParams emptyParams = new GridLayout.LayoutParams();
            emptyParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            emptyParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            emptyParams.columnSpec = GridLayout.spec(0, columns);
            empty.setLayoutParams(emptyParams);
            grid.addView(empty);
        }

        updateCounts();
    }

    private Bitmap decodeThumbnail(ArchiveItem item, int reqWidth, int reqHeight) {
        Uri uri = uriForItem(item);
        if (uri == null) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (Exception e) {
            return null;
        }
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
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (Exception e) {
            return null;
        }
    }

    private Uri uriForItem(ArchiveItem item) {
        if (item == null) {
            return null;
        }
        if (item.localUri != null && item.localUri.length() > 0) {
            return Uri.parse(item.localUri);
        }
        if (item.localPath != null && item.localPath.length() > 0) {
            return Uri.fromFile(new File(item.localPath));
        }
        return null;
    }

    private void showPreview(ArchiveItem item) {
        Uri uri = uriForItem(item);
        if (uri == null || previewOverlay == null || previewImage == null) {
            return;
        }
        previewImage.resetZoom();
        previewImage.setImageURI(uri);
        previewOverlay.setVisibility(View.VISIBLE);
        previewOverlay.bringToFront();
    }

    private void hidePreview() {
        if (previewOverlay != null) {
            previewOverlay.setVisibility(View.GONE);
        }
        if (previewImage != null) {
            previewImage.setImageDrawable(null);
        }
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
                + "  |  tab " + displayKind(activeKind)
                + "  |  " + formatBytes(directorySize(photosDir) + directorySize(galleryFallbackDir) + directorySize(legacyMediaDir))
                + "  |  pending " + pendingDownloads);
    }

    private String displayKind(String kind) {
        if ("images".equals(kind)) {
            return "Images";
        }
        if ("styles".equals(kind)) {
            return "Styles";
        }
        if ("videos".equals(kind)) {
            return "Videos";
        }
        return kind;
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
                if (!storedImageExists(item) && !isPermissionPendingMediaItem(item)) {
                    continue;
                }
                if (item.savedAt <= 0L) {
                    File file = item.localPath == null || item.localPath.length() == 0 ? null : new File(item.localPath);
                    item.savedAt = file != null && file.lastModified() > 0L ? file.lastModified() : System.currentTimeMillis();
                }
                if (itemKeys.contains(item.key) || hasStoredReference(item.localPath, item.localUri)) {
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

    private int importGalleryFolder() {
        int before = items.size();
        importFromMediaStore();
        importFromDirectory(galleryFallbackDir);
        importFromDirectory(photosDir);
        if (items.size() != before) {
            saveArchive();
        }
        return Math.max(0, items.size() - before);
    }

    private int importRecentExternalImages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasGalleryReadPermission()) {
            pendingExternalImport = true;
            ensureGalleryReadPermission();
            setStatus("Allow gallery permission, then tap Import Shots again.");
            return -1;
        }

        int before = items.size();
        long since = externalSessionStartedAt > 0
                ? Math.max(0L, externalSessionStartedAt - 60_000L)
                : Math.max(0L, System.currentTimeMillis() - 24L * 60L * 60L * 1000L);

        importRecentExternalMediaStore(since);

        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        importRecentExternalDirectory(new File(pictures, "Screenshots"), since);
        importRecentExternalDirectory(new File(dcim, "Screenshots"), since);
        importRecentExternalDirectory(new File(pictures, "Midjourney"), since);
        importRecentExternalDirectory(downloads, since);

        if (items.size() != before) {
            saveArchive();
        }
        return Math.max(0, items.size() - before);
    }

    private void importRecentExternalMediaStore(long sinceMs) {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED
        };

        String selection;
        String[] selectionArgs;
        String sinceSeconds = String.valueOf(Math.max(0L, sinceMs / 1000L));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "("
                    + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                    + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                    + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                    + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?"
                    + ") AND " + MediaStore.Images.Media.DATE_ADDED + " >= ?";
            selectionArgs = new String[]{
                    Environment.DIRECTORY_PICTURES + "/Screenshots%",
                    Environment.DIRECTORY_DCIM + "/Screenshots%",
                    Environment.DIRECTORY_PICTURES + "/Midjourney%",
                    Environment.DIRECTORY_DOWNLOADS + "%",
                    sinceSeconds
            };
        } else {
            selection = "("
                    + MediaStore.Images.Media.DATA + " LIKE ? OR "
                    + MediaStore.Images.Media.DATA + " LIKE ? OR "
                    + MediaStore.Images.Media.DATA + " LIKE ? OR "
                    + MediaStore.Images.Media.DATA + " LIKE ?"
                    + ") AND " + MediaStore.Images.Media.DATE_ADDED + " >= ?";
            selectionArgs = new String[]{
                    "%/Pictures/Screenshots/%",
                    "%/DCIM/Screenshots/%",
                    "%/Pictures/Midjourney/%",
                    "%/Download/%",
                    sinceSeconds
            };
        }

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, selectionArgs, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                Uri uri = ContentUris.withAppendedId(collection, id);
                long savedAt = Math.max(cursor.getLong(addedColumn), cursor.getLong(modifiedColumn)) * 1000L;
                String key = sha256("external:" + uri);
                addImportedItem(activeKind, key, name, "", uri.toString(), savedAt);
            }
        } catch (Exception ignored) {
        }
    }

    private void importRecentExternalDirectory(File dir, long sinceMs) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null || !file.isFile() || file.length() == 0 || !hasImageExtension(file.getName())) {
                continue;
            }
            if (file.lastModified() + 1000L < sinceMs) {
                continue;
            }
            String key = sha256("external-file:" + file.getAbsolutePath());
            addImportedItem(activeKind, key, file.getName(), file.getAbsolutePath(), Uri.fromFile(file).toString(), file.lastModified());
        }
    }

    private void importFromMediaStore() {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED
        };
        String selection;
        String[] selectionArgs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            selectionArgs = new String[]{Environment.DIRECTORY_PICTURES + "/MJLocalArchive%"};
        } else {
            selection = MediaStore.Images.Media.DATA + " LIKE ?";
            selectionArgs = new String[]{"%/Pictures/MJLocalArchive/%"};
        }

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, selectionArgs, MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return;
            }
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                Uri uri = ContentUris.withAppendedId(collection, id);
                String kind = kindFromName(name);
                String key = sha256("mediastore:" + uri);
                addImportedItem(kind, key, name, "", uri.toString(), cursor.getLong(modifiedColumn) * 1000L);
            }
        } catch (Exception ignored) {
        }
    }

    private void importFromDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null || !file.isFile() || file.length() == 0 || !hasImageExtension(file.getName())) {
                continue;
            }
            String kind = kindFromName(file.getName());
            String key = sha256("file:" + file.getAbsolutePath());
            addImportedItem(kind, key, file.getName(), file.getAbsolutePath(), Uri.fromFile(file).toString(), file.lastModified());
        }
    }

    private void addImportedItem(String kind, String key, String name, String path, String uri, long savedAt) {
        if (key == null || itemKeys.contains(key) || deletedKeys.contains(key) || hasStoredReference(path, uri)) {
            return;
        }
        ArchiveItem item = new ArchiveItem();
        item.id = "gallery-" + key.substring(0, Math.min(16, key.length()));
        item.kind = kind;
        item.sourceUrl = "gallery-import:" + name;
        item.key = key;
        item.localPath = path == null ? "" : path;
        item.localUri = uri == null ? "" : uri;
        item.savedAt = savedAt > 0L ? savedAt : System.currentTimeMillis();
        if (!storedImageExists(item)) {
            return;
        }
        itemKeys.add(item.key);
        items.add(item);
    }

    private boolean hasStoredReference(String path, String uri) {
        for (ArchiveItem item : items) {
            if (uri != null && uri.length() > 0 && uri.equals(item.localUri)) {
                return true;
            }
            if (path != null && path.length() > 0 && path.equals(item.localPath)) {
                return true;
            }
        }
        return false;
    }

    private String kindFromName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.startsWith("styles-") || lower.contains("-styles-")) {
            return "styles";
        }
        if (lower.startsWith("videos-") || lower.contains("-videos-")) {
            return "videos";
        }
        if (lower.startsWith("images-") || lower.contains("-images-")) {
            return "images";
        }
        return activeKind != null ? activeKind : "images";
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

    private String mimeTypeForExt(String ext) {
        if ("png".equals(ext)) {
            return "image/png";
        }
        if ("webp".equals(ext)) {
            return "image/webp";
        }
        return "image/jpeg";
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

    private boolean storedImageExists(ArchiveItem item) {
        Uri uri = uriForItem(item);
        if (uri == null) {
            return false;
        }
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return input != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPermissionPendingMediaItem(ArchiveItem item) {
        return item != null
                && item.localUri != null
                && item.localUri.startsWith("content://")
                && !hasGalleryReadPermission();
    }

    private void deleteStoredImage(ArchiveItem item) {
        if (item == null) {
            return;
        }
        if (item.localUri != null && item.localUri.length() > 0) {
            try {
                getContentResolver().delete(Uri.parse(item.localUri), null, null);
            } catch (Exception ignored) {
            }
        }
        if (item.localPath != null && item.localPath.length() > 0) {
            deleteFileQuietly(new File(item.localPath));
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

    private String formatSeconds(int millis) {
        return String.format(Locale.US, "%.1fs", millis / 1000.0);
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

    private static class ZoomImageView extends ImageView {
        private final ScaleGestureDetector scaleDetector;
        private float scale = 1f;
        private float lastX;
        private float lastY;
        private boolean dragging;

        ZoomImageView(Activity activity) {
            super(activity);
            scaleDetector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scale = Math.max(1f, Math.min(6f, scale * detector.getScaleFactor()));
                    setScaleX(scale);
                    setScaleY(scale);
                    if (scale <= 1.01f) {
                        setTranslationX(0f);
                        setTranslationY(0f);
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            if (event.getPointerCount() > 1) {
                dragging = false;
                return true;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = scale > 1.01f;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        setTranslationX(getTranslationX() + dx);
                        setTranslationY(getTranslationY() + dy);
                        lastX = event.getX();
                        lastY = event.getY();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    performClick();
                    break;
                default:
                    break;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        void resetZoom() {
            scale = 1f;
            setScaleX(1f);
            setScaleY(1f);
            setTranslationX(0f);
            setTranslationY(0f);
        }
    }

    private static class StoredImage {
        final String path;
        final String uri;

        StoredImage(String path, String uri) {
            this.path = path == null ? "" : path;
            this.uri = uri == null ? "" : uri;
        }
    }

    private static class ArchiveItem {
        String id;
        String kind;
        String sourceUrl;
        String key;
        String localPath;
        String localUri;
        long savedAt;

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("kind", kind);
            obj.put("sourceUrl", sourceUrl);
            obj.put("key", key);
            obj.put("localPath", localPath);
            obj.put("localUri", localUri);
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
            item.localUri = obj.optString("localUri", "");
            item.savedAt = obj.optLong("savedAt", 0L);
            if (item.id.length() == 0 || item.kind.length() == 0 || item.key.length() == 0
                    || ((item.localPath == null || item.localPath.length() == 0) && (item.localUri == null || item.localUri.length() == 0))) {
                return null;
            }
            return item;
        }
    }
}
