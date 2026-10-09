package app.anibrave;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Browser;

/** Launches Brave's own zero-toolbar-height activity in the existing normal profile. */
public final class ShortcutLaunchActivity extends Activity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String url=getIntent().getDataString();
        if(!SiteKey.of(url).isEmpty()){
            Intent target=new Intent(Intent.ACTION_VIEW,getIntent().getData());
            target.setClassName(getPackageName(),"org.chromium.chrome.browser.customtabs.FullScreenCustomTabActivity");
            target.putExtra("app.anibrave.immersive",true);
            target.putExtra(Browser.EXTRA_APPLICATION_ID,getPackageName());
            target.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY",0);
            target.putExtra("android.support.customtabs.extra.ENABLE_URLBAR_HIDING",false);
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(target);
        }
        finish();
    }
}
