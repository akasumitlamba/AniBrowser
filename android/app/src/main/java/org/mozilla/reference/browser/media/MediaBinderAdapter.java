package org.mozilla.reference.browser.media;
import android.os.IBinder;
import mozilla.components.feature.media.service.MediaServiceBinder;
import mozilla.components.feature.media.service.MediaSessionDelegate;
/** Adapter for the pinned Mozilla engine binder's JVM API. */
final class MediaBinderAdapter {
    static MediaSessionDelegate delegate(IBinder binder) {
        return binder instanceof MediaServiceBinder ? ((MediaServiceBinder) binder).getMediaService() : null;
    }
}
