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

public class MainActivity extends Activity {
    private static final String PREFS = "passwall_control_prefs";
    private static final String KEY_ADDRESS = "router_address";
    private static final String KEY_USERNAME = "router_username";
    private static final String KEY_PASSWORD = "router_password";
    private static final String DEFAULT_ADDRESS = "https://10.1.1.1";
    private static final String DEFAULT_USERNAME = "root";
    private static final String MAIN_PATH = "/cgi-bin/luci/admin/services/passwall";
    private static final String ACL_PATH = "/cgi-bin/luci/admin/services/passwall/acl";

    private static final int MODE_IDLE = 0;
    private static final int MODE_REFRESH_MAIN = 1;
    private static final int MODE_REFRESH_ACL = 2;
    private static final int MODE_TOGGLE_MAIN = 3;
    private static final int MODE_TOGGLE_ACL = 4;
    private static final int MODE_APPLY_TCP = 5;
    private static final int MAX_PAGE_SCRIPT_RETRY = 8;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ScrollView scrollView;
    private LinearLayout settingsPanel;
    private WebView webView;
    private EditText addressInput, usernameInput, passwordInput;
    private TextView statusText, detailText, tcpNodeTitle;
    private ProgressBar progressBar;
    private Button passwallButton, aclButton, settingsButton, openPageButton;
    private Spinner tcpSpinner;
    private ArrayAdapter<NodeItem> tcpAdapter;
    private final ArrayList<NodeItem> tcpNodes = new ArrayList<>();

    private int workMode = MODE_IDLE;
    private int pageScriptRetryCount = 0;
    private boolean refreshAllInProgress = false;
    private boolean debugPageVisible = false;
    private boolean tcpSpinnerReady = false;
    private Boolean passwallEnabled = null;
    private Boolean aclEnabled = null;
    private String currentTcpKey = "";
    private String currentTcpLabel = "";
    private String targetTcpKey = "";
    private String targetTcpLabel = "";

    private static class NodeItem {
        final String key;
        final String label;
        NodeItem(String key, String label) { this.key = key == null ? "" : key; this.label = label == null ? "" : label; }
        @Override public String toString() { return label.length() == 0 ? "关闭" : label; }
    }

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

