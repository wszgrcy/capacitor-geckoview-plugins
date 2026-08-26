package com.capacitorjs.plugins.browser;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * GeckoView implementation of the Capacitor Browser plugin.
 *
 * <p>The original plugin launches the page through Chrome Custom Tabs
 * ({@code androidx.browser.customtabs}). GeckoView has no equivalent "custom tabs" provider, so
 * this implementation renders the requested page inside an in-app browser built on
 * {@link GeckoSession}/{@link GeckoView}. It preserves the plugin's event contract:
 * <ul>
 *   <li>{@link #BROWSER_LOADED} — fired when the initial URL finishes loading.</li>
 *   <li>{@link #BROWSER_FINISHED} — fired when the in-app browser is closed by the user.</li>
 * </ul>
 */
public class Browser {

    /**
     * Interface for callbacks for browser events.
     */
    interface BrowserEventListener {
        void onBrowserEvent(int event);
    }

    /** Sent when the browser has loaded the initial page. */
    public static final int BROWSER_LOADED = 1;
    /** Sent when the browser is finished. */
    public static final int BROWSER_FINISHED = 2;

    @Nullable
    private BrowserEventListener browserEventListener;

    private final Context context;
    private GeckoRuntime runtime;
    private GeckoSession session;
    private boolean isInitialLoad = false;
    private boolean isFinishedNotified = false;

    /**
     * Create the GeckoView-based browser object.
     *
     * @param context the application context.
     */
    public Browser(@NonNull Context context) {
        this.context = context;
    }

    /**
     * Set the object to receive callbacks.
     *
     * @param listener the listener.
     */
    public void setBrowserEventListener(@Nullable BrowserEventListener listener) {
        this.browserEventListener = listener;
    }

    /**
     * @return the process-level Gecko runtime, creating it lazily if needed.
     */
    public GeckoRuntime getRuntime() {
        if (runtime == null) {
            runtime = GeckoRuntime.getDefault(context);
        }
        return runtime;
    }

    /**
     * Create and open a new {@link GeckoSession} wired up with the progress delegate needed to
     * emit the {@code browserPageLoaded} event.
     *
     * @return the created session.
     */
    public GeckoSession createSession() {
        GeckoSession newSession = new GeckoSession();
        newSession.setProgressDelegate(
            new GeckoSession.ProgressDelegate() {
                @Override
                public void onPageStop(GeckoSession session, boolean success) {
                    if (isInitialLoad) {
                        isInitialLoad = false;
                        if (browserEventListener != null) {
                            browserEventListener.onBrowserEvent(BROWSER_LOADED);
                        }
                    }
                }
            }
        );
        newSession.open(getRuntime());
        this.session = newSession;
        return newSession;
    }

    /**
     * Load the specified URL in the active session.
     *
     * @param url the URL to load.
     * @param toolbarColor ignored; toolbar color is applied by the controller activity.
     */
    public void open(Uri url, @Nullable Integer toolbarColor) {
        isInitialLoad = true;
        isFinishedNotified = false;
        if (session != null) {
            session.loadUri(url.toString());
        }
    }

    /**
     * Notify the plugin (once) that the in-app browser was closed, firing {@code browserFinished}.
     */
    public void notifyFinished() {
        if (!isFinishedNotified) {
            isFinishedNotified = true;
            if (browserEventListener != null) {
                browserEventListener.onBrowserEvent(BROWSER_FINISHED);
            }
        }
    }

    /**
     * Release the session and the resources associated with it.
     */
    public void dispose() {
        if (session != null) {
            session.close();
            session = null;
        }
        isFinishedNotified = true;
    }

    /** @return the active session, or {@code null} if none has been created yet. */
    @Nullable
    public GeckoSession getSession() {
        return session;
    }
}
