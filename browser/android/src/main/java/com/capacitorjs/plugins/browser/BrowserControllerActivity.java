package com.capacitorjs.plugins.browser;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * Hosts the in-app GeckoView browser used by the GeckoView Browser plugin.
 *
 * <p>The original {@code BrowserControllerActivity} delegates rendering to Chrome Custom Tabs. In
 * the GeckoView world there is no custom-tabs provider, so this activity renders the page inside a
 * {@link GeckoView} with a small toolbar offering back / forward / close actions. The toolbar is
 * built programmatically because an Android library ships no layout resources.
 */
public class BrowserControllerActivity extends Activity {

    private Browser browser;
    private GeckoView geckoView;
    private LinearLayout toolbar;
    private boolean canGoBack = false;
    private boolean canGoForward = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        // --- toolbar ---
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xFF3B3B3B);

        Button backButton = new Button(this);
        backButton.setText("‹");
        backButton.setOnClickListener(v -> {
            if (browser != null && canGoBack) {
                GeckoSession session = browser.getSession();
                if (session != null) {
                    session.goBack();
                }
            }
        });
        toolbar.addView(backButton);

        Button forwardButton = new Button(this);
        forwardButton.setText("›");
        forwardButton.setOnClickListener(v -> {
            if (browser != null && canGoForward) {
                GeckoSession session = browser.getSession();
                if (session != null) {
                    session.goForward();
                }
            }
        });
        toolbar.addView(forwardButton);

        Button closeButton = new Button(this);
        closeButton.setText("✕");
        closeButton.setOnClickListener(v -> finish());
        toolbar.addView(closeButton);

        root.addView(
            toolbar,
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        );

        // --- gecko view ---
        geckoView = new GeckoView(this);
        root.addView(
            geckoView,
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        );

        if (BrowserPlugin.browserControllerListener != null) {
            BrowserPlugin.browserControllerListener.onControllerReady(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("close")) {
            finish();
        }
    }

    /**
     * Attach the browser session to the GeckoView, wire up navigation state and load the URL.
     *
     * @param implementation the Browser instance driving the session.
     * @param url the URL to open.
     * @param toolbarColor the toolbar color, or {@code null} for the default.
     */
    public void open(Browser implementation, Uri url, Integer toolbarColor) {
        this.browser = implementation;

        GeckoSession session = implementation.createSession();
        session.setNavigationDelegate(
            new GeckoSession.NavigationDelegate() {
                @Override
                public void onCanGoBack(GeckoSession session, boolean value) {
                    canGoBack = value;
                }

                @Override
                public void onCanGoForward(GeckoSession session, boolean value) {
                    canGoForward = value;
                }
            }
        );
        geckoView.setSession(session);

        if (toolbarColor != null) {
            applyToolbarColor(toolbarColor);
        }

        implementation.open(url, toolbarColor);
    }

    private void applyToolbarColor(Integer color) {
        if (toolbar != null) {
            toolbar.setBackgroundColor(color);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (browser != null) {
            browser.notifyFinished();
            browser.dispose();
        }
        BrowserPlugin.setBrowserControllerListener(null);
    }
}
