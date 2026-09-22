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
    private Button reloadButton;
    private LinearLayout errorView, findBar;
    private TextView findStatus;
    private EditText findInput;
    private final Map<GeckoSession, Boolean> forward = new HashMap<>(), loading = new HashMap<>();
    private final Map<GeckoSession, String> failures = new HashMap<>();
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
            @Override public void onCanGoForward(GeckoSession s, boolean value) { forward.put(s, value); }
            @Override public GeckoResult<String> onLoadError(GeckoSession s, String url, WebRequestError error) {
                failures.put(s,url); loading.put(s,false);
                if (s == session) { refreshLoading(); showFailure(); }
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
            @Override public void onPageStart(GeckoSession s, String uri) { failures.remove(s); loading.put(s,true); if (s == session) { errorView.setVisibility(View.GONE); progress.setProgress(5); refreshLoading(); } }
            @Override public void onProgressChange(GeckoSession s, int value) { if (s == session) progress.setProgress(value); }
            @Override public void onPageStop(GeckoSession s, boolean success) {
                loading.put(s,false); if (s == session) refreshLoading();
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
                if (!foreground || homeVisible || s != session) media.pause();
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
        forward.remove(crashed); loading.remove(crashed); failures.remove(crashed);
        session = createSession(); session.open(runtime); browser.setSession(session); session.setActive(foreground);
        if(tabIndex>=0) tabs.set(tabIndex,session);
        runtime.getWebExtensionController().setTabActive(session,true); addons.bindInstalled();
        urls.put(session, url); failures.put(session,url); loading.put(session,false); refreshLoading(); showFailure();
    }
    private void buildUi() {
        root = new FrameLayout(this); root.setBackgroundColor(Color.rgb(247,249,252));
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setBackgroundColor(Color.WHITE);
        root.addView(column, new FrameLayout.LayoutParams(-1,-1));
        addressRow = new LinearLayout(this); addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(4),dp(6),dp(4),dp(6));
        addressRow.setBackgroundColor(Color.rgb(247,249,252));
        addButton(addressRow,"‹",this::goBack).setContentDescription("Back");
        address = new EditText(this); address.setSingleLine(true); address.setTextSize(14); address.setHint("Search or URL"); address.setContentDescription("Search or enter address");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        address.setBackground(surface(Color.rgb(233,238,245),18)); address.setPadding(dp(14),0,dp(10),0);
        address.setTextColor(Color.rgb(27,39,57)); address.setHintTextColor(Color.rgb(99,113,133));
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(48), 1));
        address.setSelectAllOnFocus(true);
        reloadButton=addButton(addressRow, "↻", () -> {
            if (homeVisible) return;
            if (Boolean.TRUE.equals(loading.get(session))) { session.stop(); loading.put(session,false); refreshLoading(); }
            else if (failures.containsKey(session)) open(failures.get(session)); else session.reload();
        }); reloadButton.setContentDescription("Reload");
        tabsButton = addButton(addressRow, "1", this::showTabs); tabsButton.setContentDescription("Tabs");
        addButton(addressRow, "⋮", this::menu).setContentDescription("Browser menu");
        address.setOnEditorActionListener((v, id, event) -> { navigateInput(address.getText().toString()); return true; });
        column.addView(addressRow, new LinearLayout.LayoutParams(-1,-2));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setVisibility(View.GONE);
        column.addView(progress, new LinearLayout.LayoutParams(-1,dp(2)));
        pageArea = new FrameLayout(this);
        browser = new GeckoView(this); pageArea.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        homeView = new ScrollView(this); homeView.setFillViewport(true); pageArea.addView(homeView,new FrameLayout.LayoutParams(-1,-1));
        errorView=new LinearLayout(this); errorView.setOrientation(LinearLayout.VERTICAL); errorView.setGravity(Gravity.CENTER); errorView.setPadding(dp(24),dp(24),dp(24),dp(24)); errorView.setBackgroundColor(Color.rgb(247,249,252)); errorView.setVisibility(View.GONE);
        pageArea.addView(errorView,new FrameLayout.LayoutParams(-1,-1));
        findBar=new LinearLayout(this); findBar.setGravity(Gravity.CENTER_VERTICAL); findBar.setBackgroundColor(Color.rgb(239,244,250)); findBar.setVisibility(View.GONE);
        findInput=new EditText(this); findInput.setSingleLine(); findInput.setTextSize(14); findInput.setHint("Find in page"); findInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        findBar.addView(findInput,new LinearLayout.LayoutParams(0,dp(48),1));
        findStatus=homeText("",12); findBar.addView(findStatus);
        addButton(findBar,"↑",()->findText(true)).setContentDescription("Previous match"); addButton(findBar,"↓",()->findText(false)).setContentDescription("Next match"); addButton(findBar,"×",this::closeFind).setContentDescription("Close find in page");
        findInput.setOnEditorActionListener((v,id,event)->{findText(false);return true;});
        column.addView(findBar,new LinearLayout.LayoutParams(-1,-2));
        controls = new LinearLayout(this);
        controls.setBackgroundColor(Color.rgb(239,244,250));
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
        styleButton(revealButton, true);
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
        styleButton(button, false);
        button.setMinWidth(dp(48)); button.setMinimumWidth(dp(48)); button.setPadding(dp(8),0,dp(8),0);
        button.setOnClickListener(v -> { handler.removeCallbacks(hide); action.run(); });
        row.addView(button, new LinearLayout.LayoutParams(-2,dp(48))); return button;
    }
    private android.graphics.drawable.GradientDrawable surface(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private void styleButton(Button button, boolean primary) {
        button.setAllCaps(false); button.setStateListAnimator(null); button.setElevation(0);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(44,72,107));
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x223A70B8),
            surface(primary ? Color.rgb(43,91,157) : Color.TRANSPARENT,16),surface(Color.WHITE,16)));
    }
    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void refreshLoading() {
        boolean busy=!homeVisible && Boolean.TRUE.equals(loading.get(session));
        reloadButton.setText(busy?"×":"↻"); reloadButton.setContentDescription(busy?"Stop loading":"Reload");
        reloadButton.setEnabled(!homeVisible); reloadButton.setAlpha(homeVisible?.35f:1f);
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
    }
    private void showFailure() {
        if(homeVisible || !failures.containsKey(session)) { errorView.setVisibility(View.GONE); return; }
        closeFind(); showControls(false); errorView.removeAllViews();
        TextView heading=homeText("Page couldn’t be loaded",22); heading.setGravity(Gravity.CENTER); errorView.addView(heading);
        TextView detail=homeText("Check your connection or the address, then try again.\n\n"+failures.get(session),14); detail.setGravity(Gravity.CENTER); detail.setPadding(0,dp(18),0,dp(24)); errorView.addView(detail);
        addButton(errorView,"Try again",()->open(failures.get(session))); addButton(errorView,"Go home",this::showHome);
        errorView.setVisibility(View.VISIBLE);
    }
    private void findText(boolean previous) {
        String query=findInput.getText().toString(); if(query.isEmpty()) { session.getFinder().clear(); findStatus.setText(""); return; }
        GeckoSession target=session; target.getFinder().setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
        target.getFinder().find(query,previous?GeckoSession.FINDER_FIND_BACKWARDS:GeckoSession.FINDER_FIND_FORWARD).accept(result->{
            if(target==session && findBar.getVisibility()==View.VISIBLE && query.equals(findInput.getText().toString())) findStatus.setText(result.found?(result.total>0?result.current+" / "+result.total:"Found"):"No match");
        },error->toast("Could not search this page"));
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(findInput.getWindowToken(),0);
    }
    private void closeFind() { if(session!=null) session.getFinder().clear(); if(findBar!=null) { findBar.setVisibility(View.GONE); findInput.clearFocus(); } }
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
        closeFind(); errorView.setVisibility(View.GONE); failures.remove(session);
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
            immersive() ? "Exit fullscreen" : "Fullscreen", "Find in page", "Copy page link"};
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(16),dp(8),dp(16),dp(8));
        ScrollView scroll=new ScrollView(this); scroll.addView(panel);
        AlertDialog menuDialog=new AlertDialog.Builder(this).setTitle(homeVisible?"Your browser":SitePolicy.host(currentUrl())).setView(scroll).setNegativeButton("Close",null).create();
        LinearLayout shortcuts=new LinearLayout(this);
        for(int i=0;i<3;i++) {final int action=i;Button button=addButton(shortcuts,items[i],()->{menuDialog.dismiss();menuAction(action);});button.setBackground(surface(0xFFEAF0FF,14));if(i==2 && !Boolean.TRUE.equals(forward.get(session))) {button.setEnabled(false);button.setAlpha(.4f);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(dp(3),0,dp(3),0);button.setLayoutParams(lp);}
        panel.addView(shortcuts);
        menuSection(panel,"YOUR PAGES"); menuPair(panel,menuDialog,items,3,5);
        if(!homeVisible) {menuPair(panel,menuDialog,items,4,9);menuSection(panel,"PAGE TOOLS"); menuPair(panel,menuDialog,items,7,8); menuPair(panel,menuDialog,items,13,12); menuPair(panel,menuDialog,items,11,14);}
        menuSection(panel,"PREFERENCES"); menuPair(panel,menuDialog,items,6,10);
        showDialog(menuDialog); Window window=menuDialog.getWindow(); if(window!=null) {window.setGravity(Gravity.BOTTOM);window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(16),dp(560)),Math.min(dp(homeVisible?390:650),Math.round(getResources().getDisplayMetrics().heightPixels*.88f)));}
    }
    private void menuSection(LinearLayout panel,String label) {TextView heading=homeText(label,11);heading.setTextColor(0xFF76829A);heading.setLetterSpacing(.08f);heading.setPadding(dp(6),dp(20),0,dp(6));panel.addView(heading);}
    private void menuPair(LinearLayout panel,AlertDialog dialog,String[] labels,int first,int second) {
        LinearLayout row=new LinearLayout(this);
        for(int index:new int[]{first,second}) {Button button=addButton(row,labels[index],()->{dialog.dismiss();menuAction(index);});button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setMaxLines(2);button.setTextSize(13);button.setPadding(dp(12),0,dp(8),0);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22315DD8),surface(0xFFF4F6FB,12),surface(Color.WHITE,12)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));button.setLayoutParams(lp);}
        panel.addView(row);
    }
    private void menuAction(int index) {
            switch(index) {
                case 0: showHome(); break;
                case 1: newTab(); break;
                case 2: if (Boolean.TRUE.equals(forward.get(session))) { setHomeVisible(false); session.goForward(); } else toast("No next page"); break;
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
                case 13: if(homeVisible || failures.containsKey(session)) { toast("Open a website first"); break; } findBar.setVisibility(View.VISIBLE); findInput.requestFocus(); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(findInput,InputMethodManager.SHOW_IMPLICIT); break;
                case 14: if(!homeVisible && SitePolicy.web(currentUrl())) { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Page link",currentUrl())); toast("Link copied"); } else toast("Open a website first"); break;
            }
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
        if(session!=null) session.setActive(foreground && !visible);
        if(errorView!=null) errorView.setVisibility(View.GONE);
        refreshLoading();
        if (visible) { address.setText(""); controls.setVisibility(View.GONE); revealButton.setVisibility(View.GONE); }
    }
    private void showHome() {
        closeFind();
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
        homeView.removeAllViews(); homeView.setBackgroundColor(0xFFF4F6FB);
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL);
        int gutter=Math.max(18,(getResources().getConfiguration().screenWidthDp-560)/2);
        outer.setPadding(dp(gutter),dp(18),dp(gutter),dp(28));
        homeView.addView(outer,new ScrollView.LayoutParams(-1,-1));
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(22),dp(24),dp(22),dp(12));
        android.graphics.drawable.GradientDrawable gradient=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xFF1B2D58,0xFF315DD8});gradient.setCornerRadius(dp(26));hero.setBackground(gradient);
        TextView brand=homeText("ANIBROWSER  /  LITE",11);brand.setLetterSpacing(.14f);brand.setTextColor(0xFFC3D4FF);hero.addView(brand);
        TextView heading=homeText("Where to next?",28);heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));heading.setTextColor(Color.WHITE);heading.setPadding(0,dp(16),0,dp(6));hero.addView(heading);
        TextView subtitle=homeText("Your web, at your pace.",14);subtitle.setTextColor(0xFFD8E3FF);subtitle.setPadding(0,0,0,dp(24));hero.addView(subtitle);
        LinearLayout search=new LinearLayout(this);search.setGravity(Gravity.CENTER_VERTICAL);search.setBackground(surface(Color.WHITE,18));search.setPadding(dp(10),dp(4),dp(4),dp(4));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(14);input.setHint("Search or enter address");input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(4),0,dp(4),0);input.setTextColor(0xFF1E2D48);input.setHintTextColor(0xFF76829A);
        search.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));Button go=addButton(search,"Go",()->navigateInput(input.getText().toString()));styleButton(go,true);
        input.setOnEditorActionListener((v,action,event)->{navigateInput(input.getText().toString());return true;});hero.addView(search,new LinearLayout.LayoutParams(-1,-2));
        Button engine=new Button(this);styleButton(engine,false);engine.setText("Search with "+engineName()+" ▾");engine.setTextSize(12);engine.setTextColor(0xFFD8E3FF);engine.setOnClickListener(v->chooseEngine());hero.addView(engine,new LinearLayout.LayoutParams(-1,dp(48)));outer.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout shortcuts=new LinearLayout(this);LinearLayout.LayoutParams shortcutsLayout=new LinearLayout.LayoutParams(-1,-2);shortcutsLayout.topMargin=dp(16);outer.addView(shortcuts,shortcutsLayout);
        shortcutCard(shortcuts,"Bookmarks","Your saved places",android.R.drawable.btn_star_big_off,0xFF315DD8,()->library("bookmarks"));
        shortcutCard(shortcuts,"History","Recently visited",android.R.drawable.ic_menu_recent_history,0xFF137F78,()->library("history"));
        LinearLayout section=new LinearLayout(this);section.setGravity(Gravity.CENTER_VERTICAL);section.setPadding(0,dp(18),0,dp(8));
        TextView label=homeText("Favorites",19);label.setTypeface(null,android.graphics.Typeface.BOLD);section.addView(label,new LinearLayout.LayoutParams(0,-2,1));addButton(section,"View all",()->library("bookmarks"));outer.addView(section);
        JSONArray saved=entries("bookmarks");
        if(saved.length()==0) {LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.VERTICAL);empty.setPadding(dp(20),dp(22),dp(20),dp(22));empty.setBackground(surface(Color.WHITE,20));TextView title=homeText("Keep your favorites close",16);title.setTypeface(null,android.graphics.Typeface.BOLD);empty.addView(title);TextView help=homeText("Open a page, then choose Bookmark this page in the menu.",13);help.setTextColor(0xFF76829A);help.setPadding(0,dp(8),0,0);empty.addView(help);outer.addView(empty);}
        for(int i=0;i<Math.min(saved.length(),8);i++) {
            JSONObject entry=saved.optJSONObject(i);if(entry==null)continue;
            LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(12),dp(12),dp(12));card.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22315DD8),surface(Color.WHITE,18),surface(Color.WHITE,18)));
            String name=entry.optString("title"),host=SitePolicy.host(entry.optString("url"));TextView initial=homeText(host.isEmpty()?"★":host.substring(0,1).toUpperCase(Locale.ROOT),20);initial.setGravity(Gravity.CENTER);initial.setTextColor(0xFF315DD8);initial.setBackground(surface(0xFFEAF0FF,14));card.addView(initial,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(14),0,dp(6),0);TextView title=homeText(name,15);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title);TextView domain=homeText(host,12);domain.setTextColor(0xFF76829A);domain.setPadding(0,dp(4),0,0);text.addView(domain);card.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            Button more=new Button(this);styleButton(more,false);more.setText("⋮");more.setTextSize(20);more.setPadding(0,0,0,0);more.setContentDescription("Manage bookmark: "+name);more.setOnClickListener(v->manageBookmark(entry));card.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
            card.setContentDescription("Open bookmark: "+name);card.setFocusable(true);card.setOnClickListener(v->open(entry.optString("url")));card.setOnLongClickListener(v->{manageBookmark(entry);return true;});LinearLayout.LayoutParams tile=new LinearLayout.LayoutParams(-1,-2);tile.bottomMargin=dp(10);outer.addView(card,tile);
        }
        TextView footer=homeText("ANI / LITE",10);footer.setLetterSpacing(.18f);footer.setTextColor(0xFF8793A8);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(24),0,0);outer.addView(footer);
        outer.setFocusableInTouchMode(true);outer.requestFocus();
    }
    private void shortcutCard(LinearLayout row,String title,String subtitle,int icon,int tint,Runnable action) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(12),dp(16));card.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22315DD8),surface(Color.WHITE,20),surface(Color.WHITE,20)));
        ImageView image=new ImageView(this);image.setImageResource(icon);image.setColorFilter(tint);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(image,new LinearLayout.LayoutParams(dp(26),dp(26)));
        TextView label=homeText(title,15);label.setTypeface(null,android.graphics.Typeface.BOLD);label.setPadding(0,dp(12),0,dp(4));card.addView(label);TextView detail=homeText(subtitle,12);detail.setTextColor(0xFF76829A);card.addView(detail);
        card.setContentDescription(title);card.setFocusable(true);card.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(row.getChildCount()==0)lp.rightMargin=dp(6);else lp.leftMargin=dp(6);row.addView(card,lp);
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
        AlertDialog libraryDialog=dialog.create(); showDialog(libraryDialog);
        if(libraryDialog.getListView()!=null && "bookmarks".equals(kind)) libraryDialog.getListView().setOnItemLongClickListener((list,view,index,id)->{libraryDialog.dismiss();manageBookmark(entries.optJSONObject(index));return true;});
    }
    private void manageBookmark(JSONObject entry) {
        String url=entry.optString("url");
        showDialog(new AlertDialog.Builder(this).setTitle(entry.optString("title")).setItems(new String[]{"Open","Rename","Remove bookmark"},(d,index)->{
            if(index==0) open(url);
            else if(index==1) { EditText name=new EditText(this); name.setSingleLine(); name.setText(entry.optString("title")); name.selectAll();
                showDialog(new AlertDialog.Builder(this).setTitle("Rename bookmark").setView(name).setPositiveButton("Save",(dialog,which)->{String text=name.getText().toString().trim();if(!text.isEmpty()) {remember("bookmarks",url,text,50);renderHome();}}).setNegativeButton("Cancel",null).create());
            } else showDialog(new AlertDialog.Builder(this).setTitle("Remove bookmark?").setMessage(entry.optString("title")).setPositiveButton("Remove",(dialog,which)->{
                JSONArray next=new JSONArray(),previous=entries("bookmarks");for(int i=0;i<previous.length();i++) {JSONObject item=previous.optJSONObject(i);if(item!=null && !url.equals(item.optString("url"))) next.put(item);}prefs.edit().putString("bookmarks",next.toString()).apply();renderHome();
            }).setNegativeButton("Cancel",null).create());
        }).setNegativeButton("Close",null).create());
    }
    private void newTab() {
        if (tabs.size() >= 4) { toast("Close a tab before opening another (4 tabs maximum in Lite)"); showTabs(); return; }
        if(parent!=null) closePopup();
        GeckoSession next=createSession(); next.open(runtime); tabs.add(next); switchTab(next); showHome();
    }
    private void switchTab(GeckoSession next) {
        closeFind(); if(fullVideo) session.exitFullScreen();
        if(parent!=null) closePopup();
        if(session!=next) { pause(session); session.setActive(false); runtime.getWebExtensionController().setTabActive(session,false); browser.releaseSession(); session=next; browser.setSession(next); }
        session.setActive(foreground); runtime.getWebExtensionController().setTabActive(session,true);
        title=titles.getOrDefault(session,"Website"); fullVideo=false; shortcut=false;
        address.setText(SitePolicy.web(currentUrl())?currentUrl():""); refreshSpeed(); systemBars(); showControls(false);
        if(!SitePolicy.web(currentUrl())) showHome(); else setHomeVisible(false);
        showFailure(); refreshLoading();
        tabsButton.setText(String.valueOf(tabs.size())); addons.bindInstalled();
    }
    private void showTabs() {
        String[] labels=new String[tabs.size()]; for(int i=0;i<tabs.size();i++) { GeckoSession tab=tabs.get(i); labels[i]=(tab==session?"● ":"")+(SitePolicy.web(urls.get(tab))?titles.getOrDefault(tab,SitePolicy.host(urls.get(tab)))+"\n"+SitePolicy.host(urls.get(tab)):"New tab"); }
        showDialog(new AlertDialog.Builder(this).setTitle("Tabs ("+tabs.size()+"/4)").setItems(labels,(d,index)->switchTab(tabs.get(index)))
            .setPositiveButton("New tab",(d,w)->newTab()).setNeutralButton("Close current",(d,w)->closeTab()).setNegativeButton("Done",null).create());
    }
    private void closeTab() {
        if(parent!=null) { closePopup(); return; }
        GeckoSession old=session; tabs.remove(old); browser.releaseSession(); old.close(); urls.remove(old); titles.remove(old); back.remove(old); mediaSessions.remove(old);
        forward.remove(old); loading.remove(old); failures.remove(old);
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
        if(dialog.getWindow()!=null) dialog.getWindow().setBackgroundDrawable(surface(Color.WHITE,24));
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
        closeFind(); mediaSessions.remove(session); titles.remove(session); forward.remove(session); loading.remove(session); failures.remove(session);
        browser.releaseSession(); urls.remove(session); back.remove(session); session.close();
        session = parent; parent = null; browser.setSession(session); session.setActive(foreground); runtime.getWebExtensionController().setTabActive(session,true);
        fullVideo = false; address.setText(currentUrl()); refreshSpeed(); systemBars(); refreshLoading(); showFailure();
    }
    private void goBack() {
        if(findBar.getVisibility()==View.VISIBLE) {closeFind();return;}
        if (fullVideo) { session.exitFullScreen(); return; }
        if (homeVisible) { finish(); return; }
        if (Boolean.TRUE.equals(back.get(session))) session.goBack();
        else if (parent != null) closePopup(); else showHome();
    }
    // API 33+ uses the platform OnBackInvokedDispatcher registered in onCreate.
    // This override is exclusively the pre-33 hardware/gesture fallback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onResume() { super.onResume(); foreground = true; if (session != null) session.setActive(!homeVisible); }
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
