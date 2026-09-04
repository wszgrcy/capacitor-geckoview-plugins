package com.capacitorjs.plugins.textzoom;

import android.app.Activity;
import com.getcapacitor.Bridge;
import org.mozilla.geckoview.GeckoRuntime;

/**
 * GeckoView implementation of text zoom.
 *
 * <p>In the stock Capacitor plugin, text zoom is applied through the Android WebView
 * {@code android.webkit.WebSettings#setTextZoom(int)}. GeckoView has no per-session text zoom
 * API; the equivalent global setting is the runtime-level {@code fontSizeFactor} exposed by
 * {@link org.mozilla.geckoview.GeckoRuntimeSettings#setFontSizeFactor(float)}. A factor of
 * {@code 1.0} means 100% (no scaling), {@code 1.2} means 120% — matching the decimal
 * zoom level contract of the Capacitor TextZoom plugin.
 */
public class TextZoom {

    private final Activity activity;
    private final GeckoRuntime runtime;

    TextZoom(Activity activity) {
        this.activity = activity;
        // Reuse the process-wide GeckoRuntime (only one is allowed per process).
        this.runtime = Bridge.getGeckoRuntime();
    }

    /** @return the current zoom level as a decimal (e.g. 1.2 == 120%). */
    public double get() {
        return runtime.getSettings().getFontSizeFactor();
    }

    /** @param level the new zoom level as a decimal (e.g. 1.2 == 120%). */
    public void set(double level) {
        runtime.getSettings().setFontSizeFactor((float) level);
    }

    /** @return the preferred zoom level derived from the system font scale. */
    public double getPreferred() {
        return Double.parseDouble(Float.valueOf(activity.getResources().getConfiguration().fontScale).toString());
    }
}
