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

    // PassWall 基本设置页。总开关和 TCP 节点都在这个页面。
    private static final String MAIN_PATH = "/cgi-bin/luci/admin/services/passwall/settings";
    private static final String ACL_PATH = "/cgi-bin/luci/admin/services/passwall/acl";

    private static final int IDLE = 0;
    private static final int READ_MAIN = 1;
    private static final int READ_ACL = 2;
    private static final int TOGGLE_MAIN = 3;
    private static final int TOGGLE_ACL = 4;
    private static final int APPLY_TCP = 5;
    private static final int MAX_RETRY = 8;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private ScrollView scrollView;
    private LinearLayout settingsPanel;
    private WebView webView;
    private EditText addressInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView statusText;
    private TextView detailText;
    private TextView tcpNodeTitle;
    private ProgressBar progressBar;
    private Button passwallButton;
    private Button aclButton;
    private Button settingsButton;
    private Button openPageButton;
    private Spinner tcpSpinner;
    private ArrayAdapter<NodeItem> tcpAdapter;
    private final ArrayList<NodeItem> tcpNodes = new ArrayList<>();

    private int mode = IDLE;
    private int retry = 0;
    private boolean refreshAll = false;
    private boolean debugVisible = false;
    private boolean spinnerReady = false;
    private Boolean passwallEnabled = null;
    private Boolean aclEnabled = null;
    private String currentTcpKey = "";
    private String currentTcpLabel = "";
    private String targetTcpKey = "";
    private String targetTcpLabel = "";

    private static class NodeItem {
        final String key;
        final String label;

        NodeItem(String key, String label) {
            this.key = key == null ? "" : key;
            this.label = label == null ? "" : label;
        }

        @Override
        public String toString() {
            return label.length() == 0 ? "关闭" : label;
        }
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

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setGravity(Gravity.CENTER_HORIZONTAL);
        main.setPadding(dp(22), dp(18), dp(22), dp(12));
        scrollView.addView(main, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("passwall控制");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(27, 43, 68));
        title.setGravity(Gravity.CENTER);
        main.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("总开关、访问控制、TCP节点切换");
        sub.setTextSize(14);
        sub.setTextColor(Color.rgb(104, 116, 135));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(16));
        main.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        settingsPanel.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsLp.setMargins(0, 0, 0, dp(14));
        main.addView(settingsPanel, settingsLp);

        addressInput = makeInput("软路由地址，例如 https://10.1.1.1", false);
        addressInput.setText(prefs.getString(KEY_ADDRESS, DEFAULT_ADDRESS));
        usernameInput = makeInput("LuCI 用户名，例如 root", false);
        usernameInput.setText(prefs.getString(KEY_USERNAME, DEFAULT_USERNAME));
        passwordInput = makeInput("LuCI 密码，仅保存在本机", true);
        passwordInput.setText(prefs.getString(KEY_PASSWORD, ""));

        settingsPanel.addView(label("软路由地址"));
        settingsPanel.addView(addressInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        settingsPanel.addView(label("用户名"));
        settingsPanel.addView(usernameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        settingsPanel.addView(label("密码"));
        settingsPanel.addView(passwordInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button saveButton = smallButton("保存设置");
        Button refreshButton = smallButton("刷新状态");
        row1.addView(saveButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        row1.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingsPanel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        openPageButton = smallButton("打开网页");
        Button clearButton = smallButton("清除登录");
        row2.addView(openPageButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        row2.addView(clearButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingsPanel.addView(row2);

        View spacer = new View(this);
        main.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        passwallButton = bigButton();
        main.addView(passwallButton, buttonLayout(92, 12));
        switchStyle(passwallButton, null, "PassWall 总开关");

        aclButton = bigButton();
        main.addView(aclButton, buttonLayout(92, 18));
        switchStyle(aclButton, null, "访问控制开关");

        tcpNodeTitle = new TextView(this);
        tcpNodeTitle.setText("TCP 节点：正在加载...");
        tcpNodeTitle.setTextSize(15);
        tcpNodeTitle.setTextColor(Color.rgb(40, 55, 80));
        tcpNodeTitle.setGravity(Gravity.LEFT);
        tcpNodeTitle.setPadding(0, 0, 0, dp(6));
        main.addView(tcpNodeTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tcpSpinner = new Spinner(this);
        tcpNodes.add(new NodeItem("", "节点列表未加载"));
        tcpAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tcpNodes);
        tcpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tcpSpinner.setAdapter(tcpAdapter);
        main.addView(tcpSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        statusText = new TextView(this);
        statusText.setText("正在初始化");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.rgb(40, 55, 80));
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(14), 0, dp(6));
        main.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detailText = new TextView(this);
        detailText.setText("首次使用请先在上方设置软路由地址、用户名和密码。");
        detailText.setTextSize(13);
        detailText.setTextColor(Color.rgb(110, 120, 135));
        detailText.setGravity(Gravity.CENTER);
        main.addView(detailText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        settingsButton = smallButton("设置");
        LinearLayout.LayoutParams settingsButtonLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        settingsButtonLp.setMargins(0, dp(12), 0, dp(8));
        main.addView(settingsButton, settingsButtonLp);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        setContentView(root);
        setupWebView();

        passwallButton.setOnClickListener(v -> startToggleMain());
        aclButton.setOnClickListener(v -> startToggleAcl());
        settingsButton.setOnClickListener(v -> toggleSettings());
        saveButton.setOnClickListener(v -> {
            saveSettings();
            hideKeyboard();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            settingsPanel.setVisibility(View.GONE);
            settingsButton.setText("设置");
            refreshAll();
        });
        refreshButton.setOnClickListener(v -> {
            saveSettings();
            hideKeyboard();
            refreshAll();
        });
        openPageButton.setOnClickListener(v -> openDebugPage());
        clearButton.setOnClickListener(v -> clearLogin());
        tcpSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnerReady || position < 0 || position >= tcpNodes.size()) return;
                NodeItem item = tcpNodes.get(position);
                if (!item.key.equals(currentTcpKey)) {
                    applyTcpNode(item.key, item.label);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        boolean hasPassword = prefs.getString(KEY_PASSWORD, "").length() > 0;
        settingsPanel.setVisibility(hasPassword ? View.GONE : View.VISIBLE);
        settingsButton.setText(hasPassword ? "设置" : "关闭设置");
        if (hasPassword) {
            handler.postDelayed(this::refreshAll, 400);
        } else {
            statusText.setText("等待设置");
        }
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13);
        v.setTextColor(Color.rgb(80, 92, 110));
        v.setPadding(0, dp(8), 0, dp(2));
        return v;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        return b;
    }

    private Button bigButton() {
        Button b = new Button(this);
        b.setTextSize(20);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private LinearLayout.LayoutParams buttonLayout(int heightDp, int bottomMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
        lp.setMargins(0, 0, 0, dp(bottomMarginDp));
        return lp;
    }

    private EditText makeInput(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setHint(hint);
        e.setSelectAllOnFocus(false);
        e.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && scrollView != null) {
                handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150);
                handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 500);
            }
        });
        e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        return e;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setUserAgentString(s.getUserAgentString() + " PassWallControl/2.2");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                injectCompactMode();
                if (mode != IDLE) {
                    handler.postDelayed(MainActivity.this::runScript, 1500);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    fail("页面打开失败，请检查软路由地址或手机网络。");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });
    }

    private void refreshAll() {
        saveSettings();
        hideKeyboard();
        if (!checkSettings()) return;
        refreshAll = true;
        begin(READ_MAIN, MAIN_PATH, "正在读取 PassWall 状态...", "正在读取总开关、TCP 节点和访问控制开关。", true);
    }

    private void startToggleMain() {
        saveSettings();
        hideKeyboard();
        if (!checkSettings()) return;
        begin(TOGGLE_MAIN, MAIN_PATH, "正在切换 PassWall 总开关...", "将进入 PassWall 设置页，操作顶部主开关，并保存应用。", true);
    }

    private void startToggleAcl() {
        saveSettings();
        hideKeyboard();
        if (!checkSettings()) return;
        begin(TOGGLE_ACL, ACL_PATH, "正在切换访问控制开关...", "将进入访问控制页，操作主开关，并保存应用。", true);
    }

    private void applyTcpNode(String key, String label) {
        saveSettings();
        hideKeyboard();
        if (!checkSettings()) return;
        targetTcpKey = key == null ? "" : key;
        targetTcpLabel = label == null ? "" : label;
        begin(APPLY_TCP, MAIN_PATH, "正在切换 TCP 节点...", "目标节点：" + targetTcpLabel + "。将自动保存并应用。", true);
    }

    private void begin(int nextMode, String path, String status, String detail, boolean hideWeb) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "当前手机网络不可用", Toast.LENGTH_SHORT).show();
        }
        mode = nextMode;
        retry = 0;
        debugVisible = false;
        setControlsEnabled(false);
        statusText.setText(status);
        detailText.setText(detail);
        if (openPageButton != null) openPageButton.setText("打开网页");
        if (hideWeb) {
            webView.getLayoutParams().height = dp(1);
            webView.requestLayout();
        }
        webView.loadUrl(cache(urlFor(addressInput.getText().toString().trim(), path)));
    }

    private void runScript() {
        if (mode == IDLE) return;
        String action = mode == READ_MAIN ? "READ_MAIN" : mode == READ_ACL ? "READ_ACL" : mode == TOGGLE_MAIN ? "TOGGLE_MAIN" : mode == TOGGLE_ACL ? "TOGGLE_ACL" : "APPLY_TCP";
        String path = (mode == READ_ACL || mode == TOGGLE_ACL) ? ACL_PATH : MAIN_PATH;
        String js = """
(function(){try{
 var USER=__USER__,PASS=__PASS__,ACTION=__ACTION__,TARGET_PATH=__TARGET_PATH__,TCP_KEY=__TCP_KEY__,TCP_LABEL=__TCP_LABEL__;
 function text(e){return ((e&&(e.innerText||e.textContent||e.value)||'')+'').trim();}
 function fire(e,t){try{e.dispatchEvent(new Event(t,{bubbles:true,cancelable:true}));}catch(x){}}
 function ret(o){return JSON.stringify(o);}
 function login(){var p=document.querySelector('input[name="luci_password"],input#luci_password,input[name="password"],input[type="password"]'); if(!p)return null; var r=p.form||document; var u=r.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]')||document.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]'); if(!u)return null; return {u:u,p:p,f:(p.form||u.form)};}
 function submit(l){l.u.value=USER;fire(l.u,'input');fire(l.u,'change');l.p.value=PASS;fire(l.p,'input');fire(l.p,'change');var b=l.f?l.f.querySelector('button[type="submit"],input[type="submit"],button,input.cbi-button'):null;if(!b)b=Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"]')).find(function(e){return /登录|登陆|Login|Sign in/i.test(text(e));}); if(b)b.click(); else if(l.f)l.f.submit(); else return false; return true;}
 var l=login(); if(l){return submit(l)?ret({kind:'LOGIN'}):ret({kind:'LOGIN_NO_BUTTON'});}
 var pth=location.pathname.replace(/\/$/,''); var want=TARGET_PATH.replace(/\/$/,''); if(pth!==want)return ret({kind:'NOT_PAGE',url:location.href,want:TARGET_PATH});
 function applyBtn(){var a=document.querySelector('.cbi-button-apply,input[name="cbi.apply"],button[name="cbi.apply"],input[value*="保存并应用"],button[value*="保存并应用"]'); if(a)return a; return Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"],a')).find(function(e){var t=text(e);return t.indexOf('保存并应用')>=0||(t.toLowerCase().indexOf('save')>=0&&t.toLowerCase().indexOf('apply')>=0);});}
 function exactMain(){var row=document.getElementById('cbi-passwall-cfg013fd6-enabled'); if(row){var cb=row.querySelector('input[type="checkbox"][name="cbid.passwall.cfg013fd6.enabled"],input[type="checkbox"][data-widget-id="widget.cbid.passwall.cfg013fd6.enabled"],input[type="checkbox"][name$=".enabled"]'); if(cb)return cb;} var cb=document.querySelector('input[type="checkbox"][name="cbid.passwall.cfg013fd6.enabled"],input[type="checkbox"][data-widget-id="widget.cbid.passwall.cfg013fd6.enabled"]'); if(cb)return cb; var rows=Array.from(document.querySelectorAll('.cbi-value[id^="cbi-passwall-"][id$="-enabled"]')); for(var i=0;i<rows.length;i++){var body=text(rows[i]); if(/Socks|ACL|访问控制/i.test(body))continue; var c=rows[i].querySelector('input[type="checkbox"][name$=".enabled"]'); if(c)return c;} return null;}
 function aclSwitch(){return document.querySelector('input[type="checkbox"][name*="acl_enable"],input[type="checkbox"][id*="acl_enable"],input[type="checkbox"][data-widget-id*="acl_enable"]');}
 function readTcp(){var all=window.lv_dropdown_data||{}, keys=Object.keys(all), cbid=keys.find(function(k){return k.indexOf('.tcp_node')>=0;}), data=cbid?all[cbid]:null, nodes=[]; if(data){(data.ungrouped||[]).forEach(function(n){nodes.push({key:n.key||'',label:n.label||'关闭'});});(data.group_order||Object.keys(data.groups||{})).forEach(function(g){(data.groups&&data.groups[g]||[]).forEach(function(n){nodes.push({key:n.key||'',label:n.label||''});});}); return {cbid:cbid,current_key:data.current_key||'',current_label:data.current_label||'',nodes:nodes};} return {cbid:'',current_key:'',current_label:'',nodes:nodes};}
 function setTcp(){var info=readTcp(), cbid=info.cbid, sel=cbid?document.getElementById(cbid):null; if(!sel)sel=Array.from(document.querySelectorAll('select')).find(function(s){return ((s.name||'')+(s.id||'')).indexOf('tcp_node')>=0;}); if(!sel)return ret({kind:'TCP_NOT_FOUND'}); var opt=Array.from(sel.options).find(function(o){return o.value===TCP_KEY;}); if(!opt){opt=document.createElement('option'); opt.value=TCP_KEY; opt.textContent=TCP_LABEL||TCP_KEY; sel.appendChild(opt);} sel.value=TCP_KEY; Array.from(sel.options).forEach(function(o){o.selected=o.value===TCP_KEY;}); fire(sel,'input'); fire(sel,'change'); var data=(window.lv_dropdown_data||{})[cbid]; if(data){data.current_key=TCP_KEY; data.current_label=TCP_LABEL;} var lab=document.getElementById(cbid+'.label'); if(lab){lab.textContent=TCP_LABEL; lab.title=TCP_LABEL;} var a=applyBtn(); if(!a)return ret({kind:'NO_APPLY'}); a.click(); return ret({kind:'TCP_DONE',key:TCP_KEY,label:TCP_LABEL});}
 function toggle(cb,kind){if(!cb)return ret({kind:'NO_SWITCH',which:kind}); var after=!cb.checked; cb.checked=after; fire(cb,'input'); fire(cb,'change'); var a=applyBtn(); if(!a)return ret({kind:'NO_APPLY',enabled:after}); a.click(); return ret({kind:kind==='main'?'MAIN_DONE':'ACL_DONE',enabled:after});}
 if(ACTION==='READ_MAIN'){var m=exactMain(); if(!m)return ret({kind:'NO_SWITCH',which:'main'}); return ret({kind:'MAIN_STATE',enabled:!!m.checked,tcp:readTcp()});}
 if(ACTION==='READ_ACL'){var ac=aclSwitch(); if(!ac)return ret({kind:'NO_SWITCH',which:'acl'}); return ret({kind:'ACL_STATE',enabled:!!ac.checked});}
 if(ACTION==='TOGGLE_MAIN')return toggle(exactMain(),'main');
 if(ACTION==='TOGGLE_ACL')return toggle(aclSwitch(),'acl');
 if(ACTION==='APPLY_TCP')return setTcp();
 return ret({kind:'ERR',message:'bad action'});
}catch(e){return JSON.stringify({kind:'ERR',message:e.message});}})()
""";
        js = js.replace("__USER__", JSONObject.quote(usernameInput.getText().toString().trim()))
                .replace("__PASS__", JSONObject.quote(passwordInput.getText().toString()))
                .replace("__ACTION__", JSONObject.quote(action))
                .replace("__TARGET_PATH__", JSONObject.quote(path))
                .replace("__TCP_KEY__", JSONObject.quote(targetTcpKey))
                .replace("__TCP_LABEL__", JSONObject.quote(targetTcpLabel));
        webView.evaluateJavascript(js, result -> handleResult(result == null ? "" : result));
    }

    private void handleResult(String raw) {
        if (mode == IDLE) return;
        try {
            JSONObject o = new JSONObject(decode(raw));
            String kind = o.optString("kind");
            if ("LOGIN".equals(kind)) {
                statusText.setText("登录会话已超时，正在自动登录...");
                detailText.setText("登录完成后会继续执行当前操作。");
                return;
            }
            if ("NOT_PAGE".equals(kind)) {
                retry("当前页面不是目标页面，正在重新进入...");
                return;
            }
            if ("MAIN_STATE".equals(kind)) {
                passwallEnabled = o.optBoolean("enabled");
                switchStyle(passwallButton, passwallEnabled, "PassWall 总开关");
                updateTcp(o.optJSONObject("tcp"));
                if (refreshAll) {
                    begin(READ_ACL, ACL_PATH, "正在读取访问控制状态...", "总开关和 TCP 节点已读取，继续读取访问控制开关。", true);
                } else {
                    finish("PassWall 状态已刷新");
                }
                return;
            }
            if ("ACL_STATE".equals(kind)) {
                aclEnabled = o.optBoolean("enabled");
                switchStyle(aclButton, aclEnabled, "访问控制开关");
                refreshAll = false;
                finish("状态已刷新");
                detailText.setText("已读取总开关、访问控制开关和 TCP 节点。选择 TCP 节点会自动保存并应用。");
                return;
            }
            if ("MAIN_DONE".equals(kind)) {
                passwallEnabled = o.optBoolean("enabled");
                switchStyle(passwallButton, passwallEnabled, "PassWall 总开关");
                finish("PassWall 总开关已切换");
                detailText.setText((passwallEnabled ? "已切换为：开启" : "已切换为：关闭") + "，已点击保存并应用。建议等待几秒生效。");
                return;
            }
            if ("ACL_DONE".equals(kind)) {
                aclEnabled = o.optBoolean("enabled");
                switchStyle(aclButton, aclEnabled, "访问控制开关");
                finish("访问控制开关已切换");
                detailText.setText((aclEnabled ? "已切换为：开启" : "已切换为：关闭") + "，已点击保存并应用。建议等待几秒生效。");
                return;
            }
            if ("TCP_DONE".equals(kind)) {
                currentTcpKey = o.optString("key");
                currentTcpLabel = o.optString("label");
                tcpNodeTitle.setText("TCP 节点：" + currentTcpLabel);
                finish("TCP 节点已切换");
                detailText.setText("已切换到：" + currentTcpLabel + "，并点击保存并应用。");
                return;
            }
            if ("NO_SWITCH".equals(kind) || "TCP_NOT_FOUND".equals(kind)) {
                retry("没有找到目标控件，正在等待页面加载并重试...");
                return;
            }
            if ("NO_APPLY".equals(kind)) {
                fail("已操作控件，但没有找到“保存并应用”按钮。请点“打开网页”确认页面结构。");
                return;
            }
            if ("LOGIN_NO_BUTTON".equals(kind)) {
                fail("已填写登录信息，但没有找到登录按钮。");
                return;
            }
            fail("自动操作失败：" + o.toString());
        } catch (Exception e) {
            retry("页面返回内容不稳定，正在重试...");
        }
    }

    private void retry(String reason) {
        if (mode == IDLE) return;
        if (retry < MAX_RETRY) {
            retry++;
            statusText.setText("正在等待页面加载...");
            detailText.setText(reason + " 第 " + retry + "/" + MAX_RETRY + " 次。");
            String path = (mode == READ_ACL || mode == TOGGLE_ACL) ? ACL_PATH : MAIN_PATH;
            handler.postDelayed(() -> {
                if (mode != IDLE) {
                    webView.loadUrl(cache(urlFor(addressInput.getText().toString().trim(), path)));
                }
            }, 1200);
            return;
        }
        fail("多次重试后仍未找到目标控件。请点“设置”里的“打开网页”，确认 LuCI 页面是否正常。");
    }

    private void updateTcp(JSONObject tcp) {
        spinnerReady = false;
        tcpNodes.clear();
        if (tcp != null) {
            currentTcpKey = tcp.optString("current_key", "");
            currentTcpLabel = tcp.optString("current_label", "");
            JSONArray arr = tcp.optJSONArray("nodes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject n = arr.optJSONObject(i);
                    if (n != null) {
                        tcpNodes.add(new NodeItem(n.optString("key", ""), n.optString("label", "")));
                    }
                }
            }
        }
        if (tcpNodes.isEmpty()) {
            tcpNodes.add(new NodeItem("", "未读取到 TCP 节点"));
        }
        tcpAdapter.notifyDataSetChanged();
        int selected = 0;
        for (int i = 0; i < tcpNodes.size(); i++) {
            if (tcpNodes.get(i).key.equals(currentTcpKey)) {
                selected = i;
                break;
            }
        }
        tcpSpinner.setSelection(selected, false);
        tcpNodeTitle.setText(currentTcpLabel.length() == 0 ? "TCP 节点：未读取到当前节点" : "TCP 节点：" + currentTcpLabel);
        handler.postDelayed(() -> spinnerReady = true, 500);
    }

    private void finish(String message) {
        mode = IDLE;
        retry = 0;
        setControlsEnabled(true);
        statusText.setText(message);
        CookieManager.getInstance().flush();
    }

    private void fail(String message) {
        mode = IDLE;
        retry = 0;
        refreshAll = false;
        setControlsEnabled(true);
        statusText.setText("执行失败");
        detailText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setControlsEnabled(boolean enabled) {
        if (passwallButton != null) passwallButton.setEnabled(enabled);
        if (aclButton != null) aclButton.setEnabled(enabled);
        if (tcpSpinner != null) tcpSpinner.setEnabled(enabled);
        if (settingsButton != null) settingsButton.setEnabled(enabled);
        if (openPageButton != null) openPageButton.setEnabled(enabled);
    }

    private void switchStyle(Button b, Boolean enabled, String title) {
        if (enabled == null) {
            color(b, Color.rgb(107, 119, 140));
            b.setText(title + "\n状态未知，点击刷新");
        } else if (enabled) {
            color(b, Color.rgb(23, 166, 92));
            b.setText(title + "\n当前：开启，点击关闭");
        } else {
            color(b, Color.rgb(218, 62, 62));
            b.setText(title + "\n当前：关闭，点击开启");
        }
    }

    private void color(Button b, int c) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(c);
        bg.setCornerRadius(dp(18));
        b.setBackground(bg);
        b.setTextColor(Color.WHITE);
    }

    private boolean checkSettings() {
        if (addressInput.getText().toString().trim().length() == 0) {
            addressInput.setText(DEFAULT_ADDRESS);
        }
        if (usernameInput.getText().toString().trim().length() == 0 || passwordInput.getText().toString().length() == 0) {
            settingsPanel.setVisibility(View.VISIBLE);
            settingsButton.setText("关闭设置");
            Toast.makeText(this, "请先填写 LuCI 用户名和密码", Toast.LENGTH_SHORT).show();
            statusText.setText("等待设置");
            detailText.setText("填写并保存后，App 会先验证登录状态，再执行控制。用户密码只保存在本机。");
            if (scrollView != null) {
                handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150);
            }
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

    private void clearLogin() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        passwallEnabled = null;
        aclEnabled = null;
        currentTcpKey = "";
        currentTcpLabel = "";
        switchStyle(passwallButton, null, "PassWall 总开关");
        switchStyle(aclButton, null, "访问控制开关");
        tcpNodeTitle.setText("TCP 节点：未登录");
        statusText.setText("已清除登录状态");
        detailText.setText("下次刷新状态或点击开关时，会在会话过期后重新登录。");
    }

    private void toggleSettings() {
        boolean show = settingsPanel.getVisibility() != View.VISIBLE;
        settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        settingsButton.setText(show ? "关闭设置" : "设置");
        if (show && scrollView != null) {
            handler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), 150);
        } else {
            hideKeyboard();
        }
    }

    private void openDebugPage() {
        saveSettings();
        hideKeyboard();
        if (debugVisible) {
            closeDebugPage();
            return;
        }
        mode = IDLE;
        retry = 0;
        refreshAll = false;
        debugVisible = true;
        setControlsEnabled(true);
        openPageButton.setText("关闭网页");
        statusText.setText("已打开网页模式");
        detailText.setText("显示 PassWall 设置页，用于排错。再次点击“关闭网页”可收起页面。");
        webView.getLayoutParams().height = dp(360);
        webView.requestLayout();
        webView.loadUrl(cache(urlFor(addressInput.getText().toString().trim(), MAIN_PATH)));
    }

    private void closeDebugPage() {
        debugVisible = false;
        if (openPageButton != null) openPageButton.setText("打开网页");
        if (webView != null) {
            webView.stopLoading();
            webView.getLayoutParams().height = dp(1);
            webView.requestLayout();
        }
        statusText.setText("已关闭网页模式");
        detailText.setText("页面已收起。需要排错时可再次点击“打开网页”。");
    }

    private String urlFor(String raw, String path) {
        String v = raw == null ? "" : raw.trim();
        if (v.length() == 0) v = DEFAULT_ADDRESS;
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "https://" + v;
        Uri uri = Uri.parse(v);
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        String authority = uri.getEncodedAuthority();
        return (authority == null || authority.length() == 0) ? DEFAULT_ADDRESS + path : scheme + "://" + authority + path;
    }

    private String cache(String url) {
        return url + (url.contains("?") ? "&" : "?") + "_pwts=" + System.currentTimeMillis();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void hideKeyboard() {
        try {
            View current = getCurrentFocus();
            if (current != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(current.getWindowToken(), 0);
                current.clearFocus();
            }
        } catch (Exception ignored) {
        }
    }

    private String decode(String raw) {
        String r = raw == null ? "" : raw;
        if (r.startsWith("\"") && r.endsWith("\"") && r.length() >= 2) {
            r = r.substring(1, r.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\u0026", "&")
                    .replace("\\/", "/");
        }
        return r;
    }

    private void injectCompactMode() {
        String js = "javascript:(function(){try{var css='html,body{max-width:100%!important;overflow-x:auto!important;}.main-left,.sidebar,aside,#mainmenu,#modemenu,.breadcrumb{display:none!important;}header,.navbar,.brand,.pull-left{display:none!important;}.main,.main-right,.container,.container-fluid,#maincontent{margin-left:0!important;left:0!important;width:100%!important;max-width:100%!important;padding-left:8px!important;padding-right:8px!important;}.cbi-map,.cbi-section,.cbi-section-node{max-width:100%!important;width:100%!important;}table,.table{width:100%!important;display:block!important;overflow-x:auto!important;}input,select,textarea,button,.btn,.cbi-button{min-height:40px!important;font-size:15px!important;}.cbi-value-title{min-width:92px!important;}';var style=document.getElementById('pw-acl-compact-style');if(!style){style=document.createElement('style');style.id='pw-acl-compact-style';document.head.appendChild(style);}style.innerHTML=css;document.title='PassWall 控制';}catch(e){}})()";
        webView.evaluateJavascript(js, null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (debugVisible) {
            closeDebugPage();
        } else if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE && prefs.getString(KEY_PASSWORD, "").length() > 0) {
            toggleSettings();
        } else if (webView != null && webView.getLayoutParams().height > dp(10) && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
