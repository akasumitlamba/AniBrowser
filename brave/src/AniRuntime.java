package app.anibrave;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

/** Lifecycle-bound native UI; Chromium and the website retain ownership of playback. */
public final class AniRuntime {
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static WeakReference<Activity> active=new WeakReference<>(null);
    private static Context context;
    private static EngineClient engine;
    private static Object server;
    private static long serverPointer;
    private static boolean registered, immersive;
    private static PopupWindow tools;
    private static View featureButton;
    private static ViewTreeObserver.OnGlobalLayoutListener toolbarListener;
    private static View toolbarDecor;
    private static String status="Connecting to the browser…";
    private static final double[] RATES={0.5,0.75,1,1.25,1.5,1.75,2,2.5,3,4};
    private static final Runnable HIDE=()->{if(tools!=null)tools.dismiss();};

    private AniRuntime() {}
    public static String blankNewTab(String url) {return SiteKey.blankNewTab(url);}
    private static boolean alive(Activity a) {return a!=null&&!a.isFinishing()&&!a.isDestroyed();}
    public static void server(Object value,long pointer) {
        server=value;serverPointer=pointer;toggleServer(alive(active.get()));
    }
    private static void toggleServer(boolean enabled) {
        if(serverPointer==0)return;
        try { Class.forName("J.N").getMethod("VJZZ",int.class,long.class,boolean.class,boolean.class).invoke(null,0,serverPointer,enabled,true); }
        catch(Exception e){status="Browser controls could not connect";}
    }
    public static void intent(Activity a,Intent intent) {
        a.setIntent(intent);
        immersive=intent.getBooleanExtra("app.anibrave.immersive",false);
        if(alive(a))UI.post(()->{if(alive(a))applyImmersive(a);});
    }
    public static void resume(Activity a) {
        if(!alive(a))return;
        if(context==null)context=a.getApplicationContext();
        if(!registered) {
            registered=true;
            ((Application)context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityCreated(Activity a,Bundle b){}
                public void onActivityStarted(Activity a){}
                public void onActivityResumed(Activity a){String name=a.getClass().getName();if(name.equals("org.chromium.chrome.browser.ChromeTabbedActivity")||name.equals("org.chromium.chrome.browser.customtabs.FullScreenCustomTabActivity"))resume(a);}
                public void onActivityPaused(Activity a){if(active.get()==a)pause();}
                public void onActivityStopped(Activity a){}
                public void onActivitySaveInstanceState(Activity a,Bundle b){}
                public void onActivityDestroyed(Activity a){if(active.get()==a)pause();}
            });
        }
        if(active.get()!=a){dismiss();active=new WeakReference<>(a);immersive=a.getClass().getName().endsWith("FullScreenCustomTabActivity")||a.getIntent().getBooleanExtra("app.anibrave.immersive",false);}
        toggleServer(true);
        if(engine==null) {
            try{engine=new EngineClient(context);Thread thread=new Thread(engine,"AniBrave-page-controls");thread.setDaemon(true);thread.start();}
            catch(Exception e){status="Page controls could not start";}
        }
        UI.post(()->{if(alive(a)&&active.get()==a){applyImmersive(a);bindToolbar(a);}});
    }
    private static void pause() {
        if(engine!=null){engine.stop();engine=null;}
        toggleServer(false);dismiss();active=new WeakReference<>(null);
    }
    private static void dismiss(){UI.removeCallbacks(HIDE);if(tools!=null){tools.dismiss();tools=null;}if(toolbarDecor!=null&&toolbarListener!=null&&toolbarDecor.getViewTreeObserver().isAlive())toolbarDecor.getViewTreeObserver().removeOnGlobalLayoutListener(toolbarListener);toolbarDecor=null;toolbarListener=null;featureButton=null;}
    private static void applyImmersive(Activity a) {
        // Ordinary tabs retain Brave's fullscreen and scroll-driven toolbar state.
        if(!immersive)return;
        View decor=a.getWindow().getDecorView();
        if(immersive){
            if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=a.getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}}
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }else{a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
        try {
            Field field=a.getClass().getField("T2");View toolbar=(View)field.get(a);
            if(toolbar!=null){toolbar.setVisibility(immersive?View.GONE:View.VISIBLE);}
        }catch(Exception ignored){}
    }
    static String currentUrl() {
        Activity a=active.get();if(!alive(a))return "";
        try {
            Object tab=a.getClass().getMethod("m4").invoke(a);if(tab==null)return "";
            Object url=tab.getClass().getMethod("getUrl").invoke(tab);
            return (String)url.getClass().getField("a").get(url);
        }catch(Exception e){return "";}
    }
    private static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    private static Button action(Activity a,String text,Runnable click){
        Button result=new Button(a);result.setText(text);result.setTextColor(Color.WHITE);result.setTextSize(13);result.setAllCaps(false);result.setMinWidth(0);result.setMinimumWidth(0);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff171717);bg.setCornerRadius(dp(a,14));result.setBackground(bg);
        result.setPadding(dp(a,12),dp(a,7),dp(a,12),dp(a,7));result.setContentDescription(text);result.setOnClickListener(v->click.run());return result;
    }
    private static void bindToolbar(Activity a){
        if(immersive)return;
        if(toolbarListener!=null)return;
        toolbarDecor=a.getWindow().getDecorView();
        toolbarListener=()->{
            if(!alive(a)||active.get()!=a)return;
            int id=a.getResources().getIdentifier("anibrave_features","id",a.getPackageName());
            View icon=toolbarDecor.findViewById(id);
            if(icon!=null&&icon!=featureButton){featureButton=icon;icon.setOnClickListener(v->showTools(a));}
        };
        toolbarDecor.getViewTreeObserver().addOnGlobalLayoutListener(toolbarListener);
        toolbarListener.onGlobalLayout();
    }
    private static void showTools(Activity a){
        if(!alive(a)||active.get()!=a)return;
        if(!immersive){
            if(featureButton==null)return;
            PopupMenu menu=new PopupMenu(a,featureButton);
            menu.getMenu().add(0,1,0,"Playback speed");menu.getMenu().add(0,2,1,"Site view");menu.getMenu().add(0,3,2,"Fullscreen shortcut");
            menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)speed(a);else if(item.getItemId()==2)view(a);else shortcut(a);return true;});menu.show();return;
        }
        if(tools!=null)tools.dismiss();
        LinearLayout row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(a,4),dp(a,4),dp(a,4),dp(a,4));
        row.addView(action(a,"Speed",()->speed(a)));
        row.addView(action(a,"Site view",()->view(a)));
        row.addView(action(a,"Shortcut",()->shortcut(a)));
        row.addView(action(a,"×",()->HIDE.run()));
        tools=new PopupWindow(row,WindowManager.LayoutParams.WRAP_CONTENT,dp(a,52),false);tools.setBackgroundDrawable(new ColorDrawable(0xff101010));tools.setElevation(dp(a,8));
        try{tools.showAtLocation(a.getWindow().getDecorView(),Gravity.TOP|Gravity.END,dp(a,8),dp(a,immersive?62:166));}catch(WindowManager.BadTokenException ignored){tools=null;}
        UI.removeCallbacks(HIDE);UI.postDelayed(HIDE,4500);
    }
    private static String requireSite(Activity a){String site=SiteKey.of(currentUrl());if(site.isEmpty())Toast.makeText(a,"Open a website first",Toast.LENGTH_SHORT).show();return site;}
    private static void speed(Activity a){
        String site=requireSite(a);if(site.isEmpty())return;HIDE.run();String[] labels=new String[RATES.length];int selected=2;
        float saved=context.getSharedPreferences("anibrave_sites",0).getFloat("rate:"+site,1);
        for(int i=0;i<RATES.length;i++){labels[i]=RATES[i]+"×";if(Math.abs(saved-RATES[i])<0.001)selected=i;}
        new AlertDialog.Builder(a).setTitle("Playback speed · "+site).setSingleChoiceItems(labels,selected,(dialog,index)->{
            context.getSharedPreferences("anibrave_sites",0).edit().putFloat("rate:"+site,(float)RATES[index]).apply();
            EngineClient e=engine;if(e!=null)IO.execute(()->e.refresh(site,false));dialog.dismiss();
        }).setNegativeButton("Close",null).setNeutralButton("Status",(d,w)->Toast.makeText(a,status,Toast.LENGTH_LONG).show()).show();
    }
    private static void view(Activity a){
        String site=requireSite(a);if(site.isEmpty())return;HIDE.run();String[] labels={"Mobile","Desktop identity · mobile layout","Desktop"};
        new AlertDialog.Builder(a).setTitle("Site view · "+site).setSingleChoiceItems(labels,context.getSharedPreferences("anibrave_sites",0).getInt("mode:"+site,0),(d,index)->{
            context.getSharedPreferences("anibrave_sites",0).edit().putInt("mode:"+site,index).apply();EngineClient e=engine;if(e!=null)IO.execute(()->e.refresh(site,true));d.dismiss();
        }).setNegativeButton("Close",null).show();
    }
    private static void shortcut(Activity a){if(requireSite(a).isEmpty())return;HIDE.run();String url=currentUrl();EngineClient e=engine;if(e!=null)IO.execute(()->e.icon(url));else pin(url,"","");}
    static void engineReady(){status="Connected";}
    static void engineWarning(String value){status=value;}
    static void reveal(String url){UI.post(()->{Activity a=active.get();if(alive(a)&&immersive&&SiteKey.of(url).equals(SiteKey.of(currentUrl())))showTools(a);});}
    static void fullscreen(String url,boolean enabled){UI.post(()->{if(tools!=null)HIDE.run();});}
    static void pin(String url,String title,String iconUrl){
        Context c=context;if(c==null||SiteKey.of(url).isEmpty())return;
        IO.execute(()->{
            Bitmap bitmap=null;
            try{
                Uri icon=Uri.parse(iconUrl);
                if("https".equals(icon.getScheme())){
                    HttpURLConnection connection=(HttpURLConnection)new URL(iconUrl).openConnection();connection.setConnectTimeout(3000);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
                    try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Icon too large");out.write(buffer,0,n);}
                        byte[] data=out.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                        if(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth<=4096&&bounds.outHeight<=4096){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/192);bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);}
                    }finally{connection.disconnect();}
                }
            }catch(Exception ignored){}
            final Bitmap artwork=bitmap;
            UI.post(()->{
                Activity a=active.get();if(!alive(a))return;
                ShortcutManager manager=c.getSystemService(ShortcutManager.class);if(manager==null||!manager.isRequestPinShortcutSupported()){Toast.makeText(a,"Your launcher does not support pinned shortcuts",Toast.LENGTH_LONG).show();return;}
                Intent launch=new Intent(Intent.ACTION_VIEW,Uri.parse(url));launch.setClass(c,ShortcutLaunchActivity.class);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                String label=title.trim().isEmpty()?SiteKey.of(url):title.trim();if(label.length()>45)label=label.substring(0,45);
                Icon image=artwork==null?Icon.createWithResource(c,c.getApplicationInfo().icon):Icon.createWithBitmap(artwork);
                String id="site-"+Integer.toHexString(url.hashCode());
                ShortcutInfo info=new ShortcutInfo.Builder(c,id).setShortLabel(label).setLongLabel(label).setIcon(image).setIntent(launch).build();
                manager.requestPinShortcut(info,null);
            });
        });
    }
}
