package rs.knjigalokala.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Hosts the web app from the APK's assets at https://appassets.androidplatform.net/assets/www/. */
public class MainActivity extends Activity {
    private static final String START_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int PICK_FILE = 1;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Izaberite rezervnu kopiju"), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Writes bytes to the cache and opens the share sheet for them. */
    private boolean share(String name, byte[] data, String mime, String subject, String chooserTitle) {
        try {
            File dir = new File(getCacheDir(), "kopije");
            if (!dir.exists() && !dir.mkdirs()) return false;
            File f = new File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(data);
            }
            Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, subject);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, chooserTitle)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Called from the page as window.AndroidBridge. */
    private class Bridge {
        @JavascriptInterface
        public boolean shareFile(String name, String content) {
            return share(name, content.getBytes(StandardCharsets.UTF_8), "application/json",
                    "Knjiga lokala – rezervna kopija", "Sačuvaj ili pošalji kopiju");
        }

        @JavascriptInterface
        public boolean shareBase64(String name, String mime, String base64) {
            try {
                return share(name, Base64.decode(base64, Base64.DEFAULT), mime, "Knjiga lokala – izveštaj", "Pošalji izveštaj");
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        /** Saves a copy to Downloads/Knjiga lokala without asking (Android 10+, no storage permission needed). */
        @JavascriptInterface
        public boolean saveBackup(String name, String content) {
            if (Build.VERSION.SDK_INT < 29) return false;
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Knjiga lokala");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) return false;
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void scheduleReminders(String json) {
            Reminders.schedule(MainActivity.this, json);
        }

        @JavascriptInterface
        public boolean notificationsEnabled() {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
            return NotificationManagerCompat.from(MainActivity.this).areNotificationsEnabled();
        }

        /** Asks once with the system dialog; after that (or below Android 13) opens the app's notification settings. */
        @JavascriptInterface
        public void requestNotifications() {
            SharedPreferences p = getSharedPreferences("podesavanja", MODE_PRIVATE);
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && !p.getBoolean("notifAsked", false)) {
                    p.edit().putBoolean("notifAsked", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
                } else {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    try { startActivity(i); } catch (Exception ignored) { }
                }
            });
        }
    }
}
