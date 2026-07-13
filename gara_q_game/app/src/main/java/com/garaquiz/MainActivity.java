package com.garaquiz;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingExportJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    String json = pendingExportJson;
                    pendingExportJson = null;
                    if (json != null) {
                        try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                            if (os != null) {
                                os.write(json.getBytes("UTF-8"));
                            }
                            Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        );

        var fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    var clipData = result.getData().getClipData();
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
            }
        );

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
                createDocumentLauncher.launch(filename);
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(
                    "(function(){window.exportQuestions=function(){" +
                    "var json=JSON.stringify(window.questions);" +
                    "var name='GARA_QUIZ_DATA_'+new Date().toISOString().slice(0,10)+'.json';" +
                    "AndroidBridge.exportJson(json,name);};})();", null);
            }
        });

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

    @Override
    public void onBackPressed() {
        WebView webView = (WebView) ((FrameLayout) findViewById(android.R.id.content)).getChildAt(0);
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
