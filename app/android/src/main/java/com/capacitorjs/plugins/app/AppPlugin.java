package com.capacitorjs.plugins.app;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.os.LocaleListCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.util.InternalUtils;
import java.util.Locale;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * GeckoView implementation of the Capacitor App plugin.
 *
 * <p>The original plugin relies on {@code bridge.getWebView().canGoBack()/goBack()} for the
 * hardware-back-button handling. GeckoView's {@link GeckoSession} does not expose a synchronous
 * {@code canGoBack()} getter; instead the ability is reported asynchronously through
 * {@link GeckoSession.NavigationDelegate#onCanGoBack}. This implementation tracks that state so
 * the {@code backButton} event and default back navigation behave identically to the WebView
 * plugin.
 */
@CapacitorPlugin(name = "App")
public class AppPlugin extends Plugin {

    private static final String EVENT_BACK_BUTTON = "backButton";
    private static final String EVENT_URL_OPEN = "appUrlOpen";
    private static final String EVENT_STATE_CHANGE = "appStateChange";
    private static final String EVENT_RESTORED_RESULT = "appRestoredResult";
    private static final String EVENT_PAUSE = "pause";
    private static final String EVENT_RESUME = "resume";
    private boolean hasPausedEver = false;

    private OnBackPressedCallback onBackPressedCallback;

    /** Tracked ability to navigate back, reported by GeckoSession.NavigationDelegate. */
    private boolean canGoBack = false;

    public void load() {
        boolean disableBackButtonHandler = getConfig().getBoolean("disableBackButtonHandler", false);

        bridge.getApp().setStatusChangeListener((isActive) -> {
            Logger.debug(getLogTag(), "Firing change: " + isActive);
            JSObject data = new JSObject();
            data.put("isActive", isActive);
            notifyListeners(EVENT_STATE_CHANGE, data, false);
        });
        bridge.getApp().setAppRestoredListener((result) -> {
            Logger.debug(getLogTag(), "Firing restored result");
            notifyListeners(EVENT_RESTORED_RESULT, result.getWrappedResult(), true);
        });

        final GeckoSession session = obtainGeckoSession();
        if (session != null) {
            session.setNavigationDelegate(
                new GeckoSession.NavigationDelegate() {
                    @Override
                    public void onCanGoBack(GeckoSession session, boolean value) {
                        canGoBack = value;
                    }
                }
            );
        }

        this.onBackPressedCallback = new OnBackPressedCallback(!disableBackButtonHandler) {
            @Override
            public void handleOnBackPressed() {
                if (!hasListeners(EVENT_BACK_BUTTON)) {
                    if (session != null && canGoBack) {
                        session.goBack();
                    }
                } else {
                    JSObject data = new JSObject();
                    data.put("canGoBack", canGoBack);
                    notifyListeners(EVENT_BACK_BUTTON, data, true);
                    bridge.triggerJSEvent("backbutton", "document");
                }
            }
        };

        getActivity().getOnBackPressedDispatcher().addCallback(getActivity(), this.onBackPressedCallback);
    }

    /**
     * Retrieves the {@link GeckoSession} backing the app's web content, if the app is running on
     * a GeckoView-based bridge.
     */
    private GeckoSession obtainGeckoSession() {
        if (getBridge().getWebView() instanceof GeckoView) {
            GeckoSession session = ((GeckoView) getBridge().getWebView()).getSession();
            if (session != null) {
                return session;
            }
        }
        return null;
    }

    @PluginMethod
    public void exitApp(PluginCall call) {
        unsetAppListeners();
        call.resolve();
        getBridge().getActivity().finish();
    }

    @PluginMethod
    public void getInfo(PluginCall call) {
        JSObject data = new JSObject();
        try {
            PackageInfo pinfo = InternalUtils.getPackageInfo(getContext().getPackageManager(), getContext().getPackageName());
            ApplicationInfo applicationInfo = getContext().getApplicationInfo();
            int stringId = applicationInfo.labelRes;
            String appName = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : getContext().getString(stringId);
            data.put("name", appName);
            data.put("id", pinfo.packageName);
            data.put("build", Integer.toString((int) PackageInfoCompat.getLongVersionCode(pinfo)));
            data.put("version", pinfo.versionName);
            call.resolve(data);
        } catch (Exception ex) {
            call.reject("Unable to get App Info");
        }
    }

    @PluginMethod
    public void getLaunchUrl(PluginCall call) {
        Uri launchUri = bridge.getIntentUri();
        if (launchUri != null) {
            JSObject d = new JSObject();
            d.put("url", launchUri.toString());
            call.resolve(d);
        } else {
            call.resolve();
        }
    }

    @PluginMethod
    public void getState(PluginCall call) {
        JSObject data = new JSObject();
        data.put("isActive", this.bridge.getApp().isActive());
        call.resolve(data);
    }

    @PluginMethod
    public void minimizeApp(PluginCall call) {
        getActivity().moveTaskToBack(true);
        call.resolve();
    }

    @PluginMethod
    public void toggleBackButtonHandler(PluginCall call) {
        if (this.onBackPressedCallback == null) {
            call.reject("onBackPressedCallback is not set");
            return;
        }

        Boolean enabled = call.getBoolean("enabled");

        this.onBackPressedCallback.setEnabled(enabled);
        call.resolve();
    }

    @PluginMethod
    public void getAppLanguage(PluginCall call) {
        JSObject ret = new JSObject();
        LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
        Locale appLocale = !appLocales.isEmpty() ? appLocales.get(0) : null;
        ret.put("value", appLocale != null ? appLocale.getLanguage() : Locale.getDefault().getLanguage());
        call.resolve(ret);
    }

    /**
     * Handle ACTION_VIEW intents to store a URL that was used to open the app
     * @param intent
     */
    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        // read intent
        String action = intent.getAction();
        Uri url = intent.getData();

        if (!Intent.ACTION_VIEW.equals(action) || url == null) {
            return;
        }

        JSObject ret = new JSObject();
        ret.put("url", url.toString());
        notifyListeners(EVENT_URL_OPEN, ret, true);
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        hasPausedEver = true;
        notifyListeners(EVENT_PAUSE, null);
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        if (hasPausedEver) {
            notifyListeners(EVENT_RESUME, null);
        }
    }

    @Override
    protected void handleOnDestroy() {
        unsetAppListeners();
    }

    private void unsetAppListeners() {
        bridge.getApp().setStatusChangeListener(null);
        bridge.getApp().setAppRestoredListener(null);
    }
}
