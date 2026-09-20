/* SPDX-License-Identifier: MPL-2.0 */
package app.anibrowser.lite;

import android.app.Application;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

public final class LiteApplication extends Application {
    private GeckoRuntime runtime;
    org.mozilla.geckoview.WebExtension.Port playbackPort;
    public GeckoRuntime runtime() {
        if (runtime == null) {
            runtime = GeckoRuntime.create(this, new GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(false).consoleOutput(false).debugLogging(false)
                .loginAutofillEnabled(false).webManifest(false).lowMemoryDetection(true)
                .build());
        }
        return runtime;
    }
}
