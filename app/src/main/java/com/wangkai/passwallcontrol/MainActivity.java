package com.wangkai.passwallcontrol;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "passwall_control_prefs";
    private static final String KEY_ADDRESS = "router_address";
    private static final String DEFAULT_ADDRESS = "10.1.1.1";
    private static final String ACL_PATH = "/cgi-bin/luci/admin/services/passwall/acl";

    private WebView webView;
    private EditText addressInput;
    private ProgressBar progressBar;
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(10), dp(8), dp(10), dp(6));
        toolbar.setBackgroundColor(Color.rgb(246, 248, 252));

        TextView title = new TextView(this);
        title.setText("PassWall 访问控制");
        title.setTextSize(18);
        title.setTextColor(Color.rgb(30, 45, 70));
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(30)
        ));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);

        addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setTextSize(14);
        addressInput.setHint("10.1.1.1 或 http://10.1.1.1:端口");
        addressInput.setText(prefs.getString(KEY_ADDRESS, DEFAULT_ADDRESS));
        addressInput.setSelectAllOnFocus(false);
        addressRow.addView(addressInput, new LinearLayout.LayoutParams(
                0,
                dp(44),
                1
        ));

        Button openButton = makeButton("打开");
        Button refreshButton = makeButton("刷新");
        Button backButton = makeButton("返回");
        addressRow.addView(openButton);
        addressRow.addView(refreshButton);
        addressRow.addView(backButton);
        toolbar.addView(addressRow);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        root.addView(toolbar);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        setContentView(root);

        configureWebView();

        openButton.setOnClickListener(v -> loadAclPage());
        refreshButton.setOnClickListener(v -> webView.reload());
        backButton.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                Toast.makeText(this, "已经是当前页面", Toast.LENGTH_SHORT).show();
            }
        });

        loadAclPage();
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(44));
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " PassWallControl/1.0");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                    return false;
                }
                Toast.makeText(MainActivity.this, "不支持的链接：" + uri, Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                injectCompactMode();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this, "页面打开失败，请检查地址或网络", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void loadAclPage() {
        String raw = addressInput.getText().toString().trim();
        if (raw.length() == 0) {
            raw = DEFAULT_ADDRESS;
        }
        prefs.edit().putString(KEY_ADDRESS, raw).apply();

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "当前手机网络不可用", Toast.LENGTH_SHORT).show();
        }

        String url = normalizeToAclUrl(raw);
        webView.loadUrl(url);
    }

    private String normalizeToAclUrl(String raw) {
        String value = raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }

        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String authority = uri.getEncodedAuthority();
        if (authority == null || authority.length() == 0) {
            return "http://" + DEFAULT_ADDRESS + ACL_PATH;
        }

        String path = uri.getPath();
        if (path != null && path.contains("/cgi-bin/luci/admin/services/passwall/acl")) {
            return value;
        }

        return scheme + "://" + authority + ACL_PATH;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void injectCompactMode() {
        String js = "javascript:(function(){" +
                "try{" +
                "var css='" +
                "html,body{max-width:100%!important;overflow-x:auto!important;}" +
                ".main-left,.sidebar,aside,#mainmenu,#modemenu,.breadcrumb{display:none!important;}" +
                "header,.navbar,.brand,.pull-left{display:none!important;}" +
                ".main,.main-right,.container,.container-fluid,#maincontent{margin-left:0!important;left:0!important;width:100%!important;max-width:100%!important;padding-left:8px!important;padding-right:8px!important;}" +
                ".cbi-map,.cbi-section,.cbi-section-node{max-width:100%!important;width:100%!important;}" +
                "table,.table{width:100%!important;display:block!important;overflow-x:auto!important;}" +
                "input,select,textarea,button,.btn,.cbi-button{min-height:40px!important;font-size:15px!important;}" +
                ".cbi-value-title{min-width:92px!important;}" +
                "';" +
                "var style=document.getElementById('pw-acl-compact-style');" +
                "if(!style){style=document.createElement('style');style.id='pw-acl-compact-style';document.head.appendChild(style);}" +
                "style.innerHTML=css;" +
                "document.title='PassWall 访问控制';" +
                "}catch(e){}" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
