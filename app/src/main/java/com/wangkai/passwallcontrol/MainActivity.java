package com.wangkai.passwallcontrol;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "passwall_control_prefs";
    private static final String KEY_ADDRESS = "router_address";
    private static final String KEY_USERNAME = "router_username";
    private static final String KEY_PASSWORD = "router_password";
    private static final String DEFAULT_ADDRESS = "10.1.1.1";
    private static final String DEFAULT_USERNAME = "root";
    private static final String PASSWALL_PATH = "/cgi-bin/luci/admin/services/passwall";
    private static final String ACL_PATH = "/cgi-bin/luci/admin/services/passwall/acl";

    private static final int MODE_IDLE = 0;
    private static final int MODE_CHECK_APP = 1;
    private static final int MODE_TOGGLE_APP = 2;
    private static final int MODE_CHECK_ACL = 3;
    private static final int MODE_TOGGLE_ACL = 4;
    private static final int MODE_LOAD_NODES = 5;
    private static final int MODE_SET_NODE = 6;
    private static final int MAX_PAGE_SCRIPT_RETRY = 8;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ScrollView scrollView;
    private LinearLayout settingsPanel;
    private WebView webView;
    private EditText addressInput, usernameInput, passwordInput;
    private TextView statusText, detailText, nodeHintText;
    private ProgressBar progressBar;
    private Button appSwitchButton, aclSwitchButton, settingsButton, openPageButton;
    private Spinner tcpNodeSpinner;
    private ArrayAdapter<String> nodeAdapter;
    private final List<String> nodeValues = new ArrayList<>();
    private final List<String> nodeLabels = new ArrayList<>();

    private int workMode = MODE_IDLE;
    private int pageScriptRetryCount = 0;
    private Boolean appEnabled = null;
    private Boolean aclEnabled = null;
    private boolean debugPageVisible = false;
    private boolean suppressNodeEvent = false;
    private String pendingNodeValue = null;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 253));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, 0, 0, dp(24));
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout mainArea = new LinearLayout(this);
        mainArea.setOrientation(LinearLayout.VERTICAL);
        mainArea.setGravity(Gravity.CENTER_HORIZONTAL);
        mainArea.setPadding(dp(22), dp(18), dp(22), dp(12));
        scrollView.addView(mainArea, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = makeCenteredText("passwall控制", 24, Color.rgb(27, 43, 68));
        mainArea.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = makeCenteredText("控制 PassWall 总开关、访问控制和 TCP 节点", 14, Color.rgb(104, 116, 135));
        subtitle.setPadding(0, dp(8), 0, dp(16));
        mainArea.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        settingsPanel.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsLp.setMargins(0, 0, 0, dp(14));
        mainArea.addView(settingsPanel, settingsLp);

        addressInput = makeInput("软路由地址，例如 10.1.1.1 或 https://10.1.1.1", false);
        addressInput.setText(prefs.getString(KEY_ADDRESS, DEFAULT_ADDRESS));
        usernameInput = makeInput("LuCI 用户名，例如 root", false);
        usernameInput.setText(prefs.getString(KEY_USERNAME, DEFAULT_USERNAME));
        passwordInput = makeInput("LuCI 密码，仅保存在本机", true);
        passwordInput.setText(prefs.getString(KEY_PASSWORD, ""));

        settingsPanel.addView(makeLabel("软路由地址"));
        settingsPanel.addView(addressInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        settingsPanel.addView(makeLabel("用户名"));
        settingsPanel.addView(usernameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        settingsPanel.addView(makeLabel("密码"));
        settingsPanel.addView(passwordInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button saveButton = makeSmallButton("保存设置");
        Button refreshButton = makeSmallButton("刷新状态");
        row1.addView(saveButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        row1.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingsPanel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        openPageButton = makeSmallButton("打开网页");
        Button clearCookieButton = makeSmallButton("清除登录");
        row2.addView(openPageButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        row2.addView(clearCookieButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingsPanel.addView(row2);

        appSwitchButton = makeBigButton();
        LinearLayout.LayoutParams bigLp1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        bigLp1.setMargins(0, dp(30), 0, dp(14));
        mainArea.addView(appSwitchButton, bigLp1);
        updateAppButtonUnknown("PassWall 总开关\n检测中");

        aclSwitchButton = makeBigButton();
        LinearLayout.LayoutParams bigLp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
        bigLp2.setMargins(0, 0, 0, dp(18));
        mainArea.addView(aclSwitchButton, bigLp2);
        updateAclButtonUnknown("访问控制开关\n检测中");

        nodeHintText = makeCenteredText("TCP 节点", 15, Color.rgb(40, 55, 80));
        nodeHintText.setPadding(0, 0, 0, dp(8));
        mainArea.addView(nodeHintText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tcpNodeSpinner = new Spinner(this);
        nodeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nodeLabels);
        nodeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tcpNodeSpinner.setAdapter(nodeAdapter);
        tcpNodeSpinner.setEnabled(false);
        mainArea.addView(tcpNodeSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        statusText = makeCenteredText("正在初始化", 16, Color.rgb(40, 55, 80));
        statusText.setPadding(0, dp(18), 0, dp(6));
        mainArea.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detailText = makeCenteredText("首次使用请先在上方设置软路由地址、用户名和密码。", 13, Color.rgb(110, 120, 135));
        detailText.setPadding(0, 0, 0, dp(12));
        mainArea.addView(detailText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        settingsButton = makeSmallButton("设置");
        LinearLayout.LayoutParams settingsBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        settingsBtnLp.setMargins(0, dp(8), 0, dp(8));
        mainArea.addView(settingsButton, settingsBtnLp);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        setContentView(root);
        configureWebView();

        appSwitchButton.setOnClickListener(v -> startToggleApp());
        aclSwitchButton.setOnClickListener(v -> startToggleAcl());
        settingsButton.setOnClickListener(v -> toggleSettingsPanel());
        saveButton.setOnClickListener(v -> {
            saveSettings();
            hideKeyboardSafe();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            settingsPanel.setVisibility(View.GONE);
            settingsButton.setText("设置");
            refreshAll(true);
        });
        refreshButton.setOnClickListener(v -> refreshAll(true));
        openPageButton.setOnClickListener(v -> openDebugWebPage());
        clearCookieButton.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            appEnabled = null;
            aclEnabled = null;
            updateAppButtonUnknown("PassWall 总开关\n未登录");
            updateAclButtonUnknown("访问控制开关\n未登录");
            tcpNodeSpinner.setEnabled(false);
            statusText.setText("已清除登录状态");
            detailText.setText("下次刷新状态或操作时，会在会话过期后重新登录。");
        });

        tcpNodeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressNodeEvent || position < 0 || position >= nodeValues.size() || nodeValues.size() == 0) return;
                String value = nodeValues.get(position);
                if (value == null || value.length() == 0) return;
                pendingNodeValue = value;
                beginWork(MODE_SET_NODE, "正在切换 TCP 节点...", "将自动进入 PassWall 基本设置页，选择 TCP 节点并保存应用。");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean hasPassword = prefs.getString(KEY_PASSWORD, "").length() > 0;
        settingsPanel.setVisibility(hasPassword ? View.GONE : View.VISIBLE);
        settingsButton.setText(hasPassword ? "设置" : "关闭设置");
        if (hasPassword) handler.postDelayed(() -> refreshAll(false), 400);
        else {
            statusText.setText("等待设置");
            updateAppButtonUnknown("PassWall 总开关\n待设置");
            updateAclButtonUnknown("访问控制开关\n待设置");
        }
    }

    private TextView makeCenteredText(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView makeLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(Color.rgb(80, 92, 110));
        label.setPadding(0, dp(8), 0, dp(2));
        return label;
    }

    private EditText makeInput(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setHint(hint);
        input.setSelectAllOnFocus(false);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && scrollView != null) {
                handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150);
                handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 500);
            }
        });
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        return input;
    }

    private Button makeSmallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private Button makeBigButton() {
        Button b = new Button(this);
        b.setTextSize(21);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
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
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUserAgentString(settings.getUserAgentString() + " PassWallControl/2.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return false;
                Toast.makeText(MainActivity.this, "不支持的链接：" + uri, Toast.LENGTH_SHORT).show();
                return true;
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                injectCompactMode();
                if (workMode != MODE_IDLE) handler.postDelayed(() -> runPageScript(), 1500);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) failWork("页面打开失败，请检查软路由地址或手机网络。");
            }
        });
    }

    private void toggleSettingsPanel() {
        boolean show = settingsPanel.getVisibility() != View.VISIBLE;
        settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        settingsButton.setText(show ? "关闭设置" : "设置");
        if (show && scrollView != null) handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150);
        else hideKeyboardSafe();
    }

    private void refreshAll(boolean userTriggered) {
        saveSettings();
        hideKeyboardSafe();
        if (!hasRequiredSettings()) return;
        if (userTriggered) Toast.makeText(this, "正在刷新状态", Toast.LENGTH_SHORT).show();
        beginWork(MODE_CHECK_APP, "正在检测 PassWall 总开关...", "会依次读取总开关、访问控制开关和 TCP 节点。", true);
    }

    private void startToggleApp() {
        saveSettings();
        hideKeyboardSafe();
        if (!hasRequiredSettings()) return;
        beginWork(MODE_TOGGLE_APP, "正在切换 PassWall 总开关...", "将自动进入基本设置页，点击主开关并保存应用。", false);
    }

    private void startToggleAcl() {
        saveSettings();
        hideKeyboardSafe();
        if (!hasRequiredSettings()) return;
        beginWork(MODE_TOGGLE_ACL, "正在切换访问控制开关...", "将自动进入访问控制页，点击主开关并保存应用。", false);
    }

    private void beginWork(int mode, String status, String detail, boolean resetSequence) {
        workMode = mode;
        pageScriptRetryCount = 0;
        debugPageVisible = false;
        if (openPageButton != null) openPageButton.setText("打开网页");
        setBusy(true);
        statusText.setText(status);
        detailText.setText(detail);
        webView.getLayoutParams().height = dp(1);
        webView.requestLayout();
        webView.loadUrl(addCacheBuster(getTargetUrlForMode(mode)));
    }

    private void beginWork(int mode, String status, String detail) {
        beginWork(mode, status, detail, false);
    }

    private void openDebugWebPage() {
        saveSettings();
        hideKeyboardSafe();
        if (debugPageVisible) { closeDebugWebPage(); return; }
        workMode = MODE_IDLE;
        pageScriptRetryCount = 0;
        debugPageVisible = true;
        setBusy(false);
        openPageButton.setText("关闭网页");
        statusText.setText("已打开网页模式");
        detailText.setText("默认打开 PassWall 基本设置页。再次点击“关闭网页”可收起页面。");
        webView.getLayoutParams().height = dp(360);
        webView.requestLayout();
        webView.loadUrl(addCacheBuster(normalizeToPath(addressInput.getText().toString().trim(), PASSWALL_PATH)));
    }

    private void closeDebugWebPage() {
        debugPageVisible = false;
        if (openPageButton != null) openPageButton.setText("打开网页");
        if (webView != null) {
            webView.stopLoading();
            webView.getLayoutParams().height = dp(1);
            webView.requestLayout();
        }
        statusText.setText("已关闭网页模式");
        detailText.setText("页面已收起。需要排错时可在设置中再次点击“打开网页”。");
    }

    private void setBusy(boolean busy) {
        appSwitchButton.setEnabled(!busy);
        aclSwitchButton.setEnabled(!busy);
        settingsButton.setEnabled(!busy);
        tcpNodeSpinner.setEnabled(!busy && nodeValues.size() > 0);
    }

    private boolean hasRequiredSettings() {
        if (addressInput.getText().toString().trim().length() == 0) addressInput.setText(DEFAULT_ADDRESS);
        if (usernameInput.getText().toString().trim().length() == 0 || passwordInput.getText().toString().length() == 0) {
            settingsPanel.setVisibility(View.VISIBLE);
            settingsButton.setText("关闭设置");
            Toast.makeText(this, "请先填写 LuCI 用户名和密码", Toast.LENGTH_SHORT).show();
            statusText.setText("等待设置");
            detailText.setText("填写并保存后，App 会自动读取两个开关和 TCP 节点。");
            updateAppButtonUnknown("PassWall 总开关\n待设置");
            updateAclButtonUnknown("访问控制开关\n待设置");
            if (scrollView != null) handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150);
            return false;
        }
        return true;
    }

    private void saveSettings() {
        prefs.edit()
                .putString(KEY_ADDRESS, addressInput.getText().toString().trim())
                .putString(KEY_USERNAME, usernameInput.getText().toString().trim())
                .putString(KEY_PASSWORD, passwordInput.getText().toString())
                .apply();
    }

    private String getTargetUrlForMode(int mode) {
        if (mode == MODE_CHECK_ACL || mode == MODE_TOGGLE_ACL) return normalizeToPath(addressInput.getText().toString().trim(), ACL_PATH);
        return normalizeToPath(addressInput.getText().toString().trim(), PASSWALL_PATH);
    }

    private void runPageScript() {
        if (workMode == MODE_IDLE) return;
        String task;
        if (workMode == MODE_CHECK_APP) task = "CHECK_APP";
        else if (workMode == MODE_TOGGLE_APP) task = "TOGGLE_APP";
        else if (workMode == MODE_CHECK_ACL) task = "CHECK_ACL";
        else if (workMode == MODE_TOGGLE_ACL) task = "TOGGLE_ACL";
        else if (workMode == MODE_LOAD_NODES) task = "LOAD_NODES";
        else task = "SET_NODE";

        String js = """
                (function(){
                  try{
                    var USER=__USER__, PASS=__PASS__, TASK=__TASK__, NODE_VALUE=__NODE_VALUE__;
                    function fire(el,type){try{el.dispatchEvent(new Event(type,{bubbles:true,cancelable:true}));}catch(e){}}
                    function text(el){return ((el&&(el.innerText||el.textContent||el.value)||'')+'').trim();}
                    function findLogin(){
                      var passEl=document.querySelector('input[name="luci_password"],input#luci_password,input[name="password"],input[type="password"]');
                      if(!passEl)return null;
                      var root=passEl.form||document;
                      var userEl=root.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]')||document.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]');
                      if(!userEl)return null;
                      return {user:userEl,pass:passEl,form:(passEl.form||userEl.form)};
                    }
                    function submitLogin(l){
                      l.user.focus();l.user.value=USER;fire(l.user,'input');fire(l.user,'change');
                      l.pass.focus();l.pass.value=PASS;fire(l.pass,'input');fire(l.pass,'change');
                      var f=l.form,b=null;
                      if(f)b=f.querySelector('button[type="submit"],input[type="submit"],button,input.cbi-button');
                      if(!b)b=Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"]')).find(function(e){return /登录|登陆|Login|Sign in/i.test(text(e));});
                      if(b){b.click();return true;} if(f){f.submit();return true;} return false;
                    }
                    function labelTitle(cb){
                      var row=cb.closest('.cbi-value,.cbi-section-node,tr,div');
                      if(row){var t=row.querySelector('.cbi-value-title,label'); if(t)return text(t);}
                      if(cb.id){var l=document.querySelector('label[for="'+cb.id+'"]'); if(l)return text(l);}
                      return '';
                    }
                    function findMainSwitch(kind){
                      var c=Array.from(document.querySelectorAll('input[type="checkbox"]'));
                      if(kind==='ACL'){
                        var a=c.find(function(cb){var s=((cb.name||'')+' '+(cb.id||'')+' '+(cb.getAttribute('data-widget-id')||''));return s.indexOf('acl_enable')>=0;});
                        if(a)return a;
                        return c.find(function(cb){return labelTitle(cb)==='主开关';});
                      }
                      var exact=c.find(function(cb){return labelTitle(cb)==='主开关';});
                      if(exact)return exact;
                      return c.find(function(cb){
                        var s=((cb.name||'')+' '+(cb.id||'')+' '+(cb.getAttribute('data-widget-id')||'')).toLowerCase();
                        return s.indexOf('enabled')>=0 && s.indexOf('acl')<0 && s.indexOf('socks')<0 && s.indexOf('dns')<0 && s.indexOf('haproxy')<0;
                      });
                    }
                    function findApply(){
                      var a=document.querySelector('.cbi-button-apply,input[name="cbi.apply"],button[name="cbi.apply"],input[value*="保存并应用"],button[value*="保存并应用"]');
                      if(a)return a;
                      return Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"],a')).find(function(e){var t=text(e);return t.indexOf('保存并应用')>=0||/Save\\s*&\\s*Apply/i.test(t)||/Save.*Apply/i.test(t);});
                    }
                    function findTcpSelect(){
                      var sels=Array.from(document.querySelectorAll('select'));
                      var s=sels.find(function(x){var k=((x.name||'')+' '+(x.id||'')+' '+(x.getAttribute('data-widget-id')||'')).toLowerCase();return k.indexOf('tcp_node')>=0||k.indexOf('tcp-node')>=0;});
                      if(s)return s;
                      var labels=Array.from(document.querySelectorAll('label,.cbi-value-title,td,div')).filter(function(e){return text(e)==='TCP 节点'||text(e)==='TCP节点';});
                      for(var i=0;i<labels.length;i++){var row=labels[i].closest('.cbi-value,tr,div'); if(row){s=row.querySelector('select'); if(s)return s;}}
                      return null;
                    }
                    var login=findLogin();
                    if(login){if(submitLogin(login))return 'LOGIN_SUBMITTED'; return 'LOGIN_FORM_NO_SUBMIT';}
                    var isAcl=location.href.indexOf('/passwall/acl')>=0;
                    var isMain=location.href.indexOf('/services/passwall')>=0 && !isAcl;
                    if((TASK==='CHECK_ACL'||TASK==='TOGGLE_ACL')&&!isAcl)return 'NOT_TARGET:'+location.href;
                    if((TASK==='CHECK_APP'||TASK==='TOGGLE_APP'||TASK==='LOAD_NODES'||TASK==='SET_NODE')&&!isMain)return 'NOT_TARGET:'+location.href;
                    if(TASK==='CHECK_APP'||TASK==='TOGGLE_APP'||TASK==='CHECK_ACL'||TASK==='TOGGLE_ACL'){
                      var kind=(TASK.indexOf('ACL')>=0)?'ACL':'APP';
                      var sw=findMainSwitch(kind);
                      if(!sw)return 'SWITCH_NOT_FOUND:'+kind;
                      if(TASK.indexOf('CHECK')===0)return 'STATE:'+kind+':' +(sw.checked?'ON':'OFF');
                      var before=sw.checked;
                      try{sw.scrollIntoView({block:'center'});}catch(e){}
                      var lab=sw.id?document.querySelector('label[for="'+sw.id+'"]'):null;
                      if(lab)lab.click();else sw.click();
                      if(before===sw.checked)sw.checked=!sw.checked;
                      fire(sw,'input');fire(sw,'change');
                      var after=sw.checked, ap=findApply();
                      if(!ap)return 'APPLY_NOT_FOUND:'+kind+':' +(before?'ON':'OFF')+'->'+(after?'ON':'OFF');
                      ap.click();
                      return 'TOGGLED:'+kind+':' +(before?'ON':'OFF')+'->'+(after?'ON':'OFF');
                    }
                    var sel=findTcpSelect();
                    if(!sel)return 'NODES_NOT_FOUND';
                    if(TASK==='LOAD_NODES'){
                      var opts=Array.from(sel.options).map(function(o){return {value:o.value,text:text(o),selected:o.selected};});
                      return 'NODES:'+JSON.stringify({value:sel.value,options:opts});
                    }
                    sel.value=NODE_VALUE;fire(sel,'input');fire(sel,'change');
                    var ap2=findApply();
                    if(!ap2)return 'APPLY_NOT_FOUND:NODE';
                    ap2.click();
                    return 'NODE_SET:'+NODE_VALUE;
                  }catch(e){return 'ERROR:'+e.message;}
                })()
                """;
        js = js.replace("__USER__", JSONObject.quote(usernameInput.getText().toString().trim()))
                .replace("__PASS__", JSONObject.quote(passwordInput.getText().toString()))
                .replace("__TASK__", JSONObject.quote(task))
                .replace("__NODE_VALUE__", JSONObject.quote(pendingNodeValue == null ? "" : pendingNodeValue));
        webView.evaluateJavascript(js, result -> handlePageScriptResult(result == null ? "" : result));
    }

    private void handlePageScriptResult(String rawResult) {
        if (workMode == MODE_IDLE) return;
        String result = decodeJsString(rawResult);
        if (result.startsWith("LOGIN_SUBMITTED")) {
            statusText.setText("登录会话已超时，正在自动登录...");
            detailText.setText("登录完成后会继续执行当前操作。");
            return;
        }
        if (result.startsWith("NOT_TARGET:") || result.startsWith("SWITCH_NOT_FOUND:") || result.startsWith("NODES_NOT_FOUND")) {
            retryLoadTargetPage("当前页面还没有进入目标设置项，正在重新进入并重试...");
            return;
        }
        if (result.startsWith("STATE:APP:")) {
            boolean enabled = result.endsWith("ON");
            appEnabled = enabled;
            updateAppButtonState(enabled);
            beginWork(MODE_CHECK_ACL, "正在检测访问控制开关...", "正在继续读取访问控制状态。", false);
            return;
        }
        if (result.startsWith("STATE:ACL:")) {
            boolean enabled = result.endsWith("ON");
            aclEnabled = enabled;
            updateAclButtonState(enabled);
            beginWork(MODE_LOAD_NODES, "正在读取 TCP 节点...", "正在加载可选节点列表。", false);
            return;
        }
        if (result.startsWith("TOGGLED:APP:")) {
            boolean enabled = result.endsWith("->ON");
            appEnabled = enabled;
            updateAppButtonState(enabled);
            finishWork("PassWall 总开关已切换");
            detailText.setText((enabled ? "PassWall 已切换为：开启" : "PassWall 已切换为：关闭") + "，已点击保存并应用。建议等待几秒生效。");
            handler.postDelayed(() -> refreshAll(false), 4500);
            return;
        }
        if (result.startsWith("TOGGLED:ACL:")) {
            boolean enabled = result.endsWith("->ON");
            aclEnabled = enabled;
            updateAclButtonState(enabled);
            finishWork("访问控制开关已切换");
            detailText.setText((enabled ? "访问控制已切换为：开启" : "访问控制已切换为：关闭") + "，已点击保存并应用。建议等待几秒生效。");
            return;
        }
        if (result.startsWith("NODES:")) {
            handleNodesResult(result.substring("NODES:".length()));
            finishWork("状态已刷新");
            detailText.setText("已读取总开关、访问控制开关和 TCP 节点。下拉选择节点后会自动保存应用。");
            return;
        }
        if (result.startsWith("NODE_SET:")) {
            finishWork("TCP 节点已切换");
            detailText.setText("已选择新的 TCP 节点，并点击保存并应用。建议等待几秒生效。");
            handler.postDelayed(() -> beginWork(MODE_LOAD_NODES, "正在刷新 TCP 节点...", "正在确认当前节点。", false), 3500);
            return;
        }
        if (result.startsWith("APPLY_NOT_FOUND:")) { failWork("已找到设置项，但没有找到“保存并应用”按钮。请点“打开网页”确认页面结构。"); return; }
        if (result.startsWith("LOGIN_FORM_NO_SUBMIT")) { failWork("已填写登录信息，但没有找到登录按钮。请确认 LuCI 登录页是否正常。"); return; }
        if (result.startsWith("ERROR:")) { failWork("自动操作失败：" + result); return; }
        retryLoadTargetPage("当前页面状态不稳定，正在重新进入并重试...");
    }

    private void handleNodesResult(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.getJSONArray("options");
            String selected = obj.optString("value", "");
            nodeValues.clear();
            nodeLabels.clear();
            int selectedIndex = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                String value = item.optString("value", "");
                String label = item.optString("text", value);
                if (label.length() == 0) label = value.length() == 0 ? "默认 / 空" : value;
                nodeValues.add(value);
                nodeLabels.add(label);
                if (value.equals(selected) || item.optBoolean("selected", false)) selectedIndex = i;
            }
            suppressNodeEvent = true;
            nodeAdapter.notifyDataSetChanged();
            tcpNodeSpinner.setSelection(selectedIndex, false);
            tcpNodeSpinner.setEnabled(nodeValues.size() > 0);
            suppressNodeEvent = false;
        } catch (Exception e) {
            tcpNodeSpinner.setEnabled(false);
            detailText.setText("TCP 节点列表解析失败：" + e.getMessage());
        }
    }

    private void retryLoadTargetPage(String reason) {
        if (workMode == MODE_IDLE) return;
        if (pageScriptRetryCount < MAX_PAGE_SCRIPT_RETRY) {
            pageScriptRetryCount++;
            statusText.setText("正在等待目标页面加载...");
            detailText.setText(reason + " 第 " + pageScriptRetryCount + "/" + MAX_PAGE_SCRIPT_RETRY + " 次。");
            handler.postDelayed(() -> {
                if (workMode != MODE_IDLE) webView.loadUrl(addCacheBuster(getTargetUrlForMode(workMode)));
            }, 1200);
            return;
        }
        failWork("多次重试后仍未找到目标控件。请点“设置”里的“打开网页”，确认 PassWall 页面结构是否正常。");
    }

    private void finishWork(String message) {
        workMode = MODE_IDLE;
        pageScriptRetryCount = 0;
        pendingNodeValue = null;
        setBusy(false);
        statusText.setText(message);
        CookieManager.getInstance().flush();
    }

    private void failWork(String message) {
        workMode = MODE_IDLE;
        pageScriptRetryCount = 0;
        pendingNodeValue = null;
        setBusy(false);
        statusText.setText("执行失败");
        detailText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String decodeJsString(String rawResult) {
        String result = rawResult == null ? "" : rawResult;
        if (result.startsWith("\"") && result.endsWith("\"") && result.length() >= 2) {
            result = result.substring(1, result.length() - 1)
                    .replace("\\\"", "\"").replace("\\n", "\n")
                    .replace("\\u003C", "<").replace("\\u003E", ">")
                    .replace("\\u0026", "&").replace("\\\\", "\\");
        }
        return result;
    }

    private void updateAppButtonState(boolean enabled) {
        if (enabled) { setButtonStyle(appSwitchButton, Color.rgb(23, 166, 92)); appSwitchButton.setText("PassWall 总开关：开启\n点击关闭"); }
        else { setButtonStyle(appSwitchButton, Color.rgb(218, 62, 62)); appSwitchButton.setText("PassWall 总开关：关闭\n点击开启"); }
    }

    private void updateAclButtonState(boolean enabled) {
        if (enabled) { setButtonStyle(aclSwitchButton, Color.rgb(23, 166, 92)); aclSwitchButton.setText("访问控制开关：开启\n点击关闭"); }
        else { setButtonStyle(aclSwitchButton, Color.rgb(218, 62, 62)); aclSwitchButton.setText("访问控制开关：关闭\n点击开启"); }
    }

    private void updateAppButtonUnknown(String text) { setButtonStyle(appSwitchButton, Color.rgb(107, 119, 140)); appSwitchButton.setText(text); }
    private void updateAclButtonUnknown(String text) { setButtonStyle(aclSwitchButton, Color.rgb(107, 119, 140)); aclSwitchButton.setText(text); }

    private void setButtonStyle(Button button, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(18));
        button.setBackground(bg);
        button.setTextColor(Color.WHITE);
    }

    private String normalizeToPath(String raw, String path) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0) value = DEFAULT_ADDRESS;
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        String authority = uri.getEncodedAuthority();
        if (authority == null || authority.length() == 0) return "https://" + DEFAULT_ADDRESS + path;
        return scheme + "://" + authority + path;
    }

    private String addCacheBuster(String url) { return url + (url.contains("?") ? "&" : "?") + "_pwts=" + System.currentTimeMillis(); }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void hideKeyboardSafe() {
        try {
            View current = getCurrentFocus();
            if (current != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(current.getWindowToken(), 0);
                current.clearFocus();
            }
        } catch (Exception ignored) {}
    }

    private void injectCompactMode() {
        String js = "javascript:(function(){try{var css='html,body{max-width:100%!important;overflow-x:auto!important;}.main-left,.sidebar,aside,#mainmenu,#modemenu,.breadcrumb{display:none!important;}header,.navbar,.brand,.pull-left{display:none!important;}.main,.main-right,.container,.container-fluid,#maincontent{margin-left:0!important;left:0!important;width:100%!important;max-width:100%!important;padding-left:8px!important;padding-right:8px!important;}.cbi-map,.cbi-section,.cbi-section-node{max-width:100%!important;width:100%!important;}table,.table{width:100%!important;display:block!important;overflow-x:auto!important;}input,select,textarea,button,.btn,.cbi-button{min-height:40px!important;font-size:15px!important;}.cbi-value-title{min-width:92px!important;}';var style=document.getElementById('pw-compact-style');if(!style){style=document.createElement('style');style.id='pw-compact-style';document.head.appendChild(style);}style.innerHTML=css;document.title='PassWall 控制';}catch(e){}})()";
        webView.evaluateJavascript(js, null);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }

    @Override public void onBackPressed() {
        if (debugPageVisible) closeDebugWebPage();
        else if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE && prefs.getString(KEY_PASSWORD, "").length() > 0) toggleSettingsPanel();
        else if (webView != null && webView.getLayoutParams().height > dp(10) && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
