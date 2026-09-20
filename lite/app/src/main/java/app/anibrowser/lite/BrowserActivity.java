/* SPDX-License-Identifier: MPL-2.0 */
package app.anibrowser.lite;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import org.mozilla.geckoview.*;
import java.util.*;
import java.util.function.Consumer;

public final class BrowserActivity extends Activity {
    static final String BUILTIN = "playback@anibrowser.lite";
    final Handler handler = new Handler(Looper.getMainLooper());
    GeckoRuntime runtime;
    GeckoSession session, parent;
    GeckoView browser;
    SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout addressRow;
    private LinearLayout controls;
    private EditText address;
    private Button speedButton, revealButton;
    private ProgressBar progress;
    private WebExtension.Port port;
    private final Map<GeckoSession, String> urls = new HashMap<>();
    private final Map<GeckoSession, Boolean> back = new HashMap<>();
    private final Map<GeckoSession, MediaSession> mediaSessions = new HashMap<>();
    private final Set<AlertDialog> dialogs = new HashSet<>();
    private boolean shortcut, fullVideo, started, foreground;
    private String title = "Website";
    private float downX, downY;
    private boolean pageTouch;
    private Addons addons;
    private ScrollView homeView;
    private FrameLayout pageArea;
    private boolean homeVisible = true;
    private Button tabsButton;
    private final List<GeckoSession> tabs = new ArrayList<>();
    private final Map<GeckoSession,String> titles = new HashMap<>();
    private final Runnable hide = () -> { if (immersive() && dialogs.isEmpty()) showControls(false); };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("sites", MODE_PRIVATE);
        runtime = ((LiteApplication)getApplication()).runtime();
        shortcut = getIntent().getBooleanExtra("fullscreen", saved != null && saved.getBoolean("fullscreen"));
        buildUi();
        session = createSession();
        tabs.add(session);
        session.open(runtime);
        browser.setSession(session);
        session.setActive(true);
        runtime.getWebExtensionController().setTabActive(session, true);
        addons = new Addons(this);
        installPlayback(saved);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(flags -> {
            if (immersive() && (flags & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                handler.postDelayed(this::systemBars, 1800);
        });
        systemBars();
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
    }
    private void installPlayback(Bundle saved) {
        runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/extensions/lite/", BUILTIN).accept(extension -> {
            WebExtension.MessageDelegate delegate = new WebExtension.MessageDelegate() {
                @Override public GeckoResult<Object> onMessage(String app, Object value, WebExtension.MessageSender sender) {
                    if (!(value instanceof JSONObject)) return GeckoResult.fromValue(null);
                    JSONObject message = (JSONObject)value;
                    String type = message.optString("type");
                    String url = message.optString("url", currentUrl());
                    if ("settings".equals(type)) return GeckoResult.fromValue(settings(url));
                    if ("saveSpeed".equals(type)) {
                        double rate = message.optDouble("speed", 1);
                        if (Double.isFinite(rate) && rate >= .25 && rate <= 4 && SitePolicy.web(url)) {
                            prefs.edit().putFloat("speed:" + SitePolicy.key(url), (float)rate).apply();
                            refreshSpeed();
                        }
                        return GeckoResult.fromValue(settings(url));
                    }
                    if ("redirect".equals(type)) {
                        String from = message.optString("source"), to = message.optString("destination");
                        GeckoResult<Object> result = new GeckoResult<>();
                        if (!SitePolicy.web(to)) result.complete(json("allowed", false));
                        else if (!SitePolicy.needsPrompt(from, to, false, false)) result.complete(json("allowed", true));
                        else ask("Leave this site?", from + "\n\n→ " + to, allowed -> result.complete(json("allowed", allowed)));
                        return result;
                    }
                    return GeckoResult.fromValue(null);
                }
                @Override public void onConnect(WebExtension.Port connected) {
                    port = connected;
                    ((LiteApplication)getApplication()).playbackPort = connected;
                    WebExtension.MessageDelegate messages = this;
                    connected.setDelegate(new WebExtension.PortDelegate() {
                        @Override public void onPortMessage(Object value, WebExtension.Port source) {
                            if (!(value instanceof JSONObject)) return;
                            JSONObject request = (JSONObject)value;
                            String id = request.optString("requestId");
                            messages.onMessage("anilite", request, null).accept(response -> {
                                try { source.postMessage(json("replyTo", id, "result", response)); }
                                catch (RuntimeException ignored) { }
                            }, error -> {
                                try { source.postMessage(json("replyTo", id, "error", "Request failed")); }
                                catch (RuntimeException ignored) { }
                            });
                        }
                        @Override public void onDisconnect(WebExtension.Port disconnected) {
                            if (port == disconnected) port = null;
                            LiteApplication app=(LiteApplication)getApplication();
                            if(app.playbackPort==disconnected) app.playbackPort=null;
                        }
                    });
                }
            };
            extension.setMessageDelegate(delegate,"anilite");
            WebExtension.Port existing=((LiteApplication)getApplication()).playbackPort;
            if(existing!=null) delegate.onConnect(existing);
            started = true;
            addons.bindInstalled();
            if (saved != null && saved.getBoolean("home")) showHome();
            else if (saved != null && saved.getString("url") != null && getIntent().getData() == null)
                open(saved.getString("url"));
            else acceptIntent(getIntent());
        }, error -> {
            progress.setVisibility(View.GONE);
            new AlertDialog.Builder(this).setTitle("Playback controls could not start")
                .setMessage("Please restart AniBrowser Lite. " + error.getMessage())
                .setPositiveButton("Close", (d,w) -> finish()).show();
        });
    }
    GeckoSession createSession() {
        GeckoSession result = new GeckoSession(new GeckoSessionSettings.Builder()
            .suspendMediaWhenInactive(true).useTrackingProtection(false).build());
        urls.put(result, "about:blank");
        result.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest request) {
                String destination = request.uri;
                if ("about:blank".equals(destination)) return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                if (!SitePolicy.web(destination)) {
                    if (destination.startsWith("moz-extension://")) return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                    handleExternal(destination);
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                // HTTP redirect hops are checked by the bundled extension, exactly once.
                if (request.isRedirect) { applyMode(s, destination); return GeckoResult.fromValue(AllowOrDeny.ALLOW); }
                String source = urls.getOrDefault(s, "about:blank");
                if (!SitePolicy.web(source) && parent != null) source = urls.getOrDefault(parent, source);
                boolean popup = request.target == TARGET_WINDOW_NEW;
                if (!SitePolicy.needsPrompt(source, destination, request.isDirectNavigation, popup)) {
                    applyMode(s, destination);
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }
                GeckoResult<AllowOrDeny> pending = new GeckoResult<>();
                ask(popup ? "Open this pop-up?" : "Leave this site?", source + "\n\n→ " + destination, allowed -> {
                    if (allowed) applyMode(s, destination);
                    pending.complete(allowed ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
                });
                return pending;
            }
            @Override public GeckoResult<AllowOrDeny> onSubframeLoadRequest(GeckoSession s, LoadRequest r) {
                // Embedded players are content, not cross-site top-level navigation.
                return GeckoResult.fromValue(SitePolicy.web(r.uri) || r.uri.startsWith("about:") || r.uri.startsWith("blob:")
                    || r.uri.startsWith("data:") ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
            }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                if (parent != null || s != session) { toast("Close the current pop-up first"); return GeckoResult.fromValue(null); }
                parent = session;
                parent.setActive(false);
                pause(parent);
                runtime.getWebExtensionController().setTabActive(parent, false);
                GeckoSession popup = createSession();
                applyMode(popup, uri);
                session = popup;
                browser.releaseSession();
                // Gecko opens this session itself; do not replay the request.
                handler.post(() -> { if (!isDestroyed() && session == popup) { browser.setSession(popup); popup.setActive(foreground); runtime.getWebExtensionController().setTabActive(popup,true); addons.bindInstalled(); } });
                return GeckoResult.fromValue(popup);
            }
            @Override public void onLocationChange(GeckoSession s, String url, List<GeckoSession.PermissionDelegate.ContentPermission> permissions, Boolean gesture) {
                if (url == null) return;
                urls.put(s, url);
                if (s == session) { if (SitePolicy.web(url)) setHomeVisible(false); if (!address.hasFocus()) address.setText("about:blank".equals(url) ? "" : url); refreshSpeed(); }
            }
            @Override public void onCanGoBack(GeckoSession s, boolean value) { back.put(s, value); }
            @Override public GeckoResult<String> onLoadError(GeckoSession s, String url, WebRequestError error) {
                if (s == session) { progress.setVisibility(View.GONE); toast("Could not load this page. Check the address or connection."); showControls(false); }
                return null;
            }
        });
        result.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession s, String value) { titles.put(s,value == null ? "Website" : value); if (s == session) title = value == null ? "Website" : value; }
            @Override public void onFullScreen(GeckoSession s, boolean full) {
                if (s != session) return;
                fullVideo = full; systemBars(); showControls(false);
            }
            @Override public void onCloseRequest(GeckoSession s) { if (s == session && parent != null) closePopup(); }
            @Override public void onCrash(GeckoSession s) { recover(s); }
            @Override public void onKill(GeckoSession s) { recover(s); }
        });
        result.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession s, String uri) { if (s == session) { progress.setProgress(5); progress.setVisibility(View.VISIBLE); } }
            @Override public void onProgressChange(GeckoSession s, int value) { if (s == session) progress.setProgress(value); }
            @Override public void onPageStop(GeckoSession s, boolean success) {
                if (s == session) progress.setVisibility(View.GONE);
                if (success && SitePolicy.web(urls.get(s))) remember("history", urls.get(s), titles.getOrDefault(s,SitePolicy.host(urls.get(s))),100);
            }
        });
        result.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s, ContentPermission permission) {
                if (permission.permission == PERMISSION_AUTOPLAY_INAUDIBLE || permission.permission == PERMISSION_MEDIA_KEY_SYSTEM_ACCESS)
                    return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
                if (permission.permission == PERMISSION_AUTOPLAY_AUDIBLE) return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
                if (permission.permission == PERMISSION_STORAGE_ACCESS) {
                    GeckoResult<Integer> pending = new GeckoResult<>();
                    ask("Allow sign-in storage?", permission.uri + "\n" + permission.thirdPartyOrigin,
                        allowed -> pending.complete(allowed ? ContentPermission.VALUE_ALLOW : ContentPermission.VALUE_DENY));
                    return pending;
                }
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
            }
        });
        result.setPromptDelegate(new PagePrompts(this));
        result.setMediaSessionDelegate(new MediaSession.Delegate() {
            @Override public void onActivated(GeckoSession s, MediaSession media) { mediaSessions.put(s,media); }
            @Override public void onDeactivated(GeckoSession s, MediaSession media) { mediaSessions.remove(s); }
            @Override public void onPlay(GeckoSession s, MediaSession media) {
                if (!foreground || s != session) media.pause();
                else getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            @Override public void onPause(GeckoSession s, MediaSession media) { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
            @Override public void onStop(GeckoSession s, MediaSession media) { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
        });
        return result;
    }
    private void recover(GeckoSession crashed) {
        if (crashed != session || isDestroyed()) return;
        String url = currentUrl();
        int tabIndex=tabs.indexOf(crashed);
        browser.releaseSession(); crashed.close(); urls.remove(crashed); back.remove(crashed); mediaSessions.remove(crashed); titles.remove(crashed);
        session = createSession(); session.open(runtime); browser.setSession(session); session.setActive(foreground);
        if(tabIndex>=0) tabs.set(tabIndex,session);
        runtime.getWebExtensionController().setTabActive(session,true); addons.bindInstalled();
        toast("Page stopped. Tap Reload to try again.");
        urls.put(session, url); showControls(true);
    }
    private void buildUi() {
        root = new FrameLayout(this);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setBackgroundColor(Color.WHITE);
        root.addView(column, new FrameLayout.LayoutParams(-1,-1));
        addressRow = new LinearLayout(this); addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(4),0,dp(4),0);
        addButton(addressRow,"‹",this::goBack).setContentDescription("Back");
        address = new EditText(this); address.setSingleLine(true); address.setTextSize(16); address.setHint("Search or enter address");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(52), 1));
        address.setSelectAllOnFocus(true);
        addButton(addressRow, "↻", () -> { if (!homeVisible) session.reload(); }).setContentDescription("Reload");
        tabsButton = addButton(addressRow, "1", this::showTabs); tabsButton.setContentDescription("Tabs");
        addButton(addressRow, "⋮", this::menu).setContentDescription("Browser menu");
        address.setOnEditorActionListener((v, id, event) -> { navigateInput(address.getText().toString()); return true; });
        column.addView(addressRow, new LinearLayout.LayoutParams(-1,-2));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setVisibility(View.GONE);
        column.addView(progress, new LinearLayout.LayoutParams(-1,dp(2)));
        pageArea = new FrameLayout(this);
        browser = new GeckoView(this); pageArea.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        homeView = new ScrollView(this); homeView.setFillViewport(true); pageArea.addView(homeView,new FrameLayout.LayoutParams(-1,-1));
        controls = new LinearLayout(this);
        LinearLayout row = controls; row.setGravity(Gravity.CENTER_VERTICAL);
        addButton(row, "−10s", () -> command("back"));
        addButton(row, "▶/Ⅱ", () -> command("toggle")).setContentDescription("Play or pause");
        addButton(row, "+10s", () -> command("forward"));
        speedButton = addButton(row, "1×", this::chooseSpeed);
        addButton(row, "Site", this::siteSettings);
        addButton(row, "×", () -> showControls(false)).setContentDescription("Hide playback controls");
        for (int i=0; i<row.getChildCount(); i++) row.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0,dp(48),1));
        column.addView(controls, new LinearLayout.LayoutParams(-1,dp(52)));
        column.addView(pageArea,new LinearLayout.LayoutParams(-1,0,1));
        revealButton = new Button(this); revealButton.setText("1× ⋮"); revealButton.setAllCaps(false); revealButton.setContentDescription("Show browser and playback controls"); revealButton.setTextSize(14);
        FrameLayout.LayoutParams reveal = new FrameLayout.LayoutParams(dp(76),dp(48),Gravity.TOP | Gravity.END);
        reveal.topMargin = dp(6); reveal.rightMargin = dp(6); root.addView(revealButton, reveal);
        revealButton.setOnClickListener(v -> menu());
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                root.setPadding(bars.left, immersive() ? 0 : bars.top, bars.right, Math.max(immersive() ? 0 : bars.bottom, ime.bottom));
            } else root.setPadding(insets.getSystemWindowInsetLeft(), immersive() ? 0 : insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), immersive() ? 0 : insets.getSystemWindowInsetBottom());
            return insets;
        });
        renderHome(); setHomeVisible(!shortcut); showControls(false);
    }
    Button addButton(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setMinWidth(dp(48)); button.setMinimumWidth(dp(48)); button.setPadding(dp(8),0,dp(8),0);
        button.setOnClickListener(v -> { handler.removeCallbacks(hide); action.run(); });
        row.addView(button, new LinearLayout.LayoutParams(-2,dp(48))); return button;
    }
    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    boolean immersive() { return shortcut || fullVideo; }
    private void systemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                if (immersive()) controller.hide(WindowInsets.Type.systemBars()); else controller.show(WindowInsets.Type.systemBars());
            }
        } else getWindow().getDecorView().setSystemUiVisibility(immersive()
            ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            : View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (immersive()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        addressRow.setVisibility(immersive() ? View.GONE : View.VISIBLE);
        root.requestApplyInsets();
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused) systemBars(); }
    private void showControls(boolean show) {
        if (homeVisible) show = false;
        controls.setVisibility(show ? View.VISIBLE : View.GONE);
        revealButton.setVisibility(immersive() && !show ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(hide);
        if (show && immersive()) handler.postDelayed(hide, 5000);
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY();
            int[] position=new int[2]; browser.getLocationOnScreen(position);
            pageTouch = !homeVisible && event.getRawY() >= position[1] && event.getRawY() < position[1]+browser.getHeight();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1 && pageTouch
            && Math.abs(event.getY() - downY) > dp(28) && Math.abs(event.getY() - downY) > Math.abs(event.getX() - downX)) {
            if (immersive()) showControls(true); pageTouch = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && pageTouch && fullVideo
            && Math.abs(event.getY() - downY) < dp(12) && Math.abs(event.getX() - downX) < dp(12)) {
            // Pass the tap to the site first, so its own controls are revealed too.
            handler.post(() -> showControls(true));
        }
        return super.dispatchTouchEvent(event);
    }
    String currentUrl() { return urls.getOrDefault(session, "about:blank"); }
    JSONObject settings(String url) { return json("type", "settings", "speed", (double)prefs.getFloat("speed:" + SitePolicy.key(url), 1)); }
    static JSONObject json(Object... pairs) {
        JSONObject object = new JSONObject();
        try { for (int i=0; i<pairs.length; i+=2) object.put((String)pairs[i], pairs[i+1]); } catch (JSONException e) { throw new IllegalArgumentException(e); }
        return object;
    }
    private void refreshSpeed() {
        String label = String.format(Locale.getDefault(), "%s×", prefs.getFloat("speed:" + SitePolicy.key(currentUrl()), 1));
        speedButton.setText(label); revealButton.setText(String.format("%s ⋮",label));
    }
    private void publish(JSONObject message) { if (port != null) try { port.postMessage(message); } catch (RuntimeException ignored) {} }
    private void command(String value) { publish(json("type", "command", "command", value)); }
    void open(String url) {
        if (!started || !SitePolicy.web(url)) return;
        setHomeVisible(false); showControls(false);
        applyMode(session, url);
        address.clearFocus(); browser.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        session.loadUri(url);
    }
    private void acceptIntent(Intent intent) {
        String url = intent.getDataString();
        if (SitePolicy.web(url)) open(url);
        else if ("about:blank".equals(currentUrl())) { showHome(); }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        shortcut = intent.getBooleanExtra("fullscreen", false); fullVideo = false;
        if (parent != null) closePopup();
        systemBars(); showControls(false);
        if (started) acceptIntent(intent);
    }
    void applyMode(GeckoSession target, String url) {
        int mode = prefs.getInt("mode:" + SitePolicy.key(url), 0);
        GeckoSessionSettings settings = target.getSettings();
        settings.setUserAgentMode(mode == 0 ? GeckoSessionSettings.USER_AGENT_MODE_MOBILE : GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
        settings.setViewportMode(mode == 2 ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        // Force phone identity even on Android tablets; hybrid uses desktop identity only.
        settings.setUserAgentOverride(mode == 0 ? GeckoSession.getDefaultUserAgent().replace("Android " + Build.VERSION.RELEASE + "; Tablet", "Android " + Build.VERSION.RELEASE + "; Mobile") : null);
    }
    private void chooseSpeed() {
        if (!SitePolicy.web(currentUrl())) { toast("Open a website first"); return; }
        double[] speeds = {.5,.75,1,1.25,1.5,1.75,2,2.5,3,4};
        String[] labels = Arrays.stream(speeds).mapToObj(rate -> rate + "×").toArray(String[]::new);
        int selected = 2; double value = prefs.getFloat("speed:" + SitePolicy.key(currentUrl()),1);
        for (int i=0;i<speeds.length;i++) if (Math.abs(speeds[i]-value)<.001) selected=i;
        String key = SitePolicy.key(currentUrl());
        showDialog(new AlertDialog.Builder(this).setTitle("Speed · " + key).setSingleChoiceItems(labels, selected, (dialog, index) -> {
            prefs.edit().putFloat("speed:" + key,(float)speeds[index]).apply(); refreshSpeed(); publish(settings(currentUrl())); dialog.dismiss();
        }).setNegativeButton("Cancel", null).create());
    }
    private void siteSettings() {
        if (homeVisible) { toast("Open a website first"); return; }
        if (!SitePolicy.web(currentUrl())) { toast("Open a website first"); return; }
        String url = currentUrl();
        showDialog(new AlertDialog.Builder(this).setTitle(SitePolicy.key(url)).setItems(new String[]{"Playback speed (remembered)", "Viewing mode (remembered)", "Redirects: ask every time; search engines exempt"}, (d, item) -> {
            if (item == 0) chooseSpeed();
            else if (item == 1) showDialog(new AlertDialog.Builder(this).setTitle("Viewing mode").setSingleChoiceItems(new String[]{"Mobile", "Desktop identity, mobile layout", "Desktop"}, prefs.getInt("mode:"+SitePolicy.key(url),0), (dialog, mode) -> {
                prefs.edit().putInt("mode:"+SitePolicy.key(url),mode).apply(); applyMode(session,url); session.reload(); dialog.dismiss();
            }).setNegativeButton("Cancel", null).create());
        }).setPositiveButton("Done", null).create());
    }
    private void menu() {
        String[] items = {"Home", "New tab", "Forward", "Bookmarks", "Bookmark this page", "History",
            "Search engine: " + engineName(), "Playback controls", "Site settings", "Add to home screen", "Extensions", "Share page",
            immersive() ? "Exit fullscreen" : "Fullscreen"};
        showDialog(new AlertDialog.Builder(this).setTitle("AniBrowser Lite").setItems(items,(dialog,index) -> {
            switch(index) {
                case 0: showHome(); break;
                case 1: newTab(); break;
                case 2: if (SitePolicy.web(currentUrl())) { setHomeVisible(false); session.goForward(); } break;
                case 3: library("bookmarks"); break;
                case 4: if (!homeVisible && SitePolicy.web(currentUrl())) { remember("bookmarks",currentUrl(),title,50); toast("Bookmark saved"); } else toast("Open a website first"); break;
                case 5: library("history"); break;
                case 6: chooseEngine(); break;
                case 7: if (homeVisible) toast("Open a website first"); else showControls(true); break;
                case 8: siteSettings(); break;
                case 9: pinShortcut(); break;
                case 10: addons.show(); break;
                case 11: if (!homeVisible && SitePolicy.web(currentUrl())) startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,currentUrl()),"Share page")); break;
                case 12: if (homeVisible) { toast("Open a website first"); break; } if (fullVideo) session.exitFullScreen(); shortcut = !immersive(); fullVideo = false; systemBars(); showControls(false); break;
            }
        }).setNegativeButton("Close",null).create());
    }
    private String engineName() { return new String[]{"Google","DuckDuckGo","Bing"}[Math.max(0,Math.min(2,prefs.getInt("searchEngine",0)))]; }
    private void navigateInput(String text) { if (!text.trim().isEmpty()) open(SitePolicy.input(text,prefs.getInt("searchEngine",0))); }
    private void chooseEngine() {
        showDialog(new AlertDialog.Builder(this).setTitle("Default search engine").setSingleChoiceItems(new String[]{"Google","DuckDuckGo","Bing"},prefs.getInt("searchEngine",0),(d,index) -> {
            prefs.edit().putInt("searchEngine",index).apply(); renderHome(); d.dismiss();
        }).setNegativeButton("Cancel",null).create());
    }
    private void setHomeVisible(boolean visible) {
        homeVisible = visible; homeView.setVisibility(visible ? View.VISIBLE : View.GONE);
        browser.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        if (visible) { address.setText(""); controls.setVisibility(View.GONE); revealButton.setVisibility(View.GONE); }
    }
    private void showHome() {
        if (session != null) { pause(session); command("pause"); session.stop(); }
        if (fullVideo) session.exitFullScreen();
        shortcut = false; fullVideo = false; systemBars(); renderHome(); setHomeVisible(true); showControls(false);
        address.clearFocus(); homeView.requestFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
    }
    private TextView homeText(String text, int size) {
        TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.rgb(35,42,52)); return view;
    }
    private void renderHome() {
        homeView.removeAllViews(); homeView.setBackgroundColor(Color.rgb(247,248,250));
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setGravity(Gravity.CENTER_HORIZONTAL); outer.setPadding(dp(24),dp(48),dp(24),dp(24));
        homeView.addView(outer,new ScrollView.LayoutParams(-1,-1));
        TextView heading=homeText("AniBrowser Lite",28); heading.setTypeface(null,android.graphics.Typeface.BOLD); heading.setGravity(Gravity.CENTER); outer.addView(heading);
        TextView subtitle=homeText("Search the web",16); subtitle.setGravity(Gravity.CENTER); subtitle.setPadding(0,dp(8),0,dp(28)); outer.addView(subtitle);
        LinearLayout search=new LinearLayout(this); search.setGravity(Gravity.CENTER_VERTICAL);
        EditText input=new EditText(this); input.setSingleLine(true); input.setTextSize(16); input.setHint("Search or enter address"); input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        search.addView(input,new LinearLayout.LayoutParams(0,dp(56),1)); addButton(search,"Go",()->navigateInput(input.getText().toString()));
        input.setOnEditorActionListener((v,action,event)->{navigateInput(input.getText().toString());return true;});
        outer.addView(search,new LinearLayout.LayoutParams(-1,-2));
        Button engine=new Button(this); engine.setText("Search with " + engineName() + " ▾"); engine.setAllCaps(false); engine.setOnClickListener(v->chooseEngine()); outer.addView(engine);
        LinearLayout links=new LinearLayout(this); links.setGravity(Gravity.CENTER);
        addButton(links,"Bookmarks",()->library("bookmarks")); addButton(links,"History",()->library("history")); outer.addView(links);
        TextView label=homeText("Your bookmarks",18); label.setTypeface(null,android.graphics.Typeface.BOLD); label.setPadding(0,dp(32),0,dp(12)); outer.addView(label,new LinearLayout.LayoutParams(-1,-2));
        JSONArray entries=entries("bookmarks");
        if(entries.length()==0) { TextView empty=homeText("Save a page from the browser menu to find it here.",15); outer.addView(empty,new LinearLayout.LayoutParams(-1,-2)); }
        for(int i=0;i<Math.min(entries.length(),8);i++) {
            JSONObject entry=entries.optJSONObject(i); if(entry==null) continue;
            Button link=new Button(this); link.setAllCaps(false); link.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); link.setMaxLines(2);
            link.setText(entry.optString("title") + "\n" + SitePolicy.host(entry.optString("url")));
            link.setOnClickListener(v->open(entry.optString("url"))); outer.addView(link,new LinearLayout.LayoutParams(-1,dp(64)));
        }
        outer.setFocusableInTouchMode(true); outer.requestFocus();
    }
    private JSONArray entries(String kind) { try { return new JSONArray(prefs.getString(kind,"[]")); } catch(JSONException e) { return new JSONArray(); } }
    private void remember(String kind,String url,String name,int limit) {
        JSONArray previous=entries(kind),next=new JSONArray(); next.put(json("url",url,"title",name == null || name.isEmpty() ? SitePolicy.host(url) : name));
        for(int i=0;i<previous.length() && next.length()<limit;i++) { JSONObject entry=previous.optJSONObject(i); if(entry!=null && !url.equals(entry.optString("url"))) next.put(entry); }
        prefs.edit().putString(kind,next.toString()).apply();
    }
    private void library(String kind) {
        JSONArray entries=entries(kind); String heading="bookmarks".equals(kind)?"Bookmarks":"History";
        String[] labels=new String[entries.length()]; for(int i=0;i<labels.length;i++) { JSONObject e=entries.optJSONObject(i); labels[i]=e.optString("title")+"\n"+e.optString("url"); }
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(heading).setNegativeButton("Close",null);
        if(labels.length==0) dialog.setMessage("bookmarks".equals(kind)?"No bookmarks yet. Use Bookmark this page in the browser menu.":"Pages you visit will appear here.");
        else dialog.setItems(labels,(d,index)->open(entries.optJSONObject(index).optString("url"))).setNeutralButton("Clear",(d,w)->{
            showDialog(new AlertDialog.Builder(this).setTitle("Clear " + heading.toLowerCase(Locale.ROOT) + "?").setPositiveButton("Clear",(confirm,which)->{prefs.edit().remove(kind).apply();renderHome();}).setNegativeButton("Cancel",null).create());
        });
        showDialog(dialog.create());
    }
    private void newTab() {
        if (tabs.size() >= 4) { toast("Close a tab before opening another (4 tabs maximum in Lite)"); showTabs(); return; }
        if(parent!=null) closePopup();
        GeckoSession next=createSession(); next.open(runtime); tabs.add(next); switchTab(next); showHome();
    }
    private void switchTab(GeckoSession next) {
        if(parent!=null) closePopup();
        if(session!=next) { pause(session); session.setActive(false); runtime.getWebExtensionController().setTabActive(session,false); browser.releaseSession(); session=next; browser.setSession(next); }
        session.setActive(foreground); runtime.getWebExtensionController().setTabActive(session,true);
        title=titles.getOrDefault(session,"Website"); fullVideo=false; shortcut=false;
        address.setText(SitePolicy.web(currentUrl())?currentUrl():""); refreshSpeed(); systemBars(); showControls(false);
        if(!SitePolicy.web(currentUrl())) showHome(); else setHomeVisible(false);
        tabsButton.setText(String.valueOf(tabs.size())); addons.bindInstalled();
    }
    private void showTabs() {
        String[] labels=new String[tabs.size()]; for(int i=0;i<tabs.size();i++) { GeckoSession tab=tabs.get(i); labels[i]=(tab==session?"● ":"")+(SitePolicy.web(urls.get(tab))?titles.getOrDefault(tab,SitePolicy.host(urls.get(tab))):"New tab"); }
        showDialog(new AlertDialog.Builder(this).setTitle("Tabs ("+tabs.size()+"/4)").setItems(labels,(d,index)->switchTab(tabs.get(index)))
            .setPositiveButton("New tab",(d,w)->newTab()).setNeutralButton("Close current",(d,w)->closeTab()).setNegativeButton("Done",null).create());
    }
    private void closeTab() {
        if(parent!=null) { closePopup(); return; }
        GeckoSession old=session; tabs.remove(old); browser.releaseSession(); old.close(); urls.remove(old); titles.remove(old); back.remove(old); mediaSessions.remove(old);
        if(tabs.isEmpty()) { session=createSession();session.open(runtime);tabs.add(session); }
        else session=tabs.get(tabs.size()-1);
        browser.setSession(session); switchTab(session);
    }
    private void pinShortcut() {
        if (homeVisible) { toast("Open a website first"); return; }
        String url = currentUrl();
        if (!SitePolicy.web(url)) { toast("Open a website first"); return; }
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (!manager.isRequestPinShortcutSupported()) { toast("This launcher does not support pinned shortcuts"); return; }
        Intent intent = new Intent(this, BrowserActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)).putExtra("fullscreen",true);
        String name = title.trim().isEmpty() ? SitePolicy.host(url) : title;
        ShortcutInfo info = new ShortcutInfo.Builder(this, "site-" + UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .setShortLabel(name.substring(0, Math.min(name.length(),40))).setLongLabel(name)
            .setIcon(Icon.createWithResource(this,R.drawable.ic_launcher)).setIntent(intent).build();
        manager.requestPinShortcut(info,null);
    }
    void ask(String heading, String message, Consumer<Boolean> answer) {
        if (!foreground || isFinishing() || isDestroyed()) { answer.accept(false); return; }
        boolean[] answered = {false};
        Consumer<Boolean> once = value -> { if (!answered[0]) { answered[0]=true; answer.accept(value); } };
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(heading).setMessage(message)
            .setPositiveButton("Allow once", (d,w) -> once.accept(true)).setNegativeButton("Block", (d,w) -> once.accept(false)).create();
        showDialog(dialog, () -> once.accept(false));
    }
    void showDialog(AlertDialog dialog) { showDialog(dialog, () -> {}); }
    void showDialog(AlertDialog dialog, Runnable dismissed) {
        if (isFinishing() || isDestroyed()) { dismissed.run(); return; }
        dialogs.add(dialog); handler.removeCallbacks(hide);
        dialog.setOnDismissListener(d -> { dialogs.remove(dialog); dismissed.run(); if (!isDestroyed()) { systemBars(); if (immersive()) handler.postDelayed(hide,5000); } });
        dialog.show();
        if (immersive() && Build.VERSION.SDK_INT >= 30 && dialog.getWindow() != null) {
            WindowInsetsController controller = dialog.getWindow().getInsetsController();
            if (controller != null) { controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); controller.hide(WindowInsets.Type.systemBars()); }
        }
    }
    void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    private void handleExternal(String url) {
        String scheme = Uri.parse(url).getScheme();
        if (!Arrays.asList("mailto","tel","intent","market").contains(scheme == null ? "" : scheme)) { toast("Unsupported link"); return; }
        ask("Open another app?", url, allowed -> {
            if (!allowed) return;
            try {
                Intent target = "intent".equals(scheme) ? Intent.parseUri(url,Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW,Uri.parse(url));
                target.addCategory(Intent.CATEGORY_BROWSABLE); target.setComponent(null); target.setSelector(null);
                target.setFlags(0); startActivity(target);
            } catch (Exception e) { toast("No app can open this link"); }
        });
    }
    private void closePopup() {
        if (parent == null) return;
        browser.releaseSession(); urls.remove(session); back.remove(session); session.close();
        session = parent; parent = null; browser.setSession(session); session.setActive(foreground); runtime.getWebExtensionController().setTabActive(session,true);
        fullVideo = false; address.setText(currentUrl()); refreshSpeed(); systemBars();
    }
    private void goBack() {
        if (fullVideo) { session.exitFullScreen(); return; }
        if (homeVisible) { if(SitePolicy.web(currentUrl())) { setHomeVisible(false); address.setText(currentUrl()); } else finish(); return; }
        if (Boolean.TRUE.equals(back.get(session))) session.goBack();
        else if (parent != null) closePopup(); else showHome();
    }
    // API 33+ uses the platform OnBackInvokedDispatcher registered in onCreate.
    // This override is exclusively the pre-33 hardware/gesture fallback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onResume() { super.onResume(); foreground = true; if (session != null) session.setActive(true); }
    @Override protected void onPause() {
        foreground = false; handler.removeCallbacks(hide);
        if (session != null) { pause(session); command("pause"); session.setActive(false); }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        for (AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("url", currentUrl()); state.putBoolean("fullscreen",shortcut); state.putBoolean("home",homeVisible); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        browser.releaseSession(); for(GeckoSession tab:tabs) tab.close(); if(session!=null && !tabs.contains(session)) session.close();
        if (addons != null) addons.close();
        port = null; urls.clear(); back.clear(); mediaSessions.clear(); super.onDestroy();
    }
    private void pause(GeckoSession target) { MediaSession media = mediaSessions.get(target); if (media != null) media.pause(); }
}