        TextView title = new TextView(this);
        title.setText("passwall控制");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(27, 43, 68));
        title.setGravity(Gravity.CENTER);
        mainArea.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("总开关、访问控制、TCP节点切换");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(104, 116, 135));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        mainArea.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        settingsPanel.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsLp.setMargins(0, 0, 0, dp(14));
        mainArea.addView(settingsPanel, settingsLp);

        addressInput = makeInput("软路由地址，例如 https://10.1.1.1 或 https://10.1.1.1:10086", false);
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

        View spacer = new View(this);
        mainArea.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        passwallButton = new Button(this);
        passwallButton.setTextSize(20);
        passwallButton.setAllCaps(false);
        passwallButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mainBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
        mainBtnLp.setMargins(0, 0, 0, dp(12));
        mainArea.addView(passwallButton, mainBtnLp);
        updateSwitchButton(passwallButton, passwallEnabled, "PassWall 总开关");

        aclButton = new Button(this);
        aclButton.setTextSize(20);
        aclButton.setAllCaps(false);
        aclButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams aclBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
        aclBtnLp.setMargins(0, 0, 0, dp(18));
        mainArea.addView(aclButton, aclBtnLp);
        updateSwitchButton(aclButton, aclEnabled, "访问控制开关");

        tcpNodeTitle = new TextView(this);
        tcpNodeTitle.setText("TCP 节点：正在加载...");
        tcpNodeTitle.setTextSize(15);
        tcpNodeTitle.setTextColor(Color.rgb(40, 55, 80));
        tcpNodeTitle.setGravity(Gravity.LEFT);
        tcpNodeTitle.setPadding(0, 0, 0, dp(6));
        mainArea.addView(tcpNodeTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tcpSpinner = new Spinner(this);
        tcpNodes.add(new NodeItem("", "节点列表未加载"));
        tcpAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tcpNodes);
        tcpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tcpSpinner.setAdapter(tcpAdapter);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        spinnerLp.setMargins(0, 0, 0, dp(14));
        mainArea.addView(tcpSpinner, spinnerLp);

        statusText = new TextView(this);
        statusText.setText("正在初始化");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.rgb(40, 55, 80));
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(4), 0, dp(6));
        mainArea.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detailText = new TextView(this);
        detailText.setText("首次使用请先在上方设置软路由地址、用户名和密码。");
        detailText.setTextSize(13);
        detailText.setTextColor(Color.rgb(110, 120, 135));
        detailText.setGravity(Gravity.CENTER);
        detailText.setPadding(0, 0, 0, dp(12));
        mainArea.addView(detailText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        settingsButton = new Button(this);
        settingsButton.setText("设置");
        settingsButton.setAllCaps(false);
        settingsButton.setTextSize(15);
        LinearLayout.LayoutParams settingsBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        settingsBtnLp.setMargins(0, dp(6), 0, dp(8));
        mainArea.addView(settingsButton, settingsBtnLp);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        setContentView(root);
        configureWebView();

        passwallButton.setOnClickListener(v -> startToggleMain());
        aclButton.setOnClickListener(v -> startToggleAcl());
        settingsButton.setOnClickListener(v -> toggleSettingsPanel());
        saveButton.setOnClickListener(v -> {
            saveSettings();
            hideKeyboardSafe();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            settingsPanel.setVisibility(View.GONE);
            settingsButton.setText("设置");
            refreshAll();
        });
        refreshButton.setOnClickListener(v -> { saveSettings(); hideKeyboardSafe(); refreshAll(); });
        openPageButton.setOnClickListener(v -> openDebugWebPage());
        clearCookieButton.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            passwallEnabled = null;
            aclEnabled = null;
            currentTcpKey = "";
            currentTcpLabel = "";
            updateSwitchButton(passwallButton, passwallEnabled, "PassWall 总开关");
            updateSwitchButton(aclButton, aclEnabled, "访问控制开关");
            tcpNodeTitle.setText("TCP 节点：未登录");
            statusText.setText("已清除登录状态");
            detailText.setText("下次刷新状态或点击开关时，会在会话过期后重新登录。");
        });

        tcpSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!tcpSpinnerReady || position < 0 || position >= tcpNodes.size()) return;
                NodeItem item = tcpNodes.get(position);
                if (item.key.equals(currentTcpKey)) return;
                startApplyTcpNode(item.key, item.label);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean hasPassword = prefs.getString(KEY_PASSWORD, "").length() > 0;
        settingsPanel.setVisibility(hasPassword ? View.GONE : View.VISIBLE);
        settingsButton.setText(hasPassword ? "设置" : "关闭设置");
        if (hasPassword) handler.postDelayed(this::refreshAll, 400);
        else {
            statusText.setText("等待设置");
            updateSwitchButton(passwallButton, null, "PassWall 总开关");
            updateSwitchButton(aclButton, null, "访问控制开关");
        }
    }

    private TextView makeLabel(String text) { TextView label = new TextView(this); label.setText(text); label.setTextSize(13); label.setTextColor(Color.rgb(80, 92, 110)); label.setPadding(0, dp(8), 0, dp(2)); return label; }
    private EditText makeInput(String hint, boolean password) { EditText input = new EditText(this); input.setSingleLine(true); input.setTextSize(14); input.setHint(hint); input.setSelectAllOnFocus(false); input.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus && scrollView != null) { handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150); handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 500); } }); input.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); return input; }
    private Button makeSmallButton(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(13); b.setPadding(dp(4), 0, dp(4), 0); return b; }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setDatabaseEnabled(true); settings.setLoadWithOverviewMode(true); settings.setUseWideViewPort(true); settings.setBuiltInZoomControls(false); settings.setDisplayZoomControls(false); settings.setSupportZoom(false); settings.setJavaScriptCanOpenWindowsAutomatically(true); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); settings.setCacheMode(WebSettings.LOAD_NO_CACHE); settings.setUserAgentString(settings.getUserAgentString() + " PassWallControl/2.0");
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() { @Override public void onProgressChanged(WebView view, int newProgress) { progressBar.setProgress(newProgress); progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE); } });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { Uri uri = request.getUrl(); String scheme = uri.getScheme(); if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return false; Toast.makeText(MainActivity.this, "不支持的链接：" + uri, Toast.LENGTH_SHORT).show(); return true; }
            @Override public void onPageFinished(WebView view, String url) { super.onPageFinished(view, url); CookieManager.getInstance().flush(); injectCompactMode(); if (workMode != MODE_IDLE) handler.postDelayed(MainActivity.this::runPageScript, 1500); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { super.onReceivedError(view, request, error); if (request.isForMainFrame()) failWork("页面打开失败，请检查软路由地址或手机网络。"); }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) { handler.proceed(); }
        });
    }

    private void toggleSettingsPanel() { boolean show = settingsPanel.getVisibility() != View.VISIBLE; settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE); settingsButton.setText(show ? "关闭设置" : "设置"); if (show && scrollView != null) handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150); else hideKeyboardSafe(); }
    private void refreshAll() { saveSettings(); hideKeyboardSafe(); if (!hasRequiredSettings()) return; refreshAllInProgress = true; beginWork(MODE_REFRESH_MAIN, MAIN_PATH, "正在读取 PassWall 状态...", "正在读取总开关、TCP节点和访问控制开关。", true); }
    private void startToggleMain() { saveSettings(); hideKeyboardSafe(); if (!hasRequiredSettings()) return; beginWork(MODE_TOGGLE_MAIN, MAIN_PATH, "正在切换 PassWall 总开关...", "将自动进入基本设置页，点击总开关并保存应用。", true); }
    private void startToggleAcl() { saveSettings(); hideKeyboardSafe(); if (!hasRequiredSettings()) return; beginWork(MODE_TOGGLE_ACL, ACL_PATH, "正在切换访问控制开关...", "将自动进入访问控制页，点击主开关并保存应用。", true); }
    private void startApplyTcpNode(String key, String label) { saveSettings(); hideKeyboardSafe(); if (!hasRequiredSettings()) return; targetTcpKey = key == null ? "" : key; targetTcpLabel = label == null ? "" : label; beginWork(MODE_APPLY_TCP, MAIN_PATH, "正在切换 TCP 节点...", "目标节点：" + targetTcpLabel + "。将自动保存并应用。", true); }

    private void beginWork(int mode, String path, String status, String detail, boolean hideWeb) { if (!isNetworkAvailable()) Toast.makeText(this, "当前手机网络不可用", Toast.LENGTH_SHORT).show(); workMode = mode; pageScriptRetryCount = 0; debugPageVisible = false; if (openPageButton != null) openPageButton.setText("打开网页"); setControlsEnabled(false); statusText.setText(status); detailText.setText(detail); if (hideWeb) { webView.getLayoutParams().height = dp(1); webView.requestLayout(); } webView.loadUrl(addCacheBuster(normalizeToUrl(addressInput.getText().toString().trim(), path))); }
    private void openDebugWebPage() { saveSettings(); hideKeyboardSafe(); if (debugPageVisible) { closeDebugWebPage(); return; } workMode = MODE_IDLE; pageScriptRetryCount = 0; refreshAllInProgress = false; debugPageVisible = true; setControlsEnabled(true); openPageButton.setText("关闭网页"); statusText.setText("已打开网页模式"); detailText.setText("显示 PassWall 基本设置页，用于排错。再次点击“关闭网页”可收起页面。"); webView.getLayoutParams().height = dp(360); webView.requestLayout(); webView.loadUrl(addCacheBuster(normalizeToUrl(addressInput.getText().toString().trim(), MAIN_PATH))); }
    private void closeDebugWebPage() { debugPageVisible = false; if (openPageButton != null) openPageButton.setText("打开网页"); if (webView != null) { webView.stopLoading(); webView.getLayoutParams().height = dp(1); webView.requestLayout(); } statusText.setText("已关闭网页模式"); detailText.setText("页面已收起。需要排错时可在设置中再次点击“打开网页”。"); }

    private boolean hasRequiredSettings() { if (addressInput.getText().toString().trim().length() == 0) addressInput.setText(DEFAULT_ADDRESS); if (usernameInput.getText().toString().trim().length() == 0 || passwordInput.getText().toString().length() == 0) { settingsPanel.setVisibility(View.VISIBLE); settingsButton.setText("关闭设置"); Toast.makeText(this, "请先填写 LuCI 用户名和密码", Toast.LENGTH_SHORT).show(); statusText.setText("等待设置"); detailText.setText("填写并保存后，App 会先验证登录状态，再执行控制。用户密码只保存在本机。 "); if (scrollView != null) handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150); return false; } return true; }
    private void saveSettings() { prefs.edit().putString(KEY_ADDRESS, addressInput.getText().toString().trim()).putString(KEY_USERNAME, usernameInput.getText().toString().trim()).putString(KEY_PASSWORD, passwordInput.getText().toString()).apply(); }

    private void runPageScript() {
        if (workMode == MODE_IDLE) return;
        String action; String path;
        if (workMode == MODE_REFRESH_MAIN) { action = "READ_MAIN"; path = MAIN_PATH; } else if (workMode == MODE_REFRESH_ACL) { action = "READ_ACL"; path = ACL_PATH; } else if (workMode == MODE_TOGGLE_MAIN) { action = "TOGGLE_MAIN"; path = MAIN_PATH; } else if (workMode == MODE_TOGGLE_ACL) { action = "TOGGLE_ACL"; path = ACL_PATH; } else { action = "APPLY_TCP"; path = MAIN_PATH; }
        String js = """
                (function(){
                  try{
                    var USER=__USER__, PASS=__PASS__, ACTION=__ACTION__, TARGET_PATH=__TARGET_PATH__, TARGET_TCP_KEY=__TCP_KEY__, TARGET_TCP_LABEL=__TCP_LABEL__;
                    function text(el){return ((el && (el.innerText||el.textContent||el.value) || '')+'').trim();}
                    function fire(el,type){try{el.dispatchEvent(new Event(type,{bubbles:true,cancelable:true}));}catch(e){}}
                    function ret(o){return JSON.stringify(o);}
                    function findLogin(){var p=document.querySelector('input[name="luci_password"],input#luci_password,input[name="password"],input[type="password"]'); if(!p)return null; var r=p.form||document; var u=r.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]')||document.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]'); if(!u)return null; return {user:u,pass:p,form:(p.form||u.form)};}
                    function submitLogin(l){l.user.focus();l.user.value=USER;fire(l.user,'input');fire(l.user,'change');l.pass.focus();l.pass.value=PASS;fire(l.pass,'input');fire(l.pass,'change');var f=l.form;var b=f?f.querySelector('button[type="submit"],input[type="submit"],button,input.cbi-button'):null;if(!b)b=Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"]')).find(function(e){return /登录|登陆|Login|Sign in/i.test(text(e));});if(b)b.click();else if(f)f.submit();else return false;return true;}
                    var login=findLogin(); if(login){if(submitLogin(login))return ret({kind:'LOGIN_SUBMITTED'});return ret({kind:'LOGIN_FORM_NO_SUBMIT'});}
                    var pth=location.pathname.replace(/\/$/,''); var want=TARGET_PATH.replace(/\/$/,''); if(pth!==want)return ret({kind:'NOT_PAGE',url:location.href,want:TARGET_PATH});
                    function findApplyButton(){var a=document.querySelector('.cbi-button-apply,input[name="cbi.apply"],button[name="cbi.apply"],input[value*="保存并应用"],button[value*="保存并应用"]'); if(a)return a; return Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"],a')).find(function(e){var t=text(e);return t.indexOf('保存并应用')>=0||/Save\\s*&\\s*Apply/i.test(t)||/Save.*Apply/i.test(t);});}
                    function findSwitch(kind){var cbs=Array.from(document.querySelectorAll('input[type="checkbox"]')); if(kind==='acl'){var acl=cbs.find(function(cb){var s=((cb.name||'')+' '+(cb.id||'')+' '+(cb.getAttribute('data-widget-id')||''));return s.indexOf('acl_enable')>=0;}); if(acl)return acl;} else {var ex=cbs.find(function(cb){var s=((cb.name||'')+' '+(cb.id||'')+' '+(cb.getAttribute('data-widget-id')||'')).toLowerCase();return (s.indexOf('.enabled')>=0||s.indexOf('_enabled')>=0||s.indexOf('.enable')>=0)&&s.indexOf('acl')<0&&s.indexOf('socks')<0;}); if(ex)return ex;} var labels=Array.from(document.querySelectorAll('label')).filter(function(l){var t=text(l).replace(/\s/g,'');return t==='主开关'||(kind==='acl'&&t.indexOf('主开关')>=0);}); for(var i=0;i<labels.length;i++){var l=labels[i], body=text(l.closest('.cbi-value,.cbi-section-node,div')||l); if(kind==='main'&&/Socks|ACL|访问控制/i.test(body))continue; var id=l.getAttribute('for'); if(id){var by=document.getElementById(id); if(by&&by.type==='checkbox')return by;} var near=l.closest('.cbi-value,.cbi-section-node,div'); if(near){var cb=near.querySelector('input[type="checkbox"]'); if(cb)return cb;}} return null;}
                    function readTcp(){var all=window.lv_dropdown_data||{};var cbid=Object.keys(all).find(function(k){return k.indexOf('.tcp_node')>=0;});var data=cbid?all[cbid]:null;var nodes=[]; if(data){(data.ungrouped||[]).forEach(function(n){nodes.push({key:n.key||'',label:n.label||'关闭'});});(data.group_order||Object.keys(data.groups||{})).forEach(function(g){(data.groups&&data.groups[g]||[]).forEach(function(n){nodes.push({key:n.key||'',label:n.label||''});});});return {cbid:cbid,current_key:data.current_key||'',current_label:data.current_label||'',nodes:nodes};} var sel=Array.from(document.querySelectorAll('select')).find(function(s){return ((s.name||'')+(s.id||'')).indexOf('tcp_node')>=0;});if(sel){Array.from(sel.options).forEach(function(o){nodes.push({key:o.value||'',label:o.textContent||o.label||''});});return {cbid:sel.id||sel.name,current_key:sel.value||'',current_label:(sel.options[sel.selectedIndex]||{}).text||'',nodes:nodes};}return {cbid:'',current_key:'',current_label:'',nodes:[]};}
                    function applyTcp(){var info=readTcp();var cbid=info.cbid;var sel=(cbid&&document.getElementById(cbid))||Array.from(document.querySelectorAll('select')).find(function(s){return ((s.name||'')+(s.id||'')).indexOf('tcp_node')>=0;});if(!sel)return ret({kind:'TCP_NOT_FOUND'});var opt=Array.from(sel.options).find(function(o){return o.value===TARGET_TCP_KEY;});if(!opt){opt=document.createElement('option');opt.value=TARGET_TCP_KEY;opt.textContent=TARGET_TCP_LABEL||TARGET_TCP_KEY;sel.appendChild(opt);}sel.value=TARGET_TCP_KEY;Array.from(sel.options).forEach(function(o){o.selected=(o.value===TARGET_TCP_KEY);});fire(sel,'input');fire(sel,'change');var data=(window.lv_dropdown_data||{})[cbid];if(data){data.current_key=TARGET_TCP_KEY;data.current_label=TARGET_TCP_LABEL;}var lab=document.getElementById(cbid+'.label');if(lab){lab.textContent=TARGET_TCP_LABEL;lab.title=TARGET_TCP_LABEL;}var apply=findApplyButton();if(!apply)return ret({kind:'APPLY_NOT_FOUND'});apply.click();return ret({kind:'TCP_APPLIED',key:TARGET_TCP_KEY,label:TARGET_TCP_LABEL});}
                    function toggle(kind){var sw=findSwitch(kind);if(!sw)return ret({kind:'SWITCH_NOT_FOUND',switch:kind});var before=!!sw.checked;var lab=sw.id?document.querySelector('label[for="'+sw.id+'"]'):null;if(lab)lab.click();else sw.click();if(before===sw.checked)sw.checked=!sw.checked;fire(sw,'input');fire(sw,'change');var after=!!sw.checked;var apply=findApplyButton();if(!apply)return ret({kind:'APPLY_NOT_FOUND',after:after});apply.click();return ret({kind:kind==='main'?'MAIN_TOGGLED':'ACL_TOGGLED',enabled:after});}
                    if(ACTION==='READ_MAIN'){var sw=findSwitch('main');if(!sw)return ret({kind:'SWITCH_NOT_FOUND',switch:'main'});return ret({kind:'MAIN_STATE',enabled:!!sw.checked,tcp:readTcp()});}
                    if(ACTION==='READ_ACL'){var asw=findSwitch('acl');if(!asw)return ret({kind:'SWITCH_NOT_FOUND',switch:'acl'});return ret({kind:'ACL_STATE',enabled:!!asw.checked});}
                    if(ACTION==='TOGGLE_MAIN')return toggle('main'); if(ACTION==='TOGGLE_ACL')return toggle('acl'); if(ACTION==='APPLY_TCP')return applyTcp(); return ret({kind:'ERROR',message:'unknown action'});
                  }catch(e){return JSON.stringify({kind:'ERROR',message:e.message});}
                })()
                """;
        js = js.replace("__USER__", JSONObject.quote(usernameInput.getText().toString().trim())).replace("__PASS__", JSONObject.quote(passwordInput.getText().toString())).replace("__ACTION__", JSONObject.quote(action)).replace("__TARGET_PATH__", JSONObject.quote(path)).replace("__TCP_KEY__", JSONObject.quote(targetTcpKey)).replace("__TCP_LABEL__", JSONObject.quote(targetTcpLabel));
        webView.evaluateJavascript(js, result -> handlePageScriptResult(result == null ? "" : result));
    }

    private void handlePageScriptResult(String rawResult) {
        if (workMode == MODE_IDLE) return; String result = decodeJsString(rawResult);
        try { JSONObject obj = new JSONObject(result); String kind = obj.optString("kind");
            if ("LOGIN_SUBMITTED".equals(kind)) { statusText.setText("登录会话已超时，正在自动登录..."); detailText.setText("登录完成后会继续执行当前操作。"); return; }
            if ("NOT_PAGE".equals(kind)) { retryLoadCurrentPage("当前页面不是目标页面，正在重新进入..."); return; }
            if ("MAIN_STATE".equals(kind)) { passwallEnabled = obj.optBoolean("enabled"); updateSwitchButton(passwallButton, passwallEnabled, "PassWall 总开关"); updateTcpNodes(obj.optJSONObject("tcp")); if (refreshAllInProgress) beginWork(MODE_REFRESH_ACL, ACL_PATH, "正在读取访问控制状态...", "总开关和 TCP 节点已读取，继续读取访问控制开关。", true); else finishWork("PassWall 状态已刷新"); return; }
            if ("ACL_STATE".equals(kind)) { aclEnabled = obj.optBoolean("enabled"); updateSwitchButton(aclButton, aclEnabled, "访问控制开关"); refreshAllInProgress = false; finishWork("状态已刷新"); detailText.setText("已读取总开关、访问控制开关和 TCP 节点。选择 TCP 节点会自动保存并应用。"); return; }
            if ("MAIN_TOGGLED".equals(kind)) { passwallEnabled = obj.optBoolean("enabled"); updateSwitchButton(passwallButton, passwallEnabled, "PassWall 总开关"); finishWork("PassWall 总开关已切换"); detailText.setText((passwallEnabled ? "已切换为：开启" : "已切换为：关闭") + "，已点击保存并应用。建议等待几秒生效。"); return; }
            if ("ACL_TOGGLED".equals(kind)) { aclEnabled = obj.optBoolean("enabled"); updateSwitchButton(aclButton, aclEnabled, "访问控制开关"); finishWork("访问控制开关已切换"); detailText.setText((aclEnabled ? "已切换为：开启" : "已切换为：关闭") + "，已点击保存并应用。建议等待几秒生效。"); return; }
            if ("TCP_APPLIED".equals(kind)) { currentTcpKey = obj.optString("key"); currentTcpLabel = obj.optString("label"); tcpNodeTitle.setText("TCP 节点：" + currentTcpLabel); finishWork("TCP 节点已切换"); detailText.setText("已切换到：" + currentTcpLabel + "，并点击保存并应用。建议等待几秒生效。"); return; }
            if ("SWITCH_NOT_FOUND".equals(kind) || "TCP_NOT_FOUND".equals(kind)) { retryLoadCurrentPage("没有找到目标控件，正在等待页面加载并重试..."); return; }
            if ("APPLY_NOT_FOUND".equals(kind)) { failWork("已经操作控件，但没有找到“保存并应用”按钮。请点“打开网页”确认页面结构。"); return; }
            if ("LOGIN_FORM_NO_SUBMIT".equals(kind)) { failWork("已填写登录信息，但没有找到登录按钮。请确认 LuCI 登录页是否正常。"); return; }
            failWork("自动操作失败：" + result);
        } catch (Exception e) { retryLoadCurrentPage("页面返回内容不稳定，正在重试..."); }
    }

    private void retryLoadCurrentPage(String reason) { if (workMode == MODE_IDLE) return; if (pageScriptRetryCount < MAX_PAGE_SCRIPT_RETRY) { pageScriptRetryCount++; statusText.setText("正在等待页面加载..."); detailText.setText(reason + " 第 " + pageScriptRetryCount + "/" + MAX_PAGE_SCRIPT_RETRY + " 次。"); String path = (workMode == MODE_REFRESH_ACL || workMode == MODE_TOGGLE_ACL) ? ACL_PATH : MAIN_PATH; handler.postDelayed(() -> { if (workMode != MODE_IDLE) webView.loadUrl(addCacheBuster(normalizeToUrl(addressInput.getText().toString().trim(), path))); }, 1200); return; } failWork("多次重试后仍未找到目标控件。请点“设置”里的“打开网页”，确认 LuCI 页面是否正常。"); }
    private void updateTcpNodes(JSONObject tcp) { tcpSpinnerReady = false; tcpNodes.clear(); if (tcp != null) { currentTcpKey = tcp.optString("current_key", ""); currentTcpLabel = tcp.optString("current_label", ""); JSONArray arr = tcp.optJSONArray("nodes"); if (arr != null) { for (int i = 0; i < arr.length(); i++) { JSONObject n = arr.optJSONObject(i); if (n != null) tcpNodes.add(new NodeItem(n.optString("key", ""), n.optString("label", ""))); } } } if (tcpNodes.isEmpty()) tcpNodes.add(new NodeItem("", "未读取到 TCP 节点")); tcpAdapter.notifyDataSetChanged(); int selected = 0; for (int i = 0; i < tcpNodes.size(); i++) if (tcpNodes.get(i).key.equals(currentTcpKey)) { selected = i; break; } tcpSpinner.setSelection(selected, false); tcpNodeTitle.setText(currentTcpLabel.length() == 0 ? "TCP 节点：未读取到当前节点" : "TCP 节点：" + currentTcpLabel); handler.postDelayed(() -> tcpSpinnerReady = true, 500); }
    private void finishWork(String message) { workMode = MODE_IDLE; pageScriptRetryCount = 0; setControlsEnabled(true); statusText.setText(message); CookieManager.getInstance().flush(); }
    private void failWork(String message) { workMode = MODE_IDLE; pageScriptRetryCount = 0; refreshAllInProgress = false; setControlsEnabled(true); statusText.setText("执行失败"); detailText.setText(message); Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void setControlsEnabled(boolean enabled) { if (passwallButton != null) passwallButton.setEnabled(enabled); if (aclButton != null) aclButton.setEnabled(enabled); if (tcpSpinner != null) tcpSpinner.setEnabled(enabled); if (settingsButton != null) settingsButton.setEnabled(enabled); if (openPageButton != null) openPageButton.setEnabled(enabled); }
    private void updateSwitchButton(Button button, Boolean enabled, String title) { if (enabled == null) { setButtonStyle(button, Color.rgb(107, 119, 140)); button.setText(title + "\n状态未知，点击刷新"); } else if (enabled) { setButtonStyle(button, Color.rgb(23, 166, 92)); button.setText(title + "\n当前：开启，点击关闭"); } else { setButtonStyle(button, Color.rgb(218, 62, 62)); button.setText(title + "\n当前：关闭，点击开启"); } }
    private void setButtonStyle(Button button, int color) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(18)); button.setBackground(bg); button.setTextColor(Color.WHITE); }
    private String normalizeToUrl(String raw, String path) { String value = raw == null ? "" : raw.trim(); if (value.length() == 0) value = DEFAULT_ADDRESS; if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value; Uri uri = Uri.parse(value); String scheme = uri.getScheme() == null ? "https" : uri.getScheme(); String authority = uri.getEncodedAuthority(); if (authority == null || authority.length() == 0) return DEFAULT_ADDRESS + path; return scheme + "://" + authority + path; }
    private String addCacheBuster(String url) { return url + (url.contains("?") ? "&" : "?") + "_pwts=" + System.currentTimeMillis(); }
    private boolean isNetworkAvailable() { ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE); if (cm == null) return true; NetworkInfo info = cm.getActiveNetworkInfo(); return info != null && info.isConnected(); }
    private void hideKeyboardSafe() { try { View current = getCurrentFocus(); if (current != null) { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.hideSoftInputFromWindow(current.getWindowToken(), 0); current.clearFocus(); } } catch (Exception ignored) {} }
    private String decodeJsString(String rawResult) { String result = rawResult == null ? "" : rawResult; if (result.startsWith("\"") && result.endsWith("\"") && result.length() >= 2) { result = result.substring(1, result.length() - 1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\u003C", "<").replace("\\u003E", ">").replace("\\u0026", "&").replace("\\/", "/"); } return result; }
    private void injectCompactMode() { String js = "javascript:(function(){try{var css='html,body{max-width:100%!important;overflow-x:auto!important;}.main-left,.sidebar,aside,#mainmenu,#modemenu,.breadcrumb{display:none!important;}header,.navbar,.brand,.pull-left{display:none!important;}.main,.main-right,.container,.container-fluid,#maincontent{margin-left:0!important;left:0!important;width:100%!important;max-width:100%!important;padding-left:8px!important;padding-right:8px!important;}.cbi-map,.cbi-section,.cbi-section-node{max-width:100%!important;width:100%!important;}table,.table{width:100%!important;display:block!important;overflow-x:auto!important;}input,select,textarea,button,.btn,.cbi-button{min-height:40px!important;font-size:15px!important;}.cbi-value-title{min-width:92px!important;}';var style=document.getElementById('pw-acl-compact-style');if(!style){style=document.createElement('style');style.id='pw-acl-compact-style';document.head.appendChild(style);}style.innerHTML=css;document.title='PassWall 控制';}catch(e){}})()"; webView.evaluateJavascript(js, null); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onPause() { super.onPause(); CookieManager.getInstance().flush(); }
    @Override public void onBackPressed() { if (debugPageVisible) closeDebugWebPage(); else if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE && prefs.getString(KEY_PASSWORD, "").length() > 0) toggleSettingsPanel(); else if (webView != null && webView.getLayoutParams().height > dp(10) && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
