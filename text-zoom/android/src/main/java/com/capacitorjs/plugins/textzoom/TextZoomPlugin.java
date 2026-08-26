package com.capacitorjs.plugins.textzoom;

import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * GeckoView implementation of the Capacitor TextZoom plugin.
 *
 * <p>The original plugin reads/writes text zoom through {@code android.webkit.WebSettings}. This
 * GeckoView variant delegates the same operations to the {@code GeckoRuntime} font size factor.
 */
@CapacitorPlugin(name = "TextZoom")
public class TextZoomPlugin extends Plugin {

    private TextZoom textZoom;
    private Handler mainHandler;

    @Override
    public void load() {
        textZoom = new TextZoom(getActivity());
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @PluginMethod
    public void get(final PluginCall call) {
        mainHandler.post(() -> {
            JSObject ret = new JSObject();
            ret.put("value", textZoom.get());
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void set(final PluginCall call) {
        mainHandler.post(() -> {
            Double value = call.getDouble("value");

            if (value == null) {
                call.reject("Invalid integer value.");
            } else {
                textZoom.set(value);
                call.resolve();
            }
        });
    }

    @PluginMethod
    public void getPreferred(final PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("value", textZoom.getPreferred());
        call.resolve(ret);
    }
}
