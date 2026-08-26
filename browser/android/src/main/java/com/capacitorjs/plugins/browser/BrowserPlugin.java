package com.capacitorjs.plugins.browser;

import android.content.Intent;
import android.net.Uri;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.util.WebColor;

/**
 * GeckoView implementation of the Capacitor Browser plugin.
 *
 * <p>The plugin entry point is identical to the stock plugin; only the rendering backend differs
 * (in-app {@link GeckoView} browser instead of Chrome Custom Tabs).
 */
@CapacitorPlugin(name = "Browser")
public class BrowserPlugin extends Plugin {

    private Browser implementation;

    public static BrowserControllerListener browserControllerListener;
    private static BrowserControllerActivity browserControllerActivityInstance;

    public static void setBrowserControllerListener(BrowserControllerListener listener) {
        browserControllerListener = listener;
        if (listener == null) {
            browserControllerActivityInstance = null;
        }
    }

    public void load() {
        implementation = new Browser(getContext());
        implementation.setBrowserEventListener(this::onBrowserEvent);
    }

    @PluginMethod
    public void open(PluginCall call) {
        // get the URL
        String urlString = call.getString("url");
        if (urlString == null) {
            call.reject("Must provide a URL to open");
            return;
        }
        if (urlString.isEmpty()) {
            call.reject("URL must not be empty");
            return;
        }
        Uri url;
        try {
            url = Uri.parse(urlString);
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage());
            return;
        }

        // get the toolbar color, if provided
        String colorString = call.getString("toolbarColor");
        Integer toolbarColor = null;
        if (colorString != null) try {
            toolbarColor = WebColor.parseColor(colorString);
        } catch (IllegalArgumentException ex) {
            Logger.error(getLogTag(), "Invalid color provided for toolbarColor. Using default", null);
        }

        // open the in-app GeckoView browser
        Intent intent = new Intent(getContext(), BrowserControllerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);

        Integer finalToolbarColor = toolbarColor;
        setBrowserControllerListener((activity) -> {
            activity.open(implementation, url, finalToolbarColor);
            browserControllerActivityInstance = activity;
            call.resolve();
        });
    }

    @PluginMethod
    public void close(PluginCall call) {
        if (browserControllerActivityInstance != null) {
            browserControllerActivityInstance = null;
            Intent intent = new Intent(getContext(), BrowserControllerActivity.class);
            intent.putExtra("close", true);
            getContext().startActivity(intent);
        }
        call.resolve();
    }

    void onBrowserEvent(int event) {
        switch (event) {
            case Browser.BROWSER_LOADED:
                notifyListeners("browserPageLoaded", null);
                break;
            case Browser.BROWSER_FINISHED:
                notifyListeners("browserFinished", null);
                break;
        }
    }
}
