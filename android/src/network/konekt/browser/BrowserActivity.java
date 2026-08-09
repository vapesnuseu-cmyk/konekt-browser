/* ================================================================
   KONEKT BROWSER for Android
   NKO Intl. Foundation of Technological Research & Development

   One Activity, framework-only (no androidx, no dependencies).
   Tabs are android.webkit.WebViews. The chrome is built in code in
   the KONEKT design language and lives at the BOTTOM, thumb-first
   (Opera-mobile style): an address row and a five-icon nav row, with
   almost everything else folded into the menu. Fully customisable
   (mode / accent / wallpaper), account-synced, and self-updating
   from the GitHub release.
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
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class BrowserActivity extends Activity {

  /* ------- palette (swapped live by appearance mode) ------- */
  int BG = 0xFF000000, BAR = 0xFF000000, HOVER2 = 0xFF141414;
  int LINE = 0xFF434343, LINE2 = 0xFF666666, LINE3 = 0xFF232323;
  int TEXT = 0xFFFFFFFF, DIM = 0xFF999999, DIM2 = 0xFF6F6F6F;
  int ACCENT = 0xFF1D9BF0;
  boolean glass = false;

  static final String START = "file:///android_asset/start.html";
  static final String KONEKT_URL = "https://konekt-tawny.vercel.app";
  static final String API_BASE = "https://konekt-browser.vercel.app";
  static final String REPO = "vapesnuseu-cmyk/konekt-browser";
  static final String RELEASES = "https://github.com/" + REPO + "/releases/latest";

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

  static final String[] ACCENTS = {"#1d9bf0","#00ba7c","#f91880","#ffd400","#a970ff","#ff6a00","#ffffff"};
  static final String[][] WALLS = {
    {"none","None"},{"aurora","Aurora"},{"ember","Ember"},{"mono","Mono"},{"teal","Teal"},{"violet","Violet"}};

  class Tab { WebView wv; String url = START; String title = "Speed Dial"; boolean desktop = false; }

  final List<Tab> tabs = new ArrayList<>();
  int cur = -1;

  FrameLayout root, container, fsHolder;
  LinearLayout bottomWrap, addrRow, navRow, sheet;
  EditText addr;
  Icon lockIc, reloadIc, backB, fwdB, tabsB, acctB, menuB;
  View progress, addrRowLine, navRowLine;
  SharedPreferences prefs;

  boolean adblockOn = true;
  long adBlocked = 0;
  String defaultUA;
  final Handler ui = new Handler(Looper.getMainLooper());

  View customView; WebChromeClient.CustomViewCallback customCb;
  ValueCallback<Uri[]> fileCb;
  GeolocationPermissions.Callback geoCb; String geoOrigin;
  PermissionRequest mediaReq;
  Runnable pushPending;

  int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }
  int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

  /* ================================================================ UI */
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("kb", MODE_PRIVATE);
    adblockOn = prefs.getBoolean("adblock", true);
    computePalette();

    Window w = getWindow();
    w.setStatusBarColor(BG);
    w.setNavigationBarColor(BAR);

    root = new FrameLayout(this);
    root.setBackgroundColor(BG);

    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);

    /* top: just a thin progress line */
    progress = new View(this);
    progress.setBackgroundColor(ACCENT);
    FrameLayout progWrap = new FrameLayout(this);
    progWrap.addView(progress, new FrameLayout.LayoutParams(0, dp(2)));
    col.addView(progWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

    /* web container */
    container = new FrameLayout(this);
    col.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    /* ---- BOTTOM chrome: address row + nav row ---- */
    bottomWrap = new LinearLayout(this);
    bottomWrap.setOrientation(LinearLayout.VERTICAL);

    addrRowLine = new View(this);
    bottomWrap.addView(addrRowLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

    addrRow = new LinearLayout(this);
    addrRow.setOrientation(LinearLayout.HORIZONTAL);
    addrRow.setGravity(Gravity.CENTER_VERTICAL);

    lockIc = new Icon(this, Icon.GLOBE);
    lockIc.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(46)));
    lockIc.setOnClickListener(v -> { if (addr != null) { addr.requestFocus(); addr.selectAll(); showKeyboard(); } });
    addrRow.addView(lockIc);

    addr = new EditText(this);
    addr.setBackground(null);
    addr.setHint("Search or enter address");
    addr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    addr.setSingleLine(true);
    addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    addr.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    addr.setSelectAllOnFocus(true);
    addr.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    addr.setOnEditorActionListener((v, id, ev) -> {
      if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
        String q = addr.getText().toString().trim();
        if (q.length() > 0) { navigate(toURL(q)); hideKeyboard(); }
        return true;
      }
      return false;
    });
    addrRow.addView(addr);

    reloadIc = new Icon(this, Icon.RELOAD);
    reloadIc.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(46)));
    reloadIc.setOnClickListener(v -> { Tab t = at(); if (t != null && t.wv != null) t.wv.reload(); });
    addrRow.addView(reloadIc);
    bottomWrap.addView(addrRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

    navRowLine = new View(this);
    bottomWrap.addView(navRowLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

    navRow = new LinearLayout(this);
    navRow.setOrientation(LinearLayout.HORIZONTAL);
    backB = navIcon(Icon.BACK, v -> { Tab t = at(); if (t != null && t.wv != null && t.wv.canGoBack()) t.wv.goBack(); });
    fwdB  = navIcon(Icon.FWD,  v -> { Tab t = at(); if (t != null && t.wv != null && t.wv.canGoForward()) t.wv.goForward(); });
    tabsB = navIcon(Icon.TABS, v -> showTabs());
    acctB = navIcon(Icon.PERSON, v -> showAccount());
    menuB = navIcon(Icon.MENU, v -> showMenu());
    navRow.addView(backB); navRow.addView(fwdB); navRow.addView(tabsB); navRow.addView(acctB); navRow.addView(menuB);
    bottomWrap.addView(navRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

    col.addView(bottomWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    fsHolder = new FrameLayout(this);
    fsHolder.setBackgroundColor(0xFF000000);
    fsHolder.setVisibility(View.GONE);
    root.addView(fsHolder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    setContentView(root);
    defaultUA = WebSettings.getDefaultUserAgent(this);
    applyAppearance();

    String intentUrl = urlFromIntent(getIntent());
    restoreSession();
    if (tabs.isEmpty()) newTab(START, false);
    if (intentUrl != null) newTab(intentUrl, false);
    ui.postDelayed(() -> checkUpdates(true), 2500);   // silent check for a new version on launch
  }

  Icon navIcon(int type, View.OnClickListener fn) {
    Icon ic = new Icon(this, type);
    ic.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    ic.setOnClickListener(fn);
    return ic;
  }
  View hairline() { View v = new View(this); v.setBackgroundColor(LINE3); return v; }

  String urlFromIntent(Intent i) {
    if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
      String s = i.getData().toString();
      if (s.startsWith("http")) return s;
    }
    return null;
  }
  @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); String u = urlFromIntent(i); if (u != null) newTab(u, false); }

  /* ================================================================ appearance */
  int parseColor(String s, int def) { try { return Color.parseColor(s); } catch (Exception e) { return def; } }
  String mode() { return prefs.getString("mode", "dark"); }
  String wall() { return prefs.getString("wallpaper", "none"); }

  boolean caps = true;
  float luma(int c) { return (0.2126f*((c>>16)&255) + 0.7152f*((c>>8)&255) + 0.0722f*(c&255)) / 255f; }
  int mix(int a, int b, float t) {
    int ar=(a>>16)&255, ag=(a>>8)&255, ab=a&255, br=(b>>16)&255, bg=(b>>8)&255, bb=b&255;
    int r=Math.round(ar+(br-ar)*t), g=Math.round(ag+(bg-ag)*t), bl=Math.round(ab+(bb-ab)*t);
    return 0xFF000000 | (r<<16) | (g<<8) | bl;
  }
  boolean sysNight() { return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES; }

  void computePalette() {
    String m = mode();
    glass = m.equals("glass");
    caps = !prefs.getString("caps", "on").equals("off");
    ACCENT = parseColor(prefs.getString("accent", "#1d9bf0"), 0xFF1D9BF0);
    String bgc = prefs.getString("bgCol", "");
    String eff = m.equals("system") ? (sysNight() ? "dark" : "light") : m;

    if (m.equals("custom") && !bgc.isEmpty()) {
      int bg = parseColor(bgc, 0xFF000000);
      boolean lightBase = luma(bg) > 0.5f;
      int ink = parseColor(prefs.getString("textCol", ""), lightBase ? 0xFF000000 : 0xFFFFFFFF);
      BG = bg; TEXT = ink; HOVER2 = mix(bg, ink, 0.12f);
      String lineC = prefs.getString("lineCol", "");
      int line = lineC.isEmpty() ? mix(bg, ink, 0.28f) : parseColor(lineC, 0xFF434343);
      LINE = line; LINE2 = mix(line, ink, 0.35f); LINE3 = mix(line, bg, 0.55f);
      DIM = mix(ink, bg, 0.40f); DIM2 = mix(ink, bg, 0.60f);
      BAR = bg;
    } else if (eff.equals("light")) {
      BG = 0xFFFFFFFF; HOVER2 = 0xFFE8E8E8;
      LINE = 0xFFC4C4C4; LINE2 = 0xFF8F8F8F; LINE3 = 0xFFE2E2E2;
      TEXT = 0xFF000000; DIM = 0xFF666666; DIM2 = 0xFF8A8A8A;
      BAR = 0xFFFFFFFF;
    } else {
      BG = 0xFF000000; HOVER2 = 0xFF141414;
      LINE = 0xFF434343; LINE2 = 0xFF666666; LINE3 = 0xFF232323;
      TEXT = 0xFFFFFFFF; DIM = 0xFF999999; DIM2 = 0xFF6F6F6F;
      BAR = 0xFF000000;
    }
    if (glass) BAR = alpha(BG, 0xCC);   // translucent bars over the page/wallpaper
  }
  int radiusPx() { int r = 0; try { r = Integer.parseInt(prefs.getString("radius", "0")); } catch (Exception e) {} return dp(Math.max(0, Math.min(24, r))); }
  int textZoom() { String f = prefs.getString("fs", "m"); return f.equals("s") ? 90 : f.equals("l") ? 115 : 100; }

  void applyAppearance() {
    computePalette();
    if (root != null) root.setBackgroundColor(BG);
    if (container != null) container.setBackgroundColor(BG);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(glass ? BG : BAR);

    int barBg = glass ? BAR : BG;
    if (bottomWrap != null) bottomWrap.setBackgroundColor(barBg);
    if (addrRow != null) addrRow.setBackgroundColor(0);
    if (navRow != null) navRow.setBackgroundColor(0);
    if (addrRowLine != null) addrRowLine.setBackgroundColor(LINE3);
    if (navRowLine != null) navRowLine.setBackgroundColor(LINE3);
    if (progress != null) progress.setBackgroundColor(ACCENT);

    if (addr != null) {
      addr.setTextColor(TEXT); addr.setHintTextColor(DIM2);
      GradientDrawable pill = new GradientDrawable();
      pill.setColor(glass ? alpha(TEXT, 0x14) : HOVER2);
      pill.setCornerRadius(radiusPx());
      pill.setStroke(dp(1), LINE3);
      addr.setBackground(pill);
      addr.setPadding(dp(12), dp(6), dp(12), dp(6));
    }
    Icon[] all = { lockIc, reloadIc, backB, fwdB, tabsB, acctB, menuB };
    for (Icon ic : all) if (ic != null) ic.tint(DIM);
    // Text size → content zoom on every tab
    int tz = textZoom();
    for (Tab tt : tabs) if (tt.wv != null) tt.wv.getSettings().setTextZoom(tz);
    renderChrome();
    // Speed Dial reflects the new look
    Tab t = at();
    if (t != null && t.wv != null && t.url != null && t.url.startsWith("file:")) t.wv.reload();
  }

  String appearanceJson() {
    JSONObject o = new JSONObject();
    try {
      o.put("mode", mode()); o.put("accent", prefs.getString("accent", "#1d9bf0")); o.put("wallpaper", wall());
      o.put("fs", prefs.getString("fs", "m")); o.put("radius", prefs.getString("radius", "0"));
      o.put("caps", prefs.getString("caps", "on"));
      o.put("bgCol", prefs.getString("bgCol", "")); o.put("textCol", prefs.getString("textCol", "")); o.put("lineCol", prefs.getString("lineCol", ""));
    } catch (Exception e) {}
    return o.toString();
  }

  /* ================================================================ tabs */
  Tab at() { return cur >= 0 && cur < tabs.size() ? tabs.get(cur) : null; }
  WebView wvOf(Tab t) { return t == null ? null : t.wv; }

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
    else if (i < cur) cur--;
    activate(cur);
  }
  void navigate(String url) {
    Tab t = at();
    if (t == null) { newTab(url, false); return; }
    t.url = url; t.wv.loadUrl(url);
  }
  void openKonekt() {
    for (int i = 0; i < tabs.size(); i++) { String u = tabs.get(i).url; if (u != null && u.startsWith(KONEKT_URL)) { activate(i); return; } }
    newTab(KONEKT_URL, false);
  }

  /* ================================================================ URL logic */
  String engineKey() { return prefs.getString("engine", "google"); }
  String[] engine() { String k = engineKey(); for (String[] e : ENGINES) if (e[0].equals(k)) return e; return ENGINES[0]; }
  String toURL(String q) {
    q = q.trim();
    if (q.isEmpty()) return START;
    if (q.equals("konekt://start")) return START;
    String l = q.toLowerCase(Locale.US);
    if (l.startsWith("http://") || l.startsWith("https://") || l.startsWith("file:") || l.startsWith("data:") || l.startsWith("about:")) return q;
    if (l.matches("^localhost(:\\d+)?([/?#].*)?$")) return "http://" + q;
    if (l.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?([/?#].*)?$")) return "http://" + q;
    if (!q.contains(" ") && l.matches("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?([/?#].*)?$")) return "https://" + q;
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
    s.setTextZoom(textZoom());
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
    wv.addJavascriptInterface(new KBridge(t), "KB");
    wv.setDownloadListener((url, ua, cd, mt, len) -> download(url, ua, cd, mt));

    wv.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
        String u = req.getUrl().toString();
        if (u.startsWith("http:") || u.startsWith("https:") || u.startsWith("file:") || u.startsWith("data:") || u.startsWith("about:")) return false;
        try {
          Intent it = u.startsWith("intent:") ? Intent.parseUri(u, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(u));
          it.addCategory(Intent.CATEGORY_BROWSABLE); it.setComponent(null);
          if (it.resolveActivity(getPackageManager()) != null) startActivity(it);
        } catch (Exception ignored) {}
        return true;
      }
      @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap f) { t.url = url; if (view == wvOf(at())) renderChrome(); }
      @Override public void onPageFinished(WebView view, String url) {
        t.url = url;
        if (t.title == null || t.title.isEmpty()) t.title = view.getTitle();
        recordVisit(url, view.getTitle());
        if (view == wvOf(at())) { renderChrome(); setLoad(100); }
        saveSession();
      }
      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
        if (adblockOn) {
          String host = req.getUrl().getHost();
          if (host != null) { String h = host.toLowerCase(Locale.US);
            for (String d : ADBLOCK) if (h.equals(d) || h.endsWith("." + d)) { adBlocked++; return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0])); } }
        }
        return null;
      }
      @Override public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError err) {
        if (Build.VERSION.SDK_INT >= 23 && req.isForMainFrame()) {
          String u = req.getUrl().toString();
          view.loadDataWithBaseURL(null, errPage(u, String.valueOf(err.getDescription())), "text/html", "utf-8", u);
        }
      }
    });

    wv.setWebChromeClient(new WebChromeClient() {
      @Override public void onProgressChanged(WebView view, int p) { if (view == wvOf(at())) setLoad(p); }
      @Override public void onReceivedTitle(WebView view, String title) { t.title = title; if (view == wvOf(at())) renderChrome(); }
      @Override public boolean onCreateWindow(WebView view, boolean d, boolean userGesture, Message resultMsg) {
        if (!userGesture) return false;
        Tab nt = newTab(null, false);
        WebView.WebViewTransport tr = (WebView.WebViewTransport) resultMsg.obj;
        tr.setWebView(nt.wv); resultMsg.sendToTarget(); return true;
      }
      @Override public void onCloseWindow(WebView view) { for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).wv == view) { closeTab(i); return; } }
      @Override public void onShowCustomView(View v, CustomViewCallback cb) {
        if (customView != null) { cb.onCustomViewHidden(); return; }
        customView = v; customCb = cb;
        fsHolder.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fsHolder.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
      }
      @Override public void onHideCustomView() { exitFullscreen(); }
      @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
          askSite(origin + " wants your location", () -> cb.invoke(origin, true, true), () -> cb.invoke(origin, false, false));
        else { geoCb = cb; geoOrigin = origin; requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 71); }
      }
      @Override public void onPermissionRequest(PermissionRequest req) {
        List<String> need = new ArrayList<>();
        for (String r : req.getResources()) {
          if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need.add(android.Manifest.permission.CAMERA);
          if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(android.Manifest.permission.RECORD_AUDIO);
        }
        if (need.isEmpty()) askSite(req.getOrigin().getHost() + " wants camera or microphone", () -> req.grant(req.getResources()), req::deny);
        else { mediaReq = req; requestPermissions(need.toArray(new String[0]), 72); }
      }
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        try { startActivityForResult(params.createIntent(), 73); } catch (Exception e) { fileCb = null; return false; }
        return true;
      }
      @Override public boolean onJsAlert(WebView v, String url, String m, final android.webkit.JsResult r) {
        new AlertDialog.Builder(BrowserActivity.this).setMessage(m).setPositiveButton("OK", (d, x) -> r.confirm()).setOnCancelListener(d -> r.cancel()).show(); return true; }
      @Override public boolean onJsConfirm(WebView v, String url, String m, final android.webkit.JsResult r) {
        new AlertDialog.Builder(BrowserActivity.this).setMessage(m).setPositiveButton("OK", (d, x) -> r.confirm()).setNegativeButton("Cancel", (d, x) -> r.cancel()).setOnCancelListener(d -> r.cancel()).show(); return true; }
    });
    return wv;
  }

  void exitFullscreen() {
    if (customView == null) return;
    fsHolder.removeView(customView); fsHolder.setVisibility(View.GONE); customView = null;
    if (customCb != null) { customCb.onCustomViewHidden(); customCb = null; }
    getWindow().getDecorView().setSystemUiVisibility(0);
  }
  void askSite(String what, Runnable yes, Runnable no) {
    new AlertDialog.Builder(this).setMessage(what).setPositiveButton("Allow", (d, x) -> yes.run()).setNegativeButton("Block", (d, x) -> no.run()).setOnCancelListener(d -> no.run()).show();
  }
  String errPage(String url, String desc) {
    return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
      + "<body style='background:#000;color:#fff;font-family:sans-serif;padding:40px 24px'>"
      + "<h2 style='font-weight:800'>Can&rsquo;t reach this page</h2>"
      + "<div style='color:#999;font-size:13px;word-break:break-all;margin:10px 0'>" + esc(url) + "</div>"
      + "<div style='color:#999;font-size:13px;font-family:monospace'>" + esc(desc) + "</div>"
      + "<div style='margin-top:26px'><a href='" + esc(url) + "' style='color:#000;background:#fff;padding:10px 22px;text-decoration:none;font-weight:700;font-size:12px'>RETRY</a></div></body>";
  }
  static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

  /* ================================================================ chrome state */
  void renderChrome() {
    Tab t = at();
    if (t == null || addr == null) return;
    boolean start = t.url == null || t.url.startsWith("file:///android_asset");
    if (!addr.hasFocus()) addr.setText(start ? "" : t.url);
    lockIc.setType(start ? Icon.GLOBE : (t.url.startsWith("https:") ? Icon.LOCK : Icon.UNLOCK));
    lockIc.tint(start ? DIM : (t.url.startsWith("https:") ? TEXT : 0xFFFFD400));
    backB.setAlpha(t.wv != null && t.wv.canGoBack() ? 1f : .3f);
    fwdB.setAlpha(t.wv != null && t.wv.canGoForward() ? 1f : .3f);
    tabsB.setCount(tabs.size());
    acctB.setType(prefs.getString("token", null) != null ? Icon.PERSON_ON : Icon.PERSON);
    acctB.tint(prefs.getString("token", null) != null ? ACCENT : DIM);
  }
  void setLoad(int p) {
    if (progress == null) return;
    ViewGroup.LayoutParams lp = progress.getLayoutParams();
    int w = container.getWidth() > 0 ? container.getWidth() : getResources().getDisplayMetrics().widthPixels;
    lp.width = p >= 100 ? 0 : (int) (w * (p / 100f));
    progress.setLayoutParams(lp);
  }
  void showKeyboard() { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.showSoftInput(addr, InputMethodManager.SHOW_IMPLICIT); }
  void hideKeyboard() { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.hideSoftInputFromWindow(addr.getWindowToken(), 0); addr.clearFocus(); }

  /* ================================================================ downloads */
  void download(String url, String ua, String cd, String mt) {
    if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 74);
      Toast.makeText(this, "Storage access needed — tap the download again", Toast.LENGTH_LONG).show(); return;
    }
    try {
      String name = URLUtil.guessFileName(url, cd, mt);
      DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
      r.setMimeType(mt); r.addRequestHeader("User-Agent", ua);
      String cookie = CookieManager.getInstance().getCookie(url); if (cookie != null) r.addRequestHeader("Cookie", cookie);
      r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
      ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
      Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
    } catch (Exception e) { Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show(); }
  }

  /* ================================================================ prefs: session, visits, dials, bookmarks */
  void saveSession() {
    try { JSONArray a = new JSONArray(); for (Tab t : tabs) a.put(t.url == null ? START : t.url);
      prefs.edit().putString("session", a.toString()).putInt("cur", cur).apply(); } catch (Exception ignored) {}
  }
  void restoreSession() {
    try { String s = prefs.getString("session", null); if (s == null) return;
      JSONArray a = new JSONArray(s); int want = prefs.getInt("cur", 0);
      for (int i = 0; i < a.length() && i < 12; i++) newTab(a.getString(i), true);
      if (!tabs.isEmpty()) activate(Math.max(0, Math.min(want, tabs.size() - 1))); } catch (Exception ignored) {}
  }
  void recordVisit(String url, String title) {
    if (url == null || !url.startsWith("http")) return;
    try {
      Uri u = Uri.parse(url); String host = u.getHost(); if (host == null) return;
      host = host.replaceFirst("^www\\.", "");
      JSONObject all = new JSONObject(prefs.getString("hosts", "{}"));
      JSONObject h = all.optJSONObject(host); if (h == null) h = new JSONObject();
      h.put("n", h.optInt("n") + 1); h.put("u", u.getScheme() + "://" + u.getHost());
      if (title != null && !title.isEmpty()) h.put("t", title);
      all.put(host, h); prefs.edit().putString("hosts", all.toString()).apply();
    } catch (Exception ignored) {}
  }
  String topSitesJson() {
    try {
      JSONObject all = new JSONObject(prefs.getString("hosts", "{}"));
      List<String> keys = new ArrayList<>(); Iterator<String> it = all.keys(); while (it.hasNext()) keys.add(it.next());
      keys.sort((x, y) -> all.optJSONObject(y).optInt("n") - all.optJSONObject(x).optInt("n"));
      JSONArray out = new JSONArray();
      for (int i = 0; i < keys.size() && i < 6; i++) { JSONObject h = all.optJSONObject(keys.get(i)); JSONObject o = new JSONObject(); o.put("host", keys.get(i)); o.put("u", h.optString("u")); out.put(o); }
      return out.toString();
    } catch (Exception e) { return "[]"; }
  }

  /* ================================================================ JS bridge (Speed Dial) */
  class KBridge {
    final Tab tab; KBridge(Tab t) { tab = t; }
    boolean onStart() { return tab.url != null && tab.url.startsWith("file:///android_asset"); }
    @JavascriptInterface public void go(final String q) { if (onStart()) ui.post(() -> navigate(toURL(q))); }
    @JavascriptInterface public String dials() { return onStart() ? prefs.getString("dials", "[]") : "[]"; }
    @JavascriptInterface public String topSites() { return onStart() ? topSitesJson() : "[]"; }
    @JavascriptInterface public String engineName() { return engine()[1]; }
    @JavascriptInterface public String appearance() { return onStart() ? appearanceJson() : "{}"; }
    @JavascriptInterface public void addDial(String u, String t) {
      if (!onStart() || u == null || u.trim().isEmpty()) return;
      try { String url = u.trim(); if (!url.matches("(?i)^[a-z]+:.*")) url = "https://" + url;
        JSONArray a = new JSONArray(prefs.getString("dials", "[]")); JSONObject o = new JSONObject();
        o.put("u", url); o.put("t", (t == null || t.trim().isEmpty()) ? Uri.parse(url).getHost() : t.trim());
        a.put(o); prefs.edit().putString("dials", a.toString()).apply(); schedulePush(); } catch (Exception ignored) {}
    }
    @JavascriptInterface public void delDial(String u) {
      if (!onStart()) return;
      try { JSONArray a = new JSONArray(prefs.getString("dials", "[]")); JSONArray b = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!a.getJSONObject(i).optString("u").equals(u)) b.put(a.getJSONObject(i));
        prefs.edit().putString("dials", b.toString()).apply(); schedulePush(); } catch (Exception ignored) {}
    }
  }

  /* ================================================================ bottom sheets */
  void dismissSheet() { if (sheet != null) { root.removeView(sheet); sheet = null; } }
  LinearLayout openSheet(String title) {
    dismissSheet();
    sheet = new LinearLayout(this);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setBackgroundColor(BG);
    sheet.setOnClickListener(v -> {});   // swallow
    // header
    LinearLayout head = new LinearLayout(this);
    head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(dp(16), dp(12), dp(6), dp(10));
    TextView h = new TextView(this); h.setText(title); h.setTextColor(DIM); h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); h.setLetterSpacing(.16f);
    h.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    head.addView(h);
    Icon x = new Icon(this, Icon.X); x.tint(DIM); x.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(40)));
    x.setOnClickListener(v -> dismissSheet()); head.addView(x);
    View top = new View(this); top.setBackgroundColor(LINE);
    sheet.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    sheet.addView(head);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    root.addView(sheet, lp);
    return sheet;
  }
  TextView row(String label, View.OnClickListener fn) { return row(label, null, fn); }
  TextView row(String label, String sub, View.OnClickListener fn) {
    TextView tv = new TextView(this);
    tv.setText(label); tv.setTextColor(0xFFFFFFFF & TEXT); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    tv.setPadding(dp(18), dp(14), dp(18), dp(14));
    tv.setTextColor(TEXT);
    if (sub != null) { tv.setText(label + "   " + sub); }
    tv.setOnClickListener(fn);
    return tv;
  }
  TextView label(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(DIM); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10); tv.setLetterSpacing(.16f); tv.setPadding(dp(18), dp(14), dp(18), dp(6)); return tv; }
  ScrollView scroller(LinearLayout content) { ScrollView sc = new ScrollView(this); sc.addView(content); return sc; }

  void showTabs() {
    LinearLayout s = openSheet("TABS");
    LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
    TextView nt = row("+  New tab", v -> { dismissSheet(); newTab(START, false); });
    nt.setTypeface(Typeface.DEFAULT_BOLD); list.addView(nt);
    View sep = new View(this); sep.setBackgroundColor(LINE3); list.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    for (int i = 0; i < tabs.size(); i++) {
      final int idx = i; Tab t = tabs.get(i);
      LinearLayout rowv = new LinearLayout(this); rowv.setOrientation(LinearLayout.HORIZONTAL); rowv.setGravity(Gravity.CENTER_VERTICAL);
      if (i == cur) rowv.setBackgroundColor(HOVER2);
      LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL);
      txt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      boolean start = t.url == null || t.url.startsWith("file:");
      TextView title = new TextView(this); title.setText(start ? "Speed Dial" : (t.title == null || t.title.isEmpty() ? "New tab" : t.title));
      title.setTextColor(TEXT); title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); title.setTypeface(Typeface.DEFAULT_BOLD); title.setSingleLine(true);
      TextView sub = new TextView(this); sub.setText(start ? "konekt://start" : t.url); sub.setTextColor(DIM); sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); sub.setSingleLine(true);
      txt.addView(title); txt.addView(sub); txt.setPadding(dp(18), dp(11), dp(8), dp(11));
      txt.setOnClickListener(v -> { dismissSheet(); activate(idx); });
      rowv.addView(txt);
      Icon cx = new Icon(this, Icon.X); cx.tint(DIM2); cx.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(48)));
      cx.setOnClickListener(v -> { closeTab(idx); showTabs(); });
      rowv.addView(cx); list.addView(rowv);
    }
    ScrollView sc = scroller(list); sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
    s.addView(sc);
  }

  void showMenu() {
    LinearLayout s = openSheet("MENU");
    LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
    Tab t = at(); boolean onPage = t != null && t.url != null && t.url.startsWith("http");
    list.addView(row("New tab", v -> { dismissSheet(); newTab(START, false); }));
    list.addView(row("Speed Dial", v -> { dismissSheet(); navigate(START); }));
    list.addView(row("KONEKT", v -> { dismissSheet(); openKonekt(); }));
    if (onPage) {
      final String pageUrl = t.url, pageTitle = t.title;
      list.addView(row("Add to bookmarks", v -> { dismissSheet(); addBookmark(pageUrl, pageTitle); }));
      list.addView(row("Share page", v -> { dismissSheet(); Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, pageUrl); startActivity(Intent.createChooser(i, "Share")); }));
    }
    list.addView(row("Bookmarks", v -> { dismissSheet(); showBookmarks(); }));
    boolean desk = t != null && t.desktop;
    list.addView(row(desk ? "Mobile site" : "Desktop site", v -> { dismissSheet(); toggleDesktop(); }));
    list.addView(row("Ad blocker", (adblockOn ? "On · " + adBlocked : "Off"), v -> { adblockOn = !adblockOn; prefs.edit().putBoolean("adblock", adblockOn).apply(); dismissSheet(); Toast.makeText(this, adblockOn ? "Ad blocker on" : "Ad blocker off", Toast.LENGTH_SHORT).show(); }));
    list.addView(label("PERSONALISE"));
    list.addView(row("Customize appearance", v -> { dismissSheet(); showCustomize(); }));
    list.addView(row("Settings", v -> { dismissSheet(); showSettings(); }));
    list.addView(row("Account", v -> { dismissSheet(); showAccount(); }));
    ScrollView sc = scroller(list); sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));
    s.addView(sc);
  }

  void toggleDesktop() {
    Tab t = at(); if (t == null || t.wv == null) return;
    t.desktop = !t.desktop;
    t.wv.getSettings().setUserAgentString(t.desktop ? defaultUA.replace("Mobile ", "").replace("Android", "X11; Linux x86_64") : null);
    t.wv.reload();
  }

  /* ---- bookmarks ---- */
  void addBookmark(String u, String title) {
    try { JSONArray a = new JSONArray(prefs.getString("bookmarks", "[]"));
      for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).optString("u").equals(u)) { Toast.makeText(this, "Already bookmarked", Toast.LENGTH_SHORT).show(); return; }
      JSONObject o = new JSONObject(); o.put("u", u); o.put("t", title == null ? u : title); a.put(o);
      prefs.edit().putString("bookmarks", a.toString()).apply(); schedulePush(); Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show();
    } catch (Exception ignored) {}
  }
  void showBookmarks() {
    LinearLayout s = openSheet("BOOKMARKS");
    LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
    try {
      JSONArray a = new JSONArray(prefs.getString("bookmarks", "[]"));
      if (a.length() == 0) { TextView e = new TextView(this); e.setText("No bookmarks yet"); e.setTextColor(DIM2); e.setPadding(dp(18), dp(30), dp(18), dp(30)); e.setGravity(Gravity.CENTER); list.addView(e); }
      for (int i = a.length() - 1; i >= 0; i--) {
        final JSONObject o = a.getJSONObject(i); final String u = o.optString("u");
        LinearLayout rowv = new LinearLayout(this); rowv.setOrientation(LinearLayout.HORIZONTAL); rowv.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(this); tv.setText(o.optString("t", u)); tv.setTextColor(TEXT); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); tv.setSingleLine(true);
        tv.setPadding(dp(18), dp(13), dp(8), dp(13)); tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tv.setOnClickListener(v -> { dismissSheet(); newTab(u, false); });
        Icon del = new Icon(this, Icon.X); del.tint(DIM2); del.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        del.setOnClickListener(v -> { delBookmark(u); showBookmarks(); });
        rowv.addView(tv); rowv.addView(del); list.addView(rowv);
      }
    } catch (Exception ignored) {}
    ScrollView sc = scroller(list); sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)));
    s.addView(sc);
  }
  void delBookmark(String u) {
    try { JSONArray a = new JSONArray(prefs.getString("bookmarks", "[]")); JSONArray b = new JSONArray();
      for (int i = 0; i < a.length(); i++) if (!a.getJSONObject(i).optString("u").equals(u)) b.put(a.getJSONObject(i));
      prefs.edit().putString("bookmarks", b.toString()).apply(); schedulePush(); } catch (Exception ignored) {}
  }

  /* ---- customize (mode / accent / wallpaper) ---- */
  void showCustomize() {
    LinearLayout s = openSheet("CUSTOMIZE");
    LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);

    body.addView(label("MODE"));
    LinearLayout seg = new LinearLayout(this); seg.setOrientation(LinearLayout.HORIZONTAL); seg.setPadding(dp(14), 0, dp(14), dp(6));
    String[][] modes = {{"dark","Dark"},{"light","Light"},{"system","System"},{"glass","Glass"},{"geek","Geek"}};
    for (String[] m : modes) {
      final String key = m[0];
      TextView bt = new TextView(this); bt.setText(m[1]); bt.setGravity(Gravity.CENTER); bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
      bt.setTypeface(Typeface.DEFAULT_BOLD); bt.setAllCaps(true); bt.setPadding(dp(4), dp(11), dp(4), dp(11));
      boolean on = mode().equals(key);
      GradientDrawable g = new GradientDrawable(); g.setColor(0); g.setStroke(dp(1), on ? TEXT : LINE); bt.setBackground(g);
      bt.setTextColor(on ? TEXT : DIM);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.setMargins(dp(3), 0, dp(3), 0); bt.setLayoutParams(lp);
      bt.setOnClickListener(v -> { prefs.edit().putString("mode", key).apply(); applyAppearance(); showCustomize(); schedulePush(); });
      seg.addView(bt);
    }
    body.addView(seg);

    // Text size
    body.addView(label("TEXT SIZE"));
    LinearLayout fsSeg = new LinearLayout(this); fsSeg.setOrientation(LinearLayout.HORIZONTAL); fsSeg.setPadding(dp(14), 0, dp(14), dp(6));
    String[][] fss = {{"s","S"},{"m","M"},{"l","L"}};
    for (String[] f : fss) {
      final String key = f[0];
      TextView bt = new TextView(this); bt.setText(f[1]); bt.setGravity(Gravity.CENTER); bt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
      bt.setTypeface(Typeface.DEFAULT_BOLD); bt.setPadding(dp(6), dp(11), dp(6), dp(11));
      boolean on = prefs.getString("fs", "m").equals(key);
      GradientDrawable g = new GradientDrawable(); g.setColor(0); g.setStroke(dp(1), on ? TEXT : LINE); bt.setBackground(g);
      bt.setTextColor(on ? TEXT : DIM);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.setMargins(dp(3), 0, dp(3), 0); bt.setLayoutParams(lp);
      bt.setOnClickListener(v -> { prefs.edit().putString("fs", key).apply(); applyAppearance(); showCustomize(); schedulePush(); });
      fsSeg.addView(bt);
    }
    body.addView(fsSeg);

    // Corner rounding
    LinearLayout radHead = new LinearLayout(this); radHead.setOrientation(LinearLayout.HORIZONTAL); radHead.setGravity(Gravity.CENTER_VERTICAL);
    TextView rl = label("CORNER ROUNDING"); rl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); radHead.addView(rl);
    int radNow = 0; try { radNow = Integer.parseInt(prefs.getString("radius", "0")); } catch (Exception e) {}
    final TextView radVal = new TextView(this); radVal.setText(radNow + "px"); radVal.setTextColor(DIM); radVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); radVal.setPadding(0, 0, dp(18), 0);
    radHead.addView(radVal); body.addView(radHead);
    android.widget.SeekBar sb = new android.widget.SeekBar(this); sb.setMax(24); sb.setProgress(radNow);
    sb.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
    sb.getThumb().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
    LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); sblp.setMargins(dp(16), 0, dp(16), dp(6)); sb.setLayoutParams(sblp);
    sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(android.widget.SeekBar s2, int p, boolean fromUser) { int q = (p/2)*2; radVal.setText(q + "px"); prefs.edit().putString("radius", String.valueOf(q)).apply(); applyAppearance(); }
      public void onStartTrackingTouch(android.widget.SeekBar s2) {}
      public void onStopTrackingTouch(android.widget.SeekBar s2) { schedulePush(); }
    });
    body.addView(sb);

    // Uppercase interface
    boolean capsOn = !prefs.getString("caps", "on").equals("off");
    body.addView(row("Uppercase interface", capsOn ? "On" : "Off", v -> {
      prefs.edit().putString("caps", capsOn ? "off" : "on").apply(); applyAppearance(); showCustomize(); schedulePush();
    }));

    body.addView(label("ACCENT"));
    LinearLayout sw = new LinearLayout(this); sw.setOrientation(LinearLayout.HORIZONTAL); sw.setPadding(dp(16), dp(2), dp(16), dp(10));
    String curAcc = prefs.getString("accent", "#1d9bf0").toLowerCase();
    for (String c : ACCENTS) {
      View dot = new View(this); int col = parseColor(c, 0xFF1D9BF0);
      GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(col);
      g.setStroke(dp(2), curAcc.equals(c) ? TEXT : 0); dot.setBackground(g);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28)); lp.setMargins(0, 0, dp(12), 0); dot.setLayoutParams(lp);
      dot.setOnClickListener(v -> { prefs.edit().putString("accent", c).apply(); applyAppearance(); showCustomize(); schedulePush(); });
      sw.addView(dot);
    }
    // custom accent via the RGB colour wheel
    View acw = new View(this);
    GradientDrawable ag = new GradientDrawable(); ag.setShape(GradientDrawable.OVAL); ag.setGradientType(GradientDrawable.SWEEP_GRADIENT);
    ag.setColors(new int[]{0xFFFF0000,0xFFFFFF00,0xFF00FF00,0xFF00FFFF,0xFF0000FF,0xFFFF00FF,0xFFFF0000});
    acw.setBackground(ag);
    LinearLayout.LayoutParams acwlp = new LinearLayout.LayoutParams(dp(28), dp(28)); acw.setLayoutParams(acwlp);
    acw.setOnClickListener(v -> openColorWheel(ACCENT, c -> { prefs.edit().putString("accent", String.format("#%06X", c & 0xFFFFFF)).apply(); applyAppearance(); showCustomize(); schedulePush(); }));
    sw.addView(acw);
    body.addView(sw);

    // Custom background — picking one switches the scheme to Custom and derives the palette
    body.addView(label("CUSTOM BACKGROUND"));
    LinearLayout bgs = new LinearLayout(this); bgs.setOrientation(LinearLayout.HORIZONTAL); bgs.setPadding(dp(16), dp(2), dp(16), dp(12));
    String[] BGCOLS = {"", "#0a0b0d", "#0d1b2a", "#12100b", "#0b1a12", "#1a1022", "#f4f1ea", "#ffffff"};
    String curBg = prefs.getString("bgCol", "");
    boolean isCustom = mode().equals("custom");
    for (String c : BGCOLS) {
      View dot = new View(this);
      GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
      if (c.isEmpty()) { g.setColor(0); g.setStroke(dp(1), LINE2); } else { g.setColor(parseColor(c, 0xFF000000)); }
      boolean on = c.isEmpty() ? !isCustom : (isCustom && curBg.equalsIgnoreCase(c));
      g.setStroke(dp(2), on ? TEXT : (c.isEmpty() ? LINE2 : 0));
      dot.setBackground(g);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28)); lp.setMargins(0, 0, dp(12), 0); dot.setLayoutParams(lp);
      final String col = c;
      dot.setOnClickListener(v -> {
        if (col.isEmpty()) prefs.edit().putString("mode", "dark").putString("bgCol", "").apply();
        else prefs.edit().putString("mode", "custom").putString("bgCol", col).apply();
        applyAppearance(); showCustomize(); schedulePush();
      });
      bgs.addView(dot);
    }
    // custom background via the RGB colour wheel
    View bcw = new View(this);
    GradientDrawable bgw = new GradientDrawable(); bgw.setShape(GradientDrawable.OVAL); bgw.setGradientType(GradientDrawable.SWEEP_GRADIENT);
    bgw.setColors(new int[]{0xFFFF0000,0xFFFFFF00,0xFF00FF00,0xFF00FFFF,0xFF0000FF,0xFFFF00FF,0xFFFF0000});
    bcw.setBackground(bgw);
    LinearLayout.LayoutParams bcwlp = new LinearLayout.LayoutParams(dp(28), dp(28)); bcw.setLayoutParams(bcwlp);
    bcw.setOnClickListener(v -> openColorWheel(parseColor(prefs.getString("bgCol", "#0a0b0d"), 0xFF0A0B0D), c -> { prefs.edit().putString("mode", "custom").putString("bgCol", String.format("#%06X", c & 0xFFFFFF)).apply(); applyAppearance(); showCustomize(); schedulePush(); }));
    bgs.addView(bcw);
    body.addView(bgs);

    body.addView(label("WALLPAPER"));
    LinearLayout walls = new LinearLayout(this); walls.setOrientation(LinearLayout.HORIZONTAL); walls.setPadding(dp(12), dp(2), dp(12), dp(14));
    String curW = wall();
    for (String[] wdef : WALLS) {
      final String key = wdef[0];
      TextView wv = new TextView(this); wv.setText(wdef[1]); wv.setGravity(Gravity.CENTER); wv.setTextColor(0xFFFFFFFF); wv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9); wv.setAllCaps(true);
      GradientDrawable g = wallDrawable(key); g.setStroke(dp(1), curW.equals(key) ? TEXT : LINE); wv.setBackground(g);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f); lp.setMargins(dp(3), 0, dp(3), 0); wv.setLayoutParams(lp);
      wv.setOnClickListener(v -> { prefs.edit().putString("wallpaper", key).apply(); applyAppearance(); showCustomize(); schedulePush(); });
      walls.addView(wv);
    }
    HorizontalScrollWrap(body, walls);

    ScrollView sc = scroller(body); sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
    s.addView(sc);
  }
  void HorizontalScrollWrap(LinearLayout body, View child) { body.addView(child); }
  GradientDrawable wallDrawable(String key) {
    GradientDrawable g = new GradientDrawable();
    int[] cols;
    switch (key) {
      case "aurora": cols = new int[]{0xFF10233b, 0xFF0a0a12}; break;
      case "ember":  cols = new int[]{0xFF2a0f0f, 0xFF0a0708}; break;
      case "mono":   cols = new int[]{0xFF1a1a1a, 0xFF000000}; break;
      case "teal":   cols = new int[]{0xFF06272b, 0xFF06131a}; break;
      case "violet": cols = new int[]{0xFF1a1040, 0xFF0a0816}; break;
      default:       cols = new int[]{HOVER2, BG}; break;
    }
    g.setColors(cols); g.setGradientType(GradientDrawable.RADIAL_GRADIENT); g.setGradientRadius(dp(80));
    return g;
  }

  /* ---- settings (engine / clear / update / about) ---- */
  void showSettings() {
    LinearLayout s = openSheet("SETTINGS");
    LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
    body.addView(label("SEARCH ENGINE"));
    for (String[] e : ENGINES) {
      final String key = e[0];
      TextView r = row(e[1] + (engineKey().equals(key) ? "   ✓" : ""), v -> { prefs.edit().putString("engine", key).apply(); applyAppearance(); showSettings(); schedulePush(); });
      body.addView(r);
    }
    body.addView(label("PRIVACY"));
    body.addView(row("Clear browsing data", v -> {
      new AlertDialog.Builder(this).setMessage("Clear cookies, cache and site data?")
        .setPositiveButton("Clear", (d, x) -> {
          CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush();
          WebStorage.getInstance().deleteAllData();
          for (Tab tt : tabs) if (tt.wv != null) tt.wv.clearCache(true);
          prefs.edit().remove("hosts").apply(); Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Cancel", null).show();
    }));
    body.addView(label("SOFTWARE"));
    body.addView(row("Check for updates", "v" + versionName(), v -> checkUpdates()));
    body.addView(row("About", v -> {
      new AlertDialog.Builder(this).setMessage("KONEKT Browser " + versionName() + " for Android\nAndroid System WebView engine\n\nkonekt-browser.vercel.app\n© 2026 KONEKT · NKO Intl. Foundation of Technological Research & Development").setPositiveButton("OK", null).show();
    }));
    ScrollView sc = scroller(body); sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));
    s.addView(sc);
  }
  String versionName() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1.0.0"; } }

  /* ---- account + sync ---- */
  boolean create = false;
  void showAccount() {
    LinearLayout s = openSheet("ACCOUNT");
    String token = prefs.getString("token", null);
    LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18), dp(4), dp(18), dp(22));
    if (token != null) {
      String handle = prefs.getString("handle", ""), name = prefs.getString("name", handle);
      TextView nm = new TextView(this); nm.setText(name); nm.setTextColor(TEXT); nm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17); nm.setTypeface(Typeface.DEFAULT_BOLD); body.addView(nm);
      TextView hd = new TextView(this); hd.setText("@" + handle); hd.setTextColor(DIM); hd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13); hd.setPadding(0, dp(2), 0, dp(12)); body.addView(hd);
      TextView sy = new TextView(this); sy.setText("Bookmarks, Speed Dial and settings sync to your account."); sy.setTextColor(DIM); sy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); sy.setPadding(0, 0, 0, dp(14)); body.addView(sy);
      LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
      r.addView(pillBtn("Sync now", false, v -> { Toast.makeText(this, "Syncing…", Toast.LENGTH_SHORT).show(); doPush(); doPull(true); }));
      r.addView(pillBtn("Sign out", true, v -> { prefs.edit().remove("token").remove("handle").remove("name").apply(); renderChrome(); dismissSheet(); Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show(); }));
      body.addView(r);
      s.addView(body); return;
    }
    // signed out — tabs
    LinearLayout tabsRow = new LinearLayout(this); tabsRow.setOrientation(LinearLayout.HORIZONTAL);
    tabsRow.addView(authTab("Sign in", !create));
    tabsRow.addView(authTab("Create account", create));
    body.addView(tabsRow);
    final EditText handle = field("Handle", InputType.TYPE_CLASS_TEXT);
    final EditText name = create ? field("Display name (optional)", InputType.TYPE_CLASS_TEXT) : null;
    final EditText pass = field("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    pass.setTransformationMethod(new android.text.method.PasswordTransformationMethod());
    body.addView(handle); if (name != null) body.addView(name); body.addView(pass);
    final TextView msg = new TextView(this); msg.setTextColor(DIM); msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f); msg.setPadding(0, dp(6), 0, dp(8));
    msg.setText(create ? "Handles are 3–20 chars: a–z, 0–9, underscore." : ""); body.addView(msg);
    TextView go = pillBtn(create ? "Create account" : "Sign in", false, null); go.setBackgroundColor(TEXT); go.setTextColor(BG);
    go.setOnClickListener(v -> {
      String h = handle.getText().toString().trim().toLowerCase().replaceAll("^@", "");
      String p = pass.getText().toString();
      String nm = name != null ? name.getText().toString().trim() : "";
      if (h.isEmpty() || p.isEmpty()) { msg.setTextColor(0xFFF4212E); msg.setText("Handle and password required"); return; }
      msg.setTextColor(DIM); msg.setText(create ? "Creating…" : "Signing in…");
      authRequest(create ? "register" : "login", h, p, nm, msg);
    });
    LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); glp.setMargins(0, dp(6), 0, 0); go.setLayoutParams(glp);
    body.addView(go);
    s.addView(body);
  }
  TextView authTab(String text, boolean on) {
    TextView tv = new TextView(this); tv.setText(text.toUpperCase()); tv.setGravity(Gravity.CENTER);
    tv.setTextColor(on ? TEXT : DIM); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); tv.setTypeface(Typeface.DEFAULT_BOLD);
    tv.setPadding(dp(4), dp(10), dp(4), dp(12));
    View underline = new View(this);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); tv.setLayoutParams(lp);
    tv.setOnClickListener(v -> { create = text.toLowerCase().startsWith("create"); showAccount(); });
    return tv;
  }
  EditText field(String hint, int type) {
    EditText e = new EditText(this); e.setHint(hint); e.setInputType(type); e.setTextColor(TEXT); e.setHintTextColor(DIM2); e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13); e.setSingleLine(true);
    GradientDrawable g = new GradientDrawable(); g.setColor(0); g.setStroke(dp(1), LINE2); e.setBackground(g); e.setPadding(dp(12), dp(11), dp(12), dp(11));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, dp(8), 0, 0); e.setLayoutParams(lp);
    return e;
  }
  TextView pillBtn(String text, boolean danger, View.OnClickListener fn) {
    TextView tv = new TextView(this); tv.setText(text.toUpperCase()); tv.setGravity(Gravity.CENTER); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); tv.setTypeface(Typeface.DEFAULT_BOLD);
    tv.setPadding(dp(12), dp(12), dp(12), dp(12)); tv.setTextColor(TEXT);
    GradientDrawable g = new GradientDrawable(); g.setColor(0); g.setStroke(dp(1), danger ? LINE2 : LINE2); tv.setBackground(g);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.setMargins(dp(3), 0, dp(3), 0); tv.setLayoutParams(lp);
    if (fn != null) tv.setOnClickListener(fn);
    return tv;
  }

  /* ================================================================ networking (background threads) */
  void authRequest(final String action, final String handle, final String password, final String name, final TextView msg) {
    new Thread(() -> {
      try {
        JSONObject body = new JSONObject();
        body.put("action", action); body.put("handle", handle); body.put("password", password); if (name != null && !name.isEmpty()) body.put("name", name);
        String resp = http("POST", API_BASE + "/api/auth", null, body.toString());
        JSONObject j = new JSONObject(resp);
        if (!j.optBoolean("ok")) { final String err = j.optString("error", "Failed"); ui.post(() -> { msg.setTextColor(0xFFF4212E); msg.setText(err); }); return; }
        final String token = j.optString("token");
        final JSONObject user = j.optJSONObject("user");
        prefs.edit().putString("token", token).putString("handle", user.optString("handle", handle)).putString("name", user.optString("name", handle)).apply();
        ui.post(() -> { renderChrome(); Toast.makeText(this, "Signed in as @" + handle, Toast.LENGTH_SHORT).show(); doPull(true); doPushLater(); showAccount(); });
      } catch (Exception e) { ui.post(() -> { msg.setTextColor(0xFFF4212E); msg.setText(e.getMessage() == null ? "Network error" : e.getMessage()); }); }
    }).start();
  }

  JSONObject collectData() {
    JSONObject d = new JSONObject();
    try {
      d.put("bookmarks", new JSONArray(prefs.getString("bookmarks", "[]")));
      d.put("dials", new JSONArray(prefs.getString("dials", "[]")));
      JSONObject st = new JSONObject(); st.put("engine", engineKey()); st.put("adblock", adblockOn); d.put("settings", st);
      JSONObject ap = new JSONObject();
      ap.put("mode", mode()); ap.put("accent", prefs.getString("accent", "#1d9bf0")); ap.put("wallpaper", wall());
      ap.put("fs", prefs.getString("fs", "m"));
      int rad = 0; try { rad = Integer.parseInt(prefs.getString("radius", "0")); } catch (Exception e) {}
      ap.put("radius", rad);
      ap.put("caps", !prefs.getString("caps", "on").equals("off"));
      ap.put("bgCol", prefs.getString("bgCol", "")); ap.put("textCol", prefs.getString("textCol", "")); ap.put("lineCol", prefs.getString("lineCol", ""));
      d.put("appearance", ap);
    } catch (Exception ignored) {}
    return d;
  }
  void mergeIn(JSONObject d) {
    if (d == null) return;
    try {
      SharedPreferences.Editor ed = prefs.edit();
      if (d.has("bookmarks")) ed.putString("bookmarks", d.getJSONArray("bookmarks").toString());
      if (d.has("dials")) ed.putString("dials", d.getJSONArray("dials").toString());
      if (d.has("settings")) { JSONObject st = d.getJSONObject("settings"); if (st.has("engine")) ed.putString("engine", st.getString("engine")); if (st.has("adblock")) ed.putBoolean("adblock", st.getBoolean("adblock")); }
      if (d.has("appearance")) {
        JSONObject ap = d.getJSONObject("appearance");
        if (ap.has("mode")) ed.putString("mode", ap.getString("mode"));
        if (ap.has("accent")) ed.putString("accent", ap.getString("accent"));
        if (ap.has("wallpaper")) ed.putString("wallpaper", ap.getString("wallpaper"));
        if (ap.has("fs")) ed.putString("fs", ap.getString("fs"));
        if (ap.has("radius")) ed.putString("radius", String.valueOf(ap.optInt("radius", 0)));
        if (ap.has("caps")) ed.putString("caps", ap.optBoolean("caps", true) ? "on" : "off");
        if (ap.has("bgCol")) ed.putString("bgCol", ap.getString("bgCol"));
        if (ap.has("textCol")) ed.putString("textCol", ap.getString("textCol"));
        if (ap.has("lineCol")) ed.putString("lineCol", ap.getString("lineCol"));
      }
      ed.apply();
      adblockOn = prefs.getBoolean("adblock", true);
    } catch (Exception ignored) {}
  }
  void doPull(final boolean apply) {
    final String token = prefs.getString("token", null); if (token == null) return;
    new Thread(() -> {
      try {
        String resp = http("GET", API_BASE + "/api/sync", token, null);
        JSONObject j = new JSONObject(resp);
        if (j.optBoolean("ok")) { final JSONObject data = j.optJSONObject("data");
          ui.post(() -> { mergeIn(data); if (apply) applyAppearance(); Toast.makeText(this, "Synced", Toast.LENGTH_SHORT).show(); }); }
      } catch (Exception ignored) {}
    }).start();
  }
  void schedulePush() { if (prefs.getString("token", null) == null) return; doPushLater(); }
  void doPushLater() {
    if (pushPending != null) ui.removeCallbacks(pushPending);
    pushPending = this::doPush;
    ui.postDelayed(pushPending, 1400);
  }
  void doPush() {
    final String token = prefs.getString("token", null); if (token == null) return;
    final String payload;
    try { JSONObject body = new JSONObject(); body.put("data", collectData()); payload = body.toString(); } catch (Exception e) { return; }
    new Thread(() -> { try { http("PUT", API_BASE + "/api/sync", token, payload); } catch (Exception ignored) {} }).start();
  }

  boolean pendingInstall = false;
  void checkUpdates() { checkUpdates(false); }
  void checkUpdates(final boolean silent) {
    if (!silent) Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show();
    new Thread(() -> {
      try {
        String resp = http("GET", "https://api.github.com/repos/" + REPO + "/releases/latest", null, null);
        JSONObject j = new JSONObject(resp);
        final String latest = j.optString("tag_name", j.optString("name", ""));
        final String cur = versionName();
        ui.post(() -> {
          if (isNewer(latest, cur)) {
            new AlertDialog.Builder(this).setTitle("Update available")
              .setMessage("KONEKT Browser " + latest + " is available (you have " + cur + ").\n\nUpdate now? It'll download and open the installer.")
              .setPositiveButton("Update now", (d, x) -> startUpdate())
              .setNegativeButton("Later", null).show();
          } else if (!silent) {
            Toast.makeText(this, "You're on the latest version (" + cur + ")", Toast.LENGTH_LONG).show();
          }
        });
      } catch (Exception e) { if (!silent) ui.post(() -> Toast.makeText(this, "Couldn't check for updates", Toast.LENGTH_SHORT).show()); }
    }).start();
  }
  void startUpdate() {
    Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show();
    new Thread(() -> {
      try {
        String cur = "https://github.com/" + REPO + "/releases/latest/download/KONEKT-Browser-android.apk";
        HttpURLConnection c = null; java.io.InputStream in = null;
        for (int redir = 0; redir < 6; redir++) {
          c = (HttpURLConnection) new URL(cur).openConnection();
          c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(45000);
          c.setRequestProperty("User-Agent", "KONEKT-Browser-Android");
          int code = c.getResponseCode();
          if (code >= 300 && code < 400) { cur = c.getHeaderField("Location"); c.disconnect(); continue; }
          in = c.getInputStream(); break;
        }
        if (in == null) throw new Exception("no stream");
        java.io.FileOutputStream fo = new java.io.FileOutputStream(ApkProvider.file(this));
        byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
        fo.close(); in.close();
        ui.post(this::installApk);
      } catch (Exception e) { ui.post(() -> Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()); }
    }).start();
  }
  void installApk() {
    if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
      pendingInstall = true;
      Toast.makeText(this, "Allow installing apps for KONEKT Browser, then it continues", Toast.LENGTH_LONG).show();
      try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
      catch (Exception e) { try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)); } catch (Exception e2) {} }
      return;
    }
    pendingInstall = false;
    try {
      Uri uri = Uri.parse("content://network.konekt.browser.apk/" + ApkProvider.NAME);
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setDataAndType(uri, "application/vnd.android.package-archive");
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(i);
    } catch (Exception e) { Toast.makeText(this, "Install failed", Toast.LENGTH_SHORT).show(); }
  }
  boolean isNewer(String a, String b) {
    int[] A = ver(a), B = ver(b);
    for (int i = 0; i < 3; i++) { if (A[i] > B[i]) return true; if (A[i] < B[i]) return false; }
    return false;
  }
  int[] ver(String v) {
    int[] out = {0, 0, 0}; if (v == null) return out; v = v.replaceAll("^v", "");
    String[] p = v.split("\\."); for (int i = 0; i < 3 && i < p.length; i++) { try { out[i] = Integer.parseInt(p[i].replaceAll("[^0-9]", "")); } catch (Exception e) {} }
    return out;
  }

  String http(String method, String urlStr, String token, String body) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
    c.setRequestMethod(method);
    c.setConnectTimeout(12000); c.setReadTimeout(12000);
    c.setRequestProperty("Accept", "application/json");
    c.setRequestProperty("User-Agent", "KONEKT-Browser-Android");
    if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
    if (body != null) {
      c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
      OutputStream os = c.getOutputStream(); os.write(body.getBytes("UTF-8")); os.close();
    }
    int code = c.getResponseCode();
    java.io.InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
    if (in != null) { byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); }
    return bo.toString("UTF-8");
  }

  /* ================================================================ system */
  @Override public void onBackPressed() {
    if (customView != null) { exitFullscreen(); return; }
    if (sheet != null) { dismissSheet(); return; }
    Tab t = at();
    if (t != null && t.wv != null && t.wv.canGoBack()) { t.wv.goBack(); return; }
    if (tabs.size() > 1) { closeTab(cur); return; }
    moveTaskToBack(true);
  }
  @Override protected void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (req == 73 && fileCb != null) { fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); fileCb = null; }
  }
  @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
    super.onRequestPermissionsResult(req, perms, grants);
    boolean ok = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
    if (req == 71 && geoCb != null) { geoCb.invoke(geoOrigin, ok, ok); geoCb = null; }
    else if (req == 72 && mediaReq != null) { boolean all = grants.length > 0; for (int g : grants) if (g != PackageManager.PERMISSION_GRANTED) all = false; if (all) mediaReq.grant(mediaReq.getResources()); else mediaReq.deny(); mediaReq = null; }
  }
  @Override protected void onPause() { super.onPause(); saveSession(); CookieManager.getInstance().flush(); Tab t = at(); if (t != null && t.wv != null) t.wv.onPause(); }
  @Override protected void onResume() { super.onResume(); Tab t = at(); if (t != null && t.wv != null) t.wv.onResume();
    if (pendingInstall && (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls())) { pendingInstall = false; ui.postDelayed(this::installApk, 400); } }

  /* ================================================================ stroke icons */
  /* ================================================================ RGB / HSV colour wheel */
  interface OnColor { void onColor(int c); }
  static class ColorWheelView extends View {
    android.graphics.Bitmap bmp; float hue = 0, sat = 1, val = 1; int size = 0;
    OnColor cb;
    final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    ColorWheelView(Context c) { super(c); mark.setStyle(Paint.Style.STROKE); mark.setStrokeWidth(3.5f); }
    int color() { return Color.HSVToColor(new float[]{ hue, sat, val }); }
    void setColor(int col) { float[] h = new float[3]; Color.colorToHSV(col, h); hue = h[0]; sat = h[1]; val = h[2]; rebuild(); invalidate(); }
    void setValue(float v) { val = v; rebuild(); invalidate(); if (cb != null) cb.onColor(color()); }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { size = Math.min(w, h); rebuild(); }
    void rebuild() {
      if (size <= 0) return;
      int R = size / 2; float cx = R, cy = R; int[] px = new int[size * size];
      float[] hsv = new float[]{ 0, 0, val };
      for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
        float dx = x - cx, dy = y - cy; double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= R) { float hh = (float) (Math.atan2(dy, dx) * 180 / Math.PI); if (hh < 0) hh += 360;
          hsv[0] = hh; hsv[1] = (float) Math.min(1, dist / R); px[y * size + x] = Color.HSVToColor(hsv); }
        else px[y * size + x] = 0;
      }
      bmp = android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888);
    }
    @Override protected void onDraw(Canvas c) {
      if (bmp != null) c.drawBitmap(bmp, 0, 0, null);
      int R = size / 2; float mr = sat * R, mx = R + (float) Math.cos(Math.toRadians(hue)) * mr, my = R + (float) Math.sin(Math.toRadians(hue)) * mr;
      mark.setColor(0xFF000000); c.drawCircle(mx, my, 9, mark); mark.setColor(0xFFFFFFFF); c.drawCircle(mx, my, 7, mark);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
      int R = size / 2; float dx = e.getX() - R, dy = e.getY() - R; double dist = Math.sqrt(dx * dx + dy * dy); if (dist > R) dist = R;
      sat = (float) (dist / R); float hh = (float) (Math.atan2(dy, dx) * 180 / Math.PI); if (hh < 0) hh += 360; hue = hh;
      invalidate(); if (cb != null) cb.onColor(color());
      if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
      return true;
    }
  }
  void openColorWheel(int initial, final OnColor onFinal) {
    LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(18), dp(20), dp(6));
    final ColorWheelView wheel = new ColorWheelView(this);
    int sz = dp(230); LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(sz, sz); wlp.gravity = Gravity.CENTER_HORIZONTAL; wheel.setLayoutParams(wlp);
    box.addView(wheel);
    final android.widget.SeekBar bright = new android.widget.SeekBar(this); bright.setMax(100);
    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); blp.setMargins(0, dp(14), 0, dp(8)); bright.setLayoutParams(blp);
    box.addView(bright);
    LinearLayout foot = new LinearLayout(this); foot.setOrientation(LinearLayout.HORIZONTAL); foot.setGravity(Gravity.CENTER_VERTICAL);
    final View swatch = new View(this); swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
    final TextView hexTv = new TextView(this); hexTv.setTextColor(TEXT); hexTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); hexTv.setPadding(dp(12), 0, 0, 0); hexTv.setTypeface(Typeface.MONOSPACE);
    foot.addView(swatch); foot.addView(hexTv); box.addView(foot);
    wheel.cb = c -> { swatch.setBackgroundColor(c); hexTv.setText(String.format("#%06X", c & 0xFFFFFF)); };
    wheel.setColor(initial);
    float[] hh = new float[3]; Color.colorToHSV(initial, hh); bright.setProgress(Math.round(hh[2] * 100));
    swatch.setBackgroundColor(initial); hexTv.setText(String.format("#%06X", initial & 0xFFFFFF));
    bright.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(android.widget.SeekBar s, int p, boolean u) { wheel.setValue(p / 100f); }
      public void onStartTrackingTouch(android.widget.SeekBar s) {} public void onStopTrackingTouch(android.widget.SeekBar s) {}
    });
    new AlertDialog.Builder(this).setTitle("Colour").setView(box)
      .setPositiveButton("Use", (d, x) -> onFinal.onColor(wheel.color()))
      .setNegativeButton("Cancel", null).show();
  }

  static class Icon extends View {
    static final int BACK = 0, FWD = 1, GLOBE = 2, TABS = 3, MENU = 4, X = 5, RELOAD = 6, LOCK = 7, UNLOCK = 8, PERSON = 9, PERSON_ON = 10;
    int type; int count = 0; int col = 0xFFD8D8D8;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    Icon(Context c, int t) { super(c); type = t;
      p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(col);
      tp.setColor(col); tp.setTextAlign(Paint.Align.CENTER); tp.setTypeface(Typeface.DEFAULT_BOLD); setClickable(true); }
    void setType(int t) { type = t; invalidate(); }
    void setCount(int n) { count = n; invalidate(); }
    void tint(int c) { col = c; p.setColor(c); tp.setColor(c); invalidate(); }
    @Override public boolean onTouchEvent(MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN) setAlpha(getAlpha() * .5f);
      if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) setAlpha(Math.min(1f, getAlpha() / .5f));
      return super.onTouchEvent(e);
    }
    float u() { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()); }
    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, u = u();
      p.setStrokeWidth(1.85f * u);
      switch (type) {
        case BACK: c.drawLine(cx + 4 * u, cy - 7 * u, cx - 4 * u, cy, p); c.drawLine(cx - 4 * u, cy, cx + 4 * u, cy + 7 * u, p); break;
        case FWD:  c.drawLine(cx - 4 * u, cy - 7 * u, cx + 4 * u, cy, p); c.drawLine(cx + 4 * u, cy, cx - 4 * u, cy + 7 * u, p); break;
        case GLOBE: { float r = 8.2f * u; p.setStrokeWidth(1.6f * u); c.drawCircle(cx, cy, r, p);
          c.drawOval(new RectF(cx - r * .42f, cy - r, cx + r * .42f, cy + r), p); c.drawLine(cx - r, cy, cx + r, cy, p);
          c.drawLine(cx - r * .87f, cy - r * .46f, cx + r * .87f, cy - r * .46f, p); c.drawLine(cx - r * .87f, cy + r * .46f, cx + r * .87f, cy + r * .46f, p); break; }
        case TABS: { float s = 8 * u; c.drawRect(cx - s, cy - s, cx + s, cy + s, p);
          if (count > 0) { tp.setTextSize(9.5f * u); c.drawText(count > 99 ? "99" : String.valueOf(count), cx, cy + 3.4f * u, tp); } break; }
        case MENU: p.setStrokeWidth(2.2f * u); c.drawLine(cx - 8 * u, cy - 5 * u, cx + 8 * u, cy - 5 * u, p); c.drawLine(cx - 8 * u, cy, cx + 8 * u, cy, p); c.drawLine(cx - 8 * u, cy + 5 * u, cx + 8 * u, cy + 5 * u, p); break;
        case X: c.drawLine(cx - 5 * u, cy - 5 * u, cx + 5 * u, cy + 5 * u, p); c.drawLine(cx + 5 * u, cy - 5 * u, cx - 5 * u, cy + 5 * u, p); break;
        case RELOAD: { float r = 7 * u; RectF o = new RectF(cx - r, cy - r, cx + r, cy + r); c.drawArc(o, -50, 300, false, p);
          c.drawLine(cx + r * .55f, cy - r * 1.15f, cx + r * .72f, cy - r * .55f, p); c.drawLine(cx + r * .72f, cy - r * .55f, cx + r * .1f, cy - r * .5f, p); break; }
        case LOCK: { float bw = 6.5f * u, bh = 5.5f * u; c.drawRect(cx - bw, cy - 1 * u, cx + bw, cy + bh + 1 * u, p);
          c.drawArc(new RectF(cx - 4 * u, cy - 8 * u, cx + 4 * u, cy + 1 * u), 180, 180, false, p); break; }
        case UNLOCK: { float bw = 6.5f * u, bh = 5.5f * u; c.drawRect(cx - bw, cy - 1 * u, cx + bw, cy + bh + 1 * u, p);
          c.drawArc(new RectF(cx - 4 * u, cy - 8 * u, cx + 4 * u, cy + 1 * u), 180, 120, false, p); break; }
        case PERSON: case PERSON_ON: { float r = 3.6f * u; c.drawCircle(cx, cy - 3 * u, r, p);
          c.drawArc(new RectF(cx - 7 * u, cy + 1 * u, cx + 7 * u, cy + 13 * u), 180, 180, false, p);
          if (type == PERSON_ON) { p.setStyle(Paint.Style.FILL); c.drawCircle(cx + 6 * u, cy - 6 * u, 2.4f * u, p); p.setStyle(Paint.Style.STROKE); } break; }
      }
    }
  }
}
