package com.garaquiz;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.contract.ActivityResultContracts;

public class MainActivity extends ComponentActivity {
    private ValueCallback<Uri[]> fileChooserCallback;
    private volatile String pendingExportJson;
    private Handler mainHandler;

    private androidx.activity.result.ActivityResultLauncher<String> createDocumentLauncher;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> fileChooserLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());

        createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    String json = pendingExportJson;
                    pendingExportJson = null;
                    if (json != null) {
                        try {
                            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                            if (os != null) {
                                java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(os, "UTF-8");
                                writer.write(json);
                                writer.flush();
                                writer.close();
                            }
                            Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    android.content.ClipData clipData = result.getData().getClipData();
                    if (clipData != null) {
                        int count = clipData.getItemCount();
                        Uri[] uris = new Uri[count];
                        for (int i = 0; i < count; i++) uris[i] = clipData.getItemAt(i).getUri();
                        if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(uris);
                    } else {
                        Uri uri = result.getData().getData();
                        if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(new Uri[]{uri});
                    }
                } else {
                    if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = null;
            });

        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void exportJson(String json, String filename) {
                pendingExportJson = json;
                mainHandler.post(() -> createDocumentLauncher.launch(filename));
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams params) {
                fileChooserCallback = filePathCallback;
                fileChooserLauncher.launch(params.createIntent());
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");

        FrameLayout layout = new FrameLayout(this);
        layout.addView(webView, new ViewGroup.LayoutParams(-1, -1));
        setContentView(layout);
    }
}
