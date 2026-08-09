/* ================================================================
   KONEKT BROWSER for Android
   NKO Intl. Foundation of Technological Research & Development

   One Activity, framework-only (no androidx, no dependencies).
   Tabs are android.webkit.WebViews; the chrome is built in code in
   the KONEKT design language: black, cubic, thin strokes. Bottom
   toolbar for thumbs, Speed Dial start page from assets, the same
   domain-level ad blocker as the desktop build.
   ================================================================ */
package network.konekt.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BrowserActivity extends Activity {

  /* ------- KONEKT design tokens ------- */
  static final int BG = 0xFF000000, HOVER2 = 0xFF141414;
  static final int LINE = 0xFF434343, LINE2 = 0xFF666666, LINE3 = 0xFF232323;
  static final int TEXT = 0xFFFFFFFF, DIM = 0xFF999999, DIM2 = 0xFF6F6F6F;
  static final int ACCENT = 0xFF1D9BF0, WARN = 0xFFFFD400;

  static final String START = "file:///android_asset/start.html";
  static final String KONEKT_URL = "https://konekt-tawny.vercel.app";

  static final String[] ADBLOCK = {
    "doubleclick.net","googlesyndication.com","googleadservices.com","adservice.google.com",
    "2mdn.net","adnxs.com","criteo.com","criteo.net","taboola.com","outbrain.com",
    "scorecardresearch.com","quantserve.com","quantcount.com","moatads.com","adsrvr.org",
    "amazon-adsystem.com","pubmatic.com","rubiconproject.com","openx.net","yieldmo.com",
    "smartadserver.com","adform.net","bidswitch.net","casalemedia.com","33across.com",
    "gumgum.com","sharethrough.com","teads.tv","zemanta.com","mathtag.com",
    "bluekai.com","demdex.net","krxd.net","exelator.com","agkn.com",
    "eyeota.net","tapad.com","rlcdn.com","adroll.com","serving-sys.com" };

  static final String[][] ENGINES = {
    {"google","Google","https://www.google.com/search?q="},
    {"duckduckgo","DuckDuckGo","https://duckduckgo.com/?q="},
    {"bing","Bing","https://www.bing.com/search?q="},
    {"yandex","Yandex","https://yandex.com/search/?text="}};

  class Tab { WebView wv; String url = START; String title = "Speed Dial"; boolean desktop = false; }

  final List<Tab> tabs = new ArrayList<>();
  int cur = -1;

  FrameLayout root, container, fsHolder;
  LinearLayout topBar, bottomBar, tabSheet, menuSheet;
  ScrollView tabScroll;
  EditText addr;
  Icon secIc, backB, fwdB, tabsB;
  View progress;
  SharedPreferences prefs;

  boolean adblockOn = true;
  long adBlocked = 0;
  String defaultUA;

  View customView; WebChromeClient.CustomViewCallback customCb;
  ValueCallback<Uri[]> fileCb;
  GeolocationPermissions.Callback geoCb; String geoOrigin;
  PermissionRequest mediaReq;

  int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }

  /* ================================================================ UI */
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("kb", MODE_PRIVATE);
    adblockOn = prefs.getBoolean("adblock", true);

    Window w = getWindow();
    w.setStatusBarColor(BG);
    w.setNavigationBarColor(BG);

    root = new FrameLayout(this);
    root.setBackgroundColor(BG);

    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);

    /* ---- top strip: padlock + address ---- */
    topBar = new LinearLayout(this);
    topBar.setOrientation(LinearLayout.HORIZONTAL);
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setBackgroundColor(BG);

    secIc = new Icon(this, Icon.GLOBE);
    secIc.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(48)));
    topBar.addView(secIc);

    addr = new EditText(this);
    addr.setBackground(null);
    addr.setTextColor(TEXT);
    addr.setHintTextColor(DIM2);
    addr.setHint("Search or enter address");
    addr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    addr.setSingleLine(true);
    addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    addr.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    addr.setSelectAllOnFocus(true);
    LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    addr.setLayoutParams(alp);
    addr.setOnEditorActionListener((v, actionId, ev) -> {
      if (actionId == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
        String q = addr.getText().toString().trim();
        if (q.length() > 0) { navigate(toURL(q)); hideKeyboard(); }
        return true;
      }
      return false;
    });
    topBar.addView(addr);

    Icon reload = new Icon(this, Icon.RELOAD);
    reload.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(48)));
    reload.setOnClickListener(v -> { Tab t = at(); if (t != null && t.wv != null) t.wv.reload(); });
    topBar.addView(reload);

    col.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    col.addView(hairline());

    progress = new View(this);
    progress.setBackgroundColor(ACCENT);
    FrameLayout progWrap = new FrameLayout(this);
    progWrap.addView(progress, new FrameLayout.LayoutParams(0, dp(2)));
    col.addView(progWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

    /* ---- web container ---- */
    container = new FrameLayout(this);
    col.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    /* ---- bottom bar: back fwd KONEKT tabs menu ---- */
    col.addView(hairline());
    bottomBar = new LinearLayout(this);
    bottomBar.setOrientation(LinearLayout.HORIZONTAL);
    bottomBar.setBackgroundColor(BG);

    backB = barIcon(Icon.BACK, v -> { Tab t = at(); if (t != null && t.wv != null && t.wv.canGoBack()) t.wv.goBack(); });
    fwdB = barIcon(Icon.FWD, v -> { Tab t = at(); if (t != null && t.wv != null && t.wv.canGoForward()) t.wv.goForward(); });
    Icon kon = barIcon(Icon.GLOBE, v -> openKonekt());
    tabsB = barIcon(Icon.TABS, v -> showTabs(true));
    Icon menu = barIcon(Icon.MENU, v -> showMenu(true));
    bottomBar.addView(backB); bottomBar.addView(fwdB); bottomBar.addView(kon);
    bottomBar.addView(tabsB); bottomBar.addView(menu);
    col.addView(bottomBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

    root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    /* overlays */
    buildTabSheet();
    buildMenuSheet();
    fsHolder = new FrameLayout(this);
    fsHolder.setBackgroundColor(BG);
    fsHolder.setVisibility(View.GONE);
    root.addView(fsHolder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    setContentView(root);
    defaultUA = WebSettings.getDefaultUserAgent(this);

    /* session restore or intent url */
    String intentUrl = urlFromIntent(getIntent());
    restoreSession();
    if (tabs.isEmpty()) newTab(START, false);
    if (intentUrl != null) newTab(intentUrl, false);
  }

  View hairline() {
    View v = new View(this);
    v.setBackgroundColor(LINE3);
    v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    return v;
  }

  Icon barIcon(int type, View.OnClickListener fn) {
    Icon ic = new Icon(this, type);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    ic.setLayoutParams(lp);
    ic.setOnClickListener(fn);
    return ic;
  }

  String urlFromIntent(Intent i) {
    if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
      String s = i.getData().toString();
      if (s.startsWith("http")) return s;
    }
    return null;
  }

  @Override protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    String u = urlFromIntent(i);
    if (u != null) newTab(u, false);
  }

  /* ================================================================ tabs */
  Tab at() { return cur >= 0 && cur < tabs.size() ? tabs.get(cur) : null; }

  Tab newTab(String url, boolean background) {
    Tab t = new Tab();
    t.wv = makeWebView(t);
    tabs.add(t);
    if (url != null) { t.url = url; t.wv.loadUrl(url); }
    if (!background || cur < 0) activate(tabs.size() - 1);
    else renderChrome();
    return t;
  }

  void activate(int i) {
    if (i < 0 || i >= tabs.size()) return;
    cur = i;
    Tab t = tabs.get(i);
    container.removeAllViews();
    container.addView(t.wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    renderChrome();
    saveSession();
  }

  void closeTab(int i) {
    if (i < 0 || i >= tabs.size()) return;
    Tab t = tabs.remove(i);
    if (t.wv != null) { container.removeView(t.wv); t.wv.destroy(); }
    if (tabs.isEmpty()) { cur = -1; newTab(START, false); return; }
    if (cur >= tabs.size()) cur = tabs.size() - 1;
    else if (i <= cur && cur > 0) cur--;
    activate(cur);
  }

  void navigate(String url) {
    Tab t = at();
    if (t == null) { newTab(url, false); return; }
    t.url = url;
    t.wv.loadUrl(url);
  }

  void openKonekt() {
    for (int i = 0; i < tabs.size(); i++) {
      String u = tabs.get(i).url;
      if (u != null && u.startsWith(KONEKT_URL)) { activate(i); return; }
    }
    newTab(KONEKT_URL, false);
  }

  /* ================================================================ URL logic */
  String engineKey() { return prefs.getString("engine", "google"); }
  String[] engine() {
    String k = engineKey();
    for (String[] e : ENGINES) if (e[0].equals(k)) return e;
    return ENGINES[0];
  }

  String toURL(String q) {
    q = q.trim();
    if (q.isEmpty()) return START;
    if (q.equals("konekt://start")) return START;
    String lower = q.toLowerCase(Locale.US);
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:") || lower.startsWith("data:") || lower.startsWith("about:")) return q;
    if (lower.matches("^localhost(:\\d+)?([/?#].*)?$")) return "http://" + q;
    if (lower.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?([/?#].*)?$")) return "http://" + q;
    if (!q.contains(" ") && lower.matches("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?([/?#].*)?$")) return "https://" + q;
    return engine()[2] + Uri.encode(q);
  }

  /* ================================================================ webview */
  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  WebView makeWebView(final Tab t) {
    WebView wv = new WebView(this);
    wv.setBackgroundColor(BG);
    WebSettings s = wv.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setLoadWithOverviewMode(true);
    s.setUseWideViewPort(true);
    s.setBuiltInZoomControls(true);
    s.setDisplayZoomControls(false);
    s.setSupportMultipleWindows(true);
    s.setJavaScriptCanOpenWindowsAutomatically(false);
    s.setMediaPlaybackRequiresUserGesture(true);
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
    wv.addJavascriptInterface(new KBridge(t), "KB");
    wv.setDownloadListener((url, ua, contentDisposition, mimetype, contentLength) -> download(url, ua, contentDisposition, mimetype));

    wv.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
        String u = req.getUrl().toString();
        if (u.startsWith("http:") || u.startsWith("https:") || u.startsWith("file:") || u.startsWith("data:") || u.startsWith("about:")) return false;
        try {
          Intent it = u.startsWith("intent:") ? Intent.parseUri(u, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(u));
          it.addCategory(Intent.CATEGORY_BROWSABLE);
          it.setComponent(null);
          if (it.resolveActivity(getPackageManager()) != null) startActivity(it);
        } catch (Exception ignored) {}
        return true;
      }
      @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        t.url = url;
        if (view == wvOf(at())) renderChrome();
      }
      @Override public void onPageFinished(WebView view, String url) {
        t.url = url;
        if (t.title == null || t.title.isEmpty()) t.title = view.getTitle();
        recordVisit(url, view.getTitle());
        if (view == wvOf(at())) { renderChrome(); setLoadProgress(100); }
        saveSession();
      }
      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
        if (adblockOn) {
          String host = req.getUrl().getHost();
          if (host != null) {
            String h = host.toLowerCase(Locale.US);
            for (String d : ADBLOCK) {
              if (h.equals(d) || h.endsWith("." + d)) {
                adBlocked++;
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
              }
            }
          }
        }
        return null;
      }
      @Override public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError err) {
        if (Build.VERSION.SDK_INT >= 23 && req.isForMainFrame()) {
          String u = req.getUrl().toString();
          String desc = String.valueOf(err.getDescription());
          view.loadDataWithBaseURL(null, errPage(u, desc), "text/html", "utf-8", u);
        }
      }
    });

    wv.setWebChromeClient(new WebChromeClient() {
      @Override public void onProgressChanged(WebView view, int p) {
        if (view == wvOf(at())) setLoadProgress(p);
      }
      @Override public void onReceivedTitle(WebView view, String title) {
        t.title = title;
        if (view == wvOf(at())) renderChrome();
      }
      @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        if (!isUserGesture) return false;
        Tab nt = newTab(null, false);
        WebView.WebViewTransport tr = (WebView.WebViewTransport) resultMsg.obj;
        tr.setWebView(nt.wv);
        resultMsg.sendToTarget();
        return true;
      }
      @Override public void onCloseWindow(WebView view) {
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).wv == view) { closeTab(i); return; }
      }
      @Override public void onShowCustomView(View v, CustomViewCallback cb) {
        if (customView != null) { cb.onCustomViewHidden(); return; }
        customView = v; customCb = cb;
        fsHolder.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fsHolder.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
      }
      @Override public void onHideCustomView() {
        if (customView == null) return;
        fsHolder.removeView(customView);
        fsHolder.setVisibility(View.GONE);
        customView = null;
        if (customCb != null) { customCb.onCustomViewHidden(); customCb = null; }
        getWindow().getDecorView().setSystemUiVisibility(0);
      }
      @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
          askSite(origin + " wants to know your location", () -> cb.invoke(origin, true, true), () -> cb.invoke(origin, false, false));
        } else {
          geoCb = cb; geoOrigin = origin;
          requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 71);
        }
      }
      @Override public void onPermissionRequest(PermissionRequest req) {
        List<String> need = new ArrayList<>();
        for (String r : req.getResources()) {
          if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(android.Manifest.permission.CAMERA);
          if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(android.Manifest.permission.RECORD_AUDIO);
        }
        if (need.isEmpty()) {
          askSite(req.getOrigin().getHost() + " wants to use your camera or microphone",
            () -> req.grant(req.getResources()), req::deny);
        } else {
          mediaReq = req;
          requestPermissions(need.toArray(new String[0]), 72);
        }
      }
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        try { startActivityForResult(params.createIntent(), 73); }
        catch (Exception e) { fileCb = null; return false; }
        return true;
      }
      @Override public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult r) {
        new AlertDialog.Builder(BrowserActivity.this).setMessage(message)
          .setPositiveButton("OK", (d, x) -> r.confirm())
          .setOnCancelListener(d -> r.cancel()).show();
        return true;
      }
      @Override public boolean onJsConfirm(WebView view, String url, String message, final android.webkit.JsResult r) {
        new AlertDialog.Builder(BrowserActivity.this).setMessage(message)
          .setPositiveButton("OK", (d, x) -> r.confirm())
          .setNegativeButton("Cancel", (d, x) -> r.cancel())
          .setOnCancelListener(d -> r.cancel()).show();
        return true;
      }
      @Override public boolean onJsPrompt(WebView view, String url, String message, String def, final android.webkit.JsPromptResult r) {
        final EditText in = new EditText(BrowserActivity.this);
        in.setText(def == null ? "" : def);
        new AlertDialog.Builder(BrowserActivity.this).setMessage(message).setView(in)
          .setPositiveButton("OK", (d, x) -> r.confirm(in.getText().toString()))
          .setNegativeButton("Cancel", (d, x) -> r.cancel())
          .setOnCancelListener(d -> r.cancel()).show();
        return true;
      }
    });
    return wv;
  }

  WebView wvOf(Tab t) { return t == null ? null : t.wv; }

  void askSite(String what, Runnable yes, Runnable no) {
    new AlertDialog.Builder(this).setMessage(what)
      .setPositiveButton("Allow", (d, x) -> yes.run())
      .setNegativeButton("Block", (d, x) -> no.run())
      .setOnCancelListener(d -> no.run()).show();
  }

  String errPage(String url, String desc) {
    return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>" +
      "<body style='background:#000;color:#fff;font-family:sans-serif;padding:40px 24px'>" +
      "<h2 style='font-weight:800'>Can&rsquo;t reach this page</h2>" +
      "<div style='color:#999;font-size:13px;word-break:break-all;margin:10px 0'>" + esc(url) + "</div>" +
      "<div style='color:#999;font-size:13px;font-family:monospace'>" + esc(desc) + "</div>" +
      "<div style='margin-top:26px'><a href='" + esc(url) + "' style='color:#000;background:#fff;padding:10px 22px;" +
      "text-decoration:none;font-weight:700;font-size:12px;letter-spacing:.08em'>RETRY</a></div></body>";
  }

  static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  /* ================================================================ chrome state */
  void renderChrome() {
    Tab t = at();
    if (t == null) return;
    boolean start = t.url == null || t.url.startsWith("file:///android_asset");
    if (!addr.hasFocus()) addr.setText(start ? "" : t.url);
    secIc.setType(start ? Icon.GLOBE : (t.url.startsWith("https:") ? Icon.LOCK : Icon.UNLOCK));
    backB.setAlpha(t.wv != null && t.wv.canGoBack() ? 1f : 0.3f);
    fwdB.setAlpha(t.wv != null && t.wv.canGoForward() ? 1f : 0.3f);
    tabsB.setCount(tabs.size());
  }

  void setLoadProgress(int p) {
    ViewGroup.LayoutParams lp = progress.getLayoutParams();
    int w = container.getWidth() > 0 ? container.getWidth() : getResources().getDisplayMetrics().widthPixels;
    lp.width = p >= 100 ? 0 : (int) (w * (p / 100f));
    progress.setLayoutParams(lp);
  }

  void hideKeyboard() {
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.hideSoftInputFromWindow(addr.getWindowToken(), 0);
    addr.clearFocus();
  }

  /* ================================================================ downloads */
  void download(String url, String ua, String contentDisposition, String mimetype) {
    if (Build.VERSION.SDK_INT < 29 &&
        checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 74);
      Toast.makeText(this, "Storage access needed â€” tap the download again", Toast.LENGTH_LONG).show();
      return;
    }
    try {
      String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
      DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
      r.setMimeType(mimetype);
      r.addRequestHeader("User-Agent", ua);
      String cookie = CookieManager.getInstance().getCookie(url);
      if (cookie != null) r.addRequestHeader("Cookie", cookie);
      r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
      DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
      dm.enqueue(r);
      Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
    }
  }

  /* ================================================================ prefs: session, visits, dials */
  void saveSession() {
    try {
      JSONArray a = new JSONArray();
      for (Tab t : tabs) a.put(t.url == null ? START : t.url);
      prefs.edit().putString("session", a.toString()).putInt("cur", cur).apply();
    } catch (Exception ignored) {}
  }

  void restoreSession() {
    try {
      String s = prefs.getString("session", null);
      if (s == null) return;
      JSONArray a = new JSONArray(s);
      int want = prefs.getInt("cur", 0);
      for (int i = 0; i < a.length() && i < 10; i++) newTab(a.getString(i), true);
      if (!tabs.isEmpty()) activate(Math.max(0, Math.min(want, tabs.size() - 1)));
    } catch (Exception ignored) {}
  }

  void recordVisit(String url, String title) {
    if (url == null || !url.startsWith("http")) return;
    try {
      Uri u = Uri.parse(url);
      String host = u.getHost();
      if (host == null) return;
      host = host.replaceFirst("^www\\.", "");
      JSONObject all = new JSONObject(prefs.getString("hosts", "{}"));
      JSONObject h = all.optJSONObject(host);
      if (h == null) h = new JSONObject();
      h.put("n", h.optInt("n") + 1);
      h.put("u", u.getScheme() + "://" + u.getHost());
      if (title != null && !title.isEmpty()) h.put("t", title);
      all.put(host, h);
      prefs.edit().putString("hosts", all.toString()).apply();
    } catch (Exception ignored) {}
  }

  String topSitesJson() {
    try {
      JSONObject all = new JSONObject(prefs.getString("hosts", "{}"));
      List<String> keys = new ArrayList<>();
      Iterator<String> it = all.keys();
      while (it.hasNext()) keys.add(it.next());
      keys.sort((x, y) -> all.optJSONObject(y).optInt("n") - all.optJSONObject(x).optInt("n"));
      JSONArray out = new JSONArray();
      for (int i = 0; i < keys.size() && i < 6; i++) {
        JSONObject h = all.optJSONObject(keys.get(i));
        JSONObject o = new JSONObject();
        o.put("host", keys.get(i));
        o.put("u", h.optString("u"));
        out.put(o);
      }
      return out.toString();
    } catch (Exception e) { return "[]"; }
  }

  /* ================================================================ JS bridge (start page only) */
  class KBridge {
    final Tab tab;
    KBridge(Tab t) { tab = t; }
    boolean onStart() { return tab.url != null && tab.url.startsWith("file:///android_asset"); }

    @JavascriptInterface public void go(final String q) {
      if (!onStart()) return;
      runOnUiThread(() -> navigate(toURL(q)));
    }
    @JavascriptInterface public String dials() { return onStart() ? prefs.getString("dials", "[]") : "[]"; }
    @JavascriptInterface public String topSites() { return onStart() ? topSitesJson() : "[]"; }
    @JavascriptInterface public String engineName() { return engine()[1]; }
    @JavascriptInterface public void addDial(String u, String t) {
      if (!onStart() || u == null || u.trim().isEmpty()) return;
      try {
        String url = u.trim();
        if (!url.matches("(?i)^[a-z]+:.*")) url = "https://" + url;
        JSONArray a = new JSONArray(prefs.getString("dials", "[]"));
        JSONObject o = new JSONObject();
        o.put("u", url);
        o.put("t", (t == null || t.trim().isEmpty()) ? Uri.parse(url).getHost() : t.trim());
        a.put(o);
        prefs.edit().putString("dials", a.toString()).apply();
      } catch (Exception ignored) {}
    }
    @JavascriptInterface public void delDial(String u) {
      if (!onStart()) return;
      try {
        JSONArray a = new JSONArray(prefs.getString("dials", "[]"));
        JSONArray b = new JSONArray();
        for (int i = 0; i < a.length(); i++)
          if (!a.getJSONObject(i).optString("u").equals(u)) b.put(a.getJSONObject(i));
        prefs.edit().putString("dials", b.toString()).apply();
      } catch (Exception ignored) {}
    }
  }

  /* ================================================================ tab sheet */
  void buildTabSheet() {
    tabSheet = new LinearLayout(this);
    tabSheet.setOrientation(LinearLayout.VERTICAL);
    tabSheet.setBackgroundColor(BG);
    tabSheet.setVisibility(View.GONE);
    root.addView(tabSheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  void showTabs(boolean show) {
    if (!show) { tabSheet.setVisibility(View.GONE); return; }
    showMenu(false);
    tabSheet.removeAllViews();

    LinearLayout head = new LinearLayout(this);
    head.setOrientation(LinearLayout.HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(dp(16), dp(10), dp(6), dp(10));
    TextView h = label("TABS");
    LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    h.setLayoutParams(hlp);
    head.addView(h);
    Icon x = new Icon(this, Icon.X);
    x.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
    x.setOnClickListener(v -> showTabs(false));
    head.addView(x);
    tabSheet.addView(head);
    tabSheet.addView(hairline());

    TextView nt = new TextView(this);
    nt.setText("+  NEW TAB");
    nt.setTextColor(TEXT);
    nt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    nt.setTypeface(Typeface.DEFAULT_BOLD);
    nt.setLetterSpacing(0.08f);
    nt.setPadding(dp(16), dp(15), dp(16), dp(15));
    nt.setOnClickListener(v -> { showTabs(false); newTab(START, false); });
    tabSheet.addView(nt);
    tabSheet.addView(hairline());

    ScrollView sc = new ScrollView(this);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    sc.addView(list);
    for (int i = 0; i < tabs.size(); i++) {
      final int idx = i;
      Tab t = tabs.get(i);
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(16), dp(12), dp(6), dp(12));
      if (i == cur) row.setBackgroundColor(HOVER2);

      LinearLayout txt = new LinearLayout(this);
      txt.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
      txt.setLayoutParams(tlp);
      boolean start = t.url == null || t.url.startsWith("file:");
      TextView title = new TextView(this);
      title.setText(start ? "Speed Dial" : (t.title == null || t.title.isEmpty() ? "New tab" : t.title));
      title.setTextColor(i == cur ? TEXT : 0xFFD8D8D8);
      title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      title.setTypeface(Typeface.DEFAULT_BOLD);
      title.setSingleLine(true);
      TextView sub = new TextView(this);
      sub.setText(start ? "konekt://start" : t.url);
      sub.setTextColor(DIM);
      sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
      sub.setSingleLine(true);
      txt.addView(title); txt.addView(sub);
      row.addView(txt);

      Icon cx = new Icon(this, Icon.X);
      cx.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
      cx.setOnClickListener(v -> { closeTab(idx); showTabs(true); });
      row.addView(cx);

      row.setOnClickListener(v -> { activate(idx); showTabs(false); });
      list.addView(row);
      list.addView(hairline());
    }
    tabSheet.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    tabSheet.setVisibility(View.VISIBLE);
  }

  TextView label(String s) {
    TextView tv = new TextView(this);
    tv.setText(s);
    tv.setTextColor(DIM);
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
    tv.setLetterSpacing(0.16f);
    return tv;
  }

  /* ================================================================ menu sheet */
  void buildMenuSheet() {
    menuSheet = new LinearLayout(this);
    menuSheet.setOrientation(LinearLayout.VERTICAL);
    menuSheet.setBackgroundColor(BG);
    menuSheet.setVisibility(View.GONE);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    root.addView(menuSheet, lp);
  }

  TextView mrow(String label, View.OnClickListener fn) {
    TextView tv = new TextView(this);
    tv.setText(label);
    tv.setTextColor(0xFFD8D8D8);
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
    tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    tv.setPadding(dp(18), dp(13), dp(18), dp(13));
    tv.setOnClickListener(fn);
    return tv;
  }

  void showMenu(boolean show) {
    if (!show) { menuSheet.setVisibility(View.GONE); return; }
    showTabs(false);
    menuSheet.removeAllViews();
    View top = new View(this);
    top.setBackgroundColor(LINE);
    menuSheet.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

    Tab t = at();
    boolean onPage = t != null && t.url != null && t.url.startsWith("http");

    menuSheet.addView(mrow("New tab", v -> { showMenu(false); newTab(START, false); }));
    menuSheet.addView(mrow("Speed Dial", v -> { showMenu(false); navigate(START); }));
    if (onPage) menuSheet.addView(mrow("Share page", v -> {
      showMenu(false);
      Intent i = new Intent(Intent.ACTION_SEND);
      i.setType("text/plain");
      i.putExtra(Intent.EXTRA_TEXT, t.url);
      startActivity(Intent.createChooser(i, "Share"));
    }));
    menuSheet.addView(mrow((t != null && t.desktop ? "Mobile site" : "Desktop site"), v -> {
      showMenu(false);
      Tab tt = at();
      if (tt == null || tt.wv == null) return;
      tt.desktop = !tt.desktop;
      WebSettings ws = tt.wv.getSettings();
      ws.setUserAgentString(tt.desktop ? defaultUA.replace("Mobile ", "").replace("Android", "X11; Linux x86_64") : null);
      tt.wv.reload();
    }));
    menuSheet.addView(mrow("Ad blocker: " + (adblockOn ? "on" : "off") + "  Â·  " + adBlocked + " blocked", v -> {
      adblockOn = !adblockOn;
      prefs.edit().putBoolean("adblock", adblockOn).apply();
      showMenu(true);
    }));
    menuSheet.addView(mrow("Search engine: " + engine()[1], v -> {
      String k = engineKey();
      int i = 0;
      for (int j = 0; j < ENGINES.length; j++) if (ENGINES[j][0].equals(k)) i = j;
      prefs.edit().putString("engine", ENGINES[(i + 1) % ENGINES.length][0]).apply();
      showMenu(true);
    }));
    menuSheet.addView(mrow("Clear browsing data", v -> {
      showMenu(false);
      new AlertDialog.Builder(this).setMessage("Clear cookies, cache and site data?")
        .setPositiveButton("Clear", (d, x) -> {
          CookieManager.getInstance().removeAllCookies(null);
          CookieManager.getInstance().flush();
          WebStorage.getInstance().deleteAllData();
          for (Tab tt : tabs) if (tt.wv != null) tt.wv.clearCache(true);
          prefs.edit().remove("hosts").apply();
          Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton("Cancel", null).show();
    }));
    menuSheet.addView(mrow("About KONEKT Browser", v -> {
      showMenu(false);
      new AlertDialog.Builder(this)
        .setMessage("KONEKT Browser 1.0.0 for Android\nAndroid System WebView engine\n\nkonekt-browser.vercel.app\nÂ© 2026 KONEKT Â· NKO Intl. Foundation of Technological Research & Development")
        .setPositiveButton("OK", null).show();
    }));
    View pad = new View(this);
    menuSheet.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
    menuSheet.setVisibility(View.VISIBLE);
  }

  /* ================================================================ system plumbing */
  @Override public void onBackPressed() {
    if (customView != null) {
      fsHolder.removeView(customView);
      fsHolder.setVisibility(View.GONE);
      customView = null;
      if (customCb != null) { customCb.onCustomViewHidden(); customCb = null; }
      getWindow().getDecorView().setSystemUiVisibility(0);
      return;
    }
    if (menuSheet.getVisibility() == View.VISIBLE) { showMenu(false); return; }
    if (tabSheet.getVisibility() == View.VISIBLE) { showTabs(false); return; }
    Tab t = at();
    if (t != null && t.wv != null && t.wv.canGoBack()) { t.wv.goBack(); return; }
    if (tabs.size() > 1) { closeTab(cur); return; }
    moveTaskToBack(true);
  }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (req == 73 && fileCb != null) {
      fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
      fileCb = null;
    }
  }

  @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
    super.onRequestPermissionsResult(req, perms, grants);
    boolean ok = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
    if (req == 71 && geoCb != null) {
      geoCb.invoke(geoOrigin, ok, ok);
      geoCb = null;
    } else if (req == 72 && mediaReq != null) {
      boolean all = true;
      for (int g : grants) if (g != PackageManager.PERMISSION_GRANTED) all = false;
      if (all) mediaReq.grant(mediaReq.getResources()); else mediaReq.deny();
      mediaReq = null;
    }
  }

  @Override protected void onPause() {
    super.onPause();
    saveSession();
    CookieManager.getInstance().flush();
    Tab t = at();
    if (t != null && t.wv != null) t.wv.onPause();
  }

  @Override protected void onResume() {
    super.onResume();
    Tab t = at();
    if (t != null && t.wv != null) t.wv.onResume();
  }

  /* ================================================================ stroke icons (KONEKT style) */
  static class Icon extends View {
    static final int BACK = 0, FWD = 1, GLOBE = 2, TABS = 3, MENU = 4, X = 5, RELOAD = 6, LOCK = 7, UNLOCK = 8;
    int type;
    int count = 0;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icon(Context c, int t) {
      super(c);
      type = t;
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      p.setColor(0xFFD8D8D8);
      tp.setColor(0xFFD8D8D8);
      tp.setTextAlign(Paint.Align.CENTER);
      tp.setTypeface(Typeface.DEFAULT_BOLD);
      setClickable(true);
    }

    void setType(int t) { type = t; invalidate(); }
    void setCount(int n) { count = n; invalidate(); }

    @Override public boolean onTouchEvent(MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN) setAlpha(getAlpha() * 0.55f);
      if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
        setAlpha(Math.min(1f, getAlpha() / 0.55f));
      return super.onTouchEvent(e);
    }

    float dpf(float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, u = dpf(1);
      p.setStrokeWidth(1.8f * u);
      switch (type) {
        case BACK:
          c.drawLine(cx + 4 * u, cy - 7 * u, cx - 4 * u, cy, p);
          c.drawLine(cx - 4 * u, cy, cx + 4 * u, cy + 7 * u, p);
          break;
        case FWD:
          c.drawLine(cx - 4 * u, cy - 7 * u, cx + 4 * u, cy, p);
          c.drawLine(cx + 4 * u, cy, cx - 4 * u, cy + 7 * u, p);
          break;
        case GLOBE: {
          float r = 8.2f * u;
          p.setStrokeWidth(1.6f * u);
          c.drawCircle(cx, cy, r, p);
          c.drawOval(new RectF(cx - r * 0.42f, cy - r, cx + r * 0.42f, cy + r), p);
          c.drawLine(cx - r, cy, cx + r, cy, p);
          c.drawLine(cx - r * 0.87f, cy - r * 0.46f, cx + r * 0.87f, cy - r * 0.46f, p);
          c.drawLine(cx - r * 0.87f, cy + r * 0.46f, cx + r * 0.87f, cy + r * 0.46f, p);
          break;
        }
        case TABS: {
          float s = 8 * u;
          c.drawRect(cx - s, cy - s, cx + s, cy + s, p);
          if (count > 0) {
            tp.setTextSize(9.5f * u);
            c.drawText(count > 99 ? "99" : String.valueOf(count), cx, cy + 3.4f * u, tp);
          }
          break;
        }
        case MENU:
          p.setStrokeWidth(2.2f * u);
          c.drawPoint(cx, cy - 6 * u, p);
          c.drawPoint(cx, cy, p);
          c.drawPoint(cx, cy + 6 * u, p);
          break;
        case X:
          c.drawLine(cx - 5 * u, cy - 5 * u, cx + 5 * u, cy + 5 * u, p);
          c.drawLine(cx + 5 * u, cy - 5 * u, cx - 5 * u, cy + 5 * u, p);
          break;
        case RELOAD: {
          float r = 7 * u;
          RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
          c.drawArc(o, -50, 300, false, p);
          c.drawLine(cx + r * 0.55f, cy - r * 1.15f, cx + r * 0.72f, cy - r * 0.55f, p);
          c.drawLine(cx + r * 0.72f, cy - r * 0.55f, cx + r * 0.1f, cy - r * 0.5f, p);
          break;
        }
        case LOCK: {
          float bw = 6.5f * u, bh = 5.5f * u;
          c.drawRect(cx - bw, cy - 1 * u, cx + bw, cy + bh + 1 * u, p);
          RectF arc = new RectF(cx - 4 * u, cy - 8 * u, cx + 4 * u, cy + 1 * u);
          c.drawArc(arc, 180, 180, false, p);
          break;
        }
        case UNLOCK: {
          float bw = 6.5f * u, bh = 5.5f * u;
          c.drawRect(cx - bw, cy - 1 * u, cx + bw, cy + bh + 1 * u, p);
          RectF arc = new RectF(cx - 4 * u, cy - 8 * u, cx + 4 * u, cy + 1 * u);
          c.drawArc(arc, 180, 120, false, p);
          break;
        }
      }
    }
  }
}
