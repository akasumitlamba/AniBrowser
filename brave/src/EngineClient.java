package app.anibrave;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class EngineClient implements Runnable {
    static final class Page {
        final String session, target, type, parent;
        volatile String url;
        volatile String metricsKey="";
        final Set<Integer> contexts = ConcurrentHashMap.newKeySet();
        Page(String s, String t, String ty, String p, String u) { session=s; target=t; type=ty; parent=p; url=u; }
    }
    private final Context context;
    private final String source;
    private final AtomicInteger next = new AtomicInteger();
    private final Map<String,Page> pages = new ConcurrentHashMap<>();
    private final Map<Integer,String> iconRequests = new ConcurrentHashMap<>();
    private final Set<String> attachedTargets = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;
    private volatile LocalWebSocket socket;
    EngineClient(Context c) throws Exception {
        context=c;
        try (java.io.InputStream in=c.getAssets().open("anibrave/playback.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);source=new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    void stop() { running=false; LocalWebSocket s=socket; if (s!=null) try { s.close(); } catch(Exception ignored) {} }
    static JSONObject json(Object... items) throws JSONException {
        JSONObject result=new JSONObject(); for(int i=0;i<items.length;i+=2) result.put((String)items[i],items[i+1]); return result;
    }
    private int send(String method, JSONObject params, String session) throws Exception {
        int id=next.incrementAndGet(); JSONObject value=json("id",id,"method",method,"params",params);
        if(session!=null) value.put("sessionId",session);
        LocalWebSocket s=socket; if(s==null) throw new java.io.IOException("Engine reconnecting"); s.send(value.toString()); return id;
    }
    private Page root(Page p) {
        for(int depth=0;depth<12 && p.parent!=null;depth++) { Page parent=pages.get(p.parent); if(parent==null) break; p=parent; }
        return p;
    }
    private double rate(Page p) { return context.getSharedPreferences("anibrave_sites",0).getFloat("rate:"+SiteKey.of(root(p).url),1f); }
    private int mode(Page p) { return context.getSharedPreferences("anibrave_sites",0).getInt("mode:"+SiteKey.of(root(p).url),0); }
    private void configure(Page p,int id) throws Exception {
        if(SiteKey.of(root(p).url).isEmpty()) return;
        send("Runtime.evaluate",json("expression",source+";globalThis.__aniBrave.configure("+rate(p)+");","contextId",id),p.session);
    }
    private void view(Page p) throws Exception {
        if(SiteKey.of(root(p).url).isEmpty()) return;
        int mode=mode(p);
        String ua=mode==0 ? "Mozilla/5.0 (Linux; Android "+Build.VERSION.RELEASE+") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36"
            : "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36";
        send("Emulation.setUserAgentOverride",json("userAgent",ua,"platform",mode==0?"Linux armv8l":"Linux x86_64"),p.session);
        // Identity and layout remain separate. Default mobile/hybrid retain the physical viewport.
        if("page".equals(p.type)) {
            android.util.DisplayMetrics dm=context.getResources().getDisplayMetrics();
            String key=mode+":"+dm.widthPixels+":"+dm.heightPixels+":"+dm.density;
            if(key.equals(p.metricsKey))return;
            p.metricsKey=key;
            if(mode==2) {
                int width=1280, height=Math.max(480,Math.round(width*(float)dm.heightPixels/dm.widthPixels));
                send("Emulation.setDeviceMetricsOverride",json("width",width,"height",height,"deviceScaleFactor",1,"mobile",false,"scale",Math.min(1,dm.widthPixels/(dm.density*width))),p.session);
            } else send("Emulation.setDeviceMetricsOverride",json("width",Math.round(dm.widthPixels/dm.density),"height",Math.round(dm.heightPixels/dm.density),"deviceScaleFactor",dm.density,"mobile",true),p.session);
        }
    }
    private void attach(JSONObject info,String parent) throws Exception {
        String type=info.optString("type"), id=info.optString("targetId");
        if((type.equals("page")||(type.equals("iframe")&&parent!=null)) && attachedTargets.add(id)) {
            send("Target.attachToTarget",json("targetId",id,"flatten",true),parent);
        }
    }
    void refresh(String site,boolean reload) {
        try {
            for(Page p:pages.values()) if(SiteKey.of(root(p).url).equals(site)) {
                for(int id:p.contexts) configure(p,id);
                if(reload) { view(p); if(p.type.equals("page")) send("Page.reload",json("ignoreCache",false),p.session); }
            }
        } catch(Exception e) { AniRuntime.engineWarning("Settings will apply when the page reconnects"); }
    }
    void icon(String url) {
        try {
            for(Page p:pages.values()) if(p.type.equals("page") && p.url.equals(url) && !p.contexts.isEmpty()) {
                int id=send("Runtime.evaluate",json("expression","JSON.stringify(globalThis.__aniBrave.icon())","contextId",p.contexts.iterator().next(),"returnByValue",true),p.session);
                iconRequests.put(id,url); return;
            }
        } catch(Exception ignored) {}
        AniRuntime.pin(url,"","");
    }
    public void run() {
        while(running) {
            try {
                JSONObject version=new JSONObject(LocalWebSocket.get("/json/version"));
                String path=URI.create(version.getString("webSocketDebuggerUrl")).getRawPath();
                socket=new LocalWebSocket(path); pages.clear(); attachedTargets.clear(); iconRequests.clear();
                send("Target.setDiscoverTargets",json("discover",true),null);
                send("Target.setAutoAttach",json("autoAttach",true,"waitForDebuggerOnStart",false,"flatten",true),null);
                send("Target.getTargets",json(),null);
                AniRuntime.engineReady();
                while(running) receive(new JSONObject(socket.read()));
            } catch(Exception e) {
                if(running) android.util.Log.w("AniBrave", "Page control connection failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if(running) AniRuntime.engineWarning("Connecting to the browser…");
            } finally { LocalWebSocket s=socket; socket=null; if(s!=null) try{s.close();}catch(Exception ignored){} }
            if(running) try{Thread.sleep(1500);}catch(InterruptedException e){break;}
        }
    }
    private void receive(JSONObject message) throws Exception {
        String method=message.optString("method"), session=message.optString("sessionId",null);
        JSONObject params=message.optJSONObject("params"); if(params==null) params=new JSONObject();
        if(message.has("id")) {
            String url=iconRequests.remove(message.optInt("id"));
            if(url!=null) {
                JSONObject wrapper=message.optJSONObject("result");
                JSONObject result=wrapper==null?null:wrapper.optJSONObject("result");
                try { JSONObject data=new JSONObject(result.getString("value")); AniRuntime.pin(url,data.optString("title"),data.optString("icon")); }
                catch(Exception e){AniRuntime.pin(url,"","");} return;
            }
            JSONObject result=message.optJSONObject("result"); JSONArray targets=result==null?null:result.optJSONArray("targetInfos");
            if(targets!=null) for(int i=0;i<targets.length();i++) attach(targets.getJSONObject(i),null);
            if(message.has("error")) {android.util.Log.w("AniBrave", "Page command failed: " + message.optJSONObject("error")); AniRuntime.engineWarning("Some page controls are unavailable; reopen the site");}
            return;
        }
        if(method.equals("Target.targetCreated")) { attach(params.getJSONObject("targetInfo"),session); return; }
        if(method.equals("Target.targetInfoChanged")) {
            JSONObject info=params.getJSONObject("targetInfo");
            for(Page page:pages.values()) if(page.target.equals(info.optString("targetId"))) {
                page.url=info.optString("url");
                view(page);for(int id:page.contexts)configure(page,id);
            }
            return;
        }
        if(method.equals("Target.attachedToTarget")) {
            JSONObject info=params.getJSONObject("targetInfo"); String id=params.getString("sessionId");
            Page p=new Page(id,info.getString("targetId"),info.optString("type"),session,info.optString("url"));
            if(!p.type.equals("page")&&!p.type.equals("iframe")) return;
            attachedTargets.add(p.target); pages.put(id,p);
            send("Target.setAutoAttach",json("autoAttach",true,"waitForDebuggerOnStart",false,"flatten",true),id);
            send("Runtime.addBinding",json("name","aniBraveNative","executionContextName","AniBrave"),id);
            send("Page.enable",json(),id);
            view(p);
            send("Page.addScriptToEvaluateOnNewDocument",json("source",source,"worldName","AniBrave","runImmediately",true),id);
            send("Runtime.enable",json(),id); return;
        }
        if(method.equals("Target.detachedFromTarget")) { Page p=pages.remove(params.optString("sessionId")); if(p!=null) attachedTargets.remove(p.target); return; }
        if(session==null)return; // Browser-level events have no page session.
        Page p=pages.get(session); if(p==null) return;
        if(method.equals("Runtime.executionContextCreated")) {
            JSONObject c=params.getJSONObject("context"); if(c.optString("name").equals("AniBrave")) {int id=c.getInt("id");p.contexts.add(id);configure(p,id);} return;
        }
        if(method.equals("Runtime.executionContextDestroyed")) {p.contexts.remove(params.optInt("executionContextId"));return;}
        if(method.equals("Runtime.executionContextsCleared")) {p.contexts.clear();return;}
        if(method.equals("Page.frameNavigated")) {
            JSONObject frame=params.getJSONObject("frame");
            if(!frame.has("parentId") && p.parent==null) { p.url=frame.optString("url"); view(p); for(int id:p.contexts) configure(p,id); }
            return;
        }
        if(method.equals("Runtime.bindingCalled") && params.optString("name").equals("aniBraveNative") && p.contexts.contains(params.optInt("executionContextId"))) {
            String payload=params.optString("payload"); if(payload.length()>4096) return;
            JSONObject data=new JSONObject(payload);
            String url=root(p).url;
            if(data.optString("type").equals("reveal")) AniRuntime.reveal(url);
            else if(data.optString("type").equals("resize")) view(root(p));
            else if(data.optString("type").equals("fullscreen")) AniRuntime.fullscreen(url,data.optBoolean("enabled"));
            else if(data.optString("type").equals("rateRejected")) AniRuntime.engineWarning("This player is rejecting the saved speed");
        }
    }
}
