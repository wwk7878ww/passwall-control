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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String PREFS = "passwall_control_prefs";
    private static final String KEY_ADDRESS = "router_address";
    private static final String KEY_USERNAME = "router_username";
    private static final String KEY_PASSWORD = "router_password";
    private static final String DEFAULT_ADDRESS = "10.1.1.1";
    private static final String DEFAULT_USERNAME = "root";
    private static final String ACL_PATH = "/cgi-bin/luci/admin/services/passwall/acl";

    private static final int MODE_IDLE = 0;
    private static final int MODE_CHECK_STATE = 1;
    private static final int MODE_TOGGLE = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private EditText addressInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView statusText;
    private TextView detailText;
    private ProgressBar progressBar;
    private LinearLayout settingsPanel;
    private Button actionButton;
    private Button settingsButton;
    private SharedPreferences prefs;

    private int workMode = MODE_IDLE;
    private Boolean aclEnabled = null;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 253));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        LinearLayout mainArea = new LinearLayout(this);
        mainArea.setOrientation(LinearLayout.VERTICAL);
        mainArea.setGravity(Gravity.CENTER_HORIZONTAL);
        mainArea.setPadding(dp(22), dp(22), dp(22), dp(12));
        root.addView(mainArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        TextView title = new TextView(this);
        title.setText("passwall控制");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(27, 43, 68));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(8));
        mainArea.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("一键切换访问控制主开关，并保存应用");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(104, 116, 135));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(20));
        mainArea.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View topSpacer = new View(this);
        mainArea.addView(topSpacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        actionButton = new Button(this);
        actionButton.setTextSize(22);
        actionButton.setAllCaps(false);
        actionButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(118)
        );
        actionLp.setMargins(0, 0, 0, dp(18));
        mainArea.addView(actionButton, actionLp);
        updateActionButtonUnknown("检测状态中");

        statusText = new TextView(this);
        statusText.setText("正在初始化");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.rgb(40, 55, 80));
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 0, 0, dp(6));
        mainArea.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        detailText = new TextView(this);
        detailText.setText("首次使用请先在下方设置软路由地址、用户名和密码。");
        detailText.setTextSize(13);
        detailText.setTextColor(Color.rgb(110, 120, 135));
        detailText.setGravity(Gravity.CENTER);
        detailText.setPadding(0, 0, 0, dp(12));
        mainArea.addView(detailText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View bottomSpacer = new View(this);
        mainArea.addView(bottomSpacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        settingsButton = new Button(this);
        settingsButton.setText("设置");
        settingsButton.setAllCaps(false);
        settingsButton.setTextSize(15);
        LinearLayout.LayoutParams settingsBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        settingsBtnLp.setMargins(0, 0, 0, dp(8));
        mainArea.addView(settingsButton, settingsBtnLp);

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        settingsPanel.setBackgroundColor(Color.WHITE);
        mainArea.addView(settingsPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addressInput = makeInput("软路由地址，例如 10.1.1.1 或 http://10.1.1.1:10086", false);
        addressInput.setText(prefs.getString(KEY_ADDRESS, DEFAULT_ADDRESS));
        usernameInput = makeInput("LuCI 用户名，例如 root", false);
        usernameInput.setText(prefs.getString(KEY_USERNAME, DEFAULT_USERNAME));
        passwordInput = makeInput("LuCI 密码，仅保存在本机", true);
        passwordInput.setText(prefs.getString(KEY_PASSWORD, ""));

        settingsPanel.addView(makeLabel("软路由地址"));
        settingsPanel.addView(addressInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        settingsPanel.addView(makeLabel("用户名"));
        settingsPanel.addView(usernameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        settingsPanel.addView(makeLabel("密码"));
        settingsPanel.addView(passwordInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        LinearLayout settingButtons = new LinearLayout(this);
        settingButtons.setOrientation(LinearLayout.HORIZONTAL);
        settingButtons.setGravity(Gravity.CENTER_VERTICAL);

        Button saveButton = makeSmallButton("保存设置");
        Button refreshStateButton = makeSmallButton("刷新状态");
        Button clearCookieButton = makeSmallButton("清除登录");
        settingButtons.addView(saveButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingButtons.addView(refreshStateButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingButtons.addView(clearCookieButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingsPanel.addView(settingButtons);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        setContentView(root);
        configureWebView();

        actionButton.setOnClickListener(v -> startOneClickToggle());
        settingsButton.setOnClickListener(v -> toggleSettingsPanel());
        saveButton.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            checkCurrentState(true);
        });
        refreshStateButton.setOnClickListener(v -> {
            saveSettings();
            checkCurrentState(true);
        });
        clearCookieButton.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            aclEnabled = null;
            updateActionButtonUnknown("未登录");
            statusText.setText("已清除登录状态");
            detailText.setText("下次刷新状态或一键切换时，会在会话过期后重新登录。");
        });

        boolean hasPassword = prefs.getString(KEY_PASSWORD, "").length() > 0;
        settingsPanel.setVisibility(hasPassword ? View.GONE : View.VISIBLE);
        if (hasPassword) {
            handler.postDelayed(() -> checkCurrentState(false), 400);
        } else {
            statusText.setText("等待设置");
            updateActionButtonUnknown("一键切换");
        }
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
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        }
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
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " PassWallControl/1.2");

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
                if (workMode == MODE_CHECK_STATE) {
                    handler.postDelayed(() -> runPageScript(false), 500);
                } else if (workMode == MODE_TOGGLE) {
                    handler.postDelayed(() -> runPageScript(true), 500);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    failWork("页面打开失败，请检查软路由地址或手机网络。");
                }
            }
        });
    }

    private void toggleSettingsPanel() {
        settingsPanel.setVisibility(settingsPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void startOneClickToggle() {
        saveSettings();
        if (!hasRequiredSettings()) {
            return;
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "当前手机网络不可用", Toast.LENGTH_SHORT).show();
        }

        workMode = MODE_TOGGLE;
        actionButton.setEnabled(false);
        settingsButton.setEnabled(false);
        statusText.setText("正在检测登录状态...");
        detailText.setText("已有 LuCI 会话会直接操作；会话超时才会自动登录。操作内容：切换主开关并保存应用。");
        webView.getLayoutParams().height = dp(1);
        webView.requestLayout();
        webView.loadUrl(normalizeToAclUrl(addressInput.getText().toString().trim()));
    }

    private void checkCurrentState(boolean userTriggered) {
        saveSettings();
        if (!hasRequiredSettings()) {
            return;
        }
        workMode = MODE_CHECK_STATE;
        actionButton.setEnabled(false);
        settingsButton.setEnabled(false);
        statusText.setText("正在检测当前状态...");
        detailText.setText("如果登录会话仍有效，将直接读取状态；如果会话超时，会自动登录后再读取。");
        webView.getLayoutParams().height = dp(1);
        webView.requestLayout();
        webView.loadUrl(normalizeToAclUrl(addressInput.getText().toString().trim()));
        if (userTriggered) {
            Toast.makeText(this, "正在刷新 PassWall 访问控制状态", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasRequiredSettings() {
        String address = addressInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (address.length() == 0) {
            addressInput.setText(DEFAULT_ADDRESS);
        }
        if (username.length() == 0 || password.length() == 0) {
            settingsPanel.setVisibility(View.VISIBLE);
            Toast.makeText(this, "请先填写 LuCI 用户名和密码", Toast.LENGTH_SHORT).show();
            statusText.setText("等待设置");
            detailText.setText("填写并保存后，App 会先验证登录状态，再执行一键切换。");
            updateActionButtonUnknown("一键切换");
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

    private void runPageScript(boolean doToggle) {
        if (workMode == MODE_IDLE) {
            return;
        }

        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        String js = """
                (function(){
                  try{
                    var USER = __USER__;
                    var PASS = __PASS__;
                    var DO_TOGGLE = __DO_TOGGLE__;
                    function fire(el,type){try{el.dispatchEvent(new Event(type,{bubbles:true,cancelable:true}));}catch(e){}}
                    function text(el){return ((el && (el.innerText||el.textContent||el.value) || '')+'').trim();}
                    function findLogin(){
                      var passEl = document.querySelector('input[name="luci_password"],input#luci_password,input[name="password"],input[type="password"]');
                      if(!passEl){ return null; }
                      var root = passEl.form || document;
                      var userEl = root.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]');
                      if(!userEl){ userEl = document.querySelector('input[name="luci_username"],input#luci_username,input[name="username"],input[type="text"]'); }
                      if(!userEl){ return null; }
                      return {user:userEl, pass:passEl, form:(passEl.form||userEl.form)};
                    }
                    function submitLogin(login){
                      login.user.focus(); login.user.value = USER; fire(login.user,'input'); fire(login.user,'change');
                      login.pass.focus(); login.pass.value = PASS; fire(login.pass,'input'); fire(login.pass,'change');
                      var form = login.form;
                      var btn = null;
                      if(form){ btn = form.querySelector('button[type="submit"],input[type="submit"],button,input.cbi-button'); }
                      if(!btn){
                        btn = Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"]')).find(function(e){
                          var t = text(e); return /登录|登陆|Login|Sign in/i.test(t);
                        });
                      }
                      if(btn){ btn.click(); }
                      else if(form){ form.submit(); }
                      else { return false; }
                      return true;
                    }
                    function findAclSwitch(){
                      var candidates = Array.from(document.querySelectorAll('input[type="checkbox"]'));
                      return candidates.find(function(cb){
                        var id = cb.id || '';
                        var name = cb.name || '';
                        var box = cb.closest('.cbi-value,.cbi-section-node,div');
                        var body = text(box || cb);
                        return name.indexOf('acl_enable') >= 0 || id.indexOf('acl_enable') >= 0 || body.indexOf('主开关') >= 0;
                      });
                    }
                    function findApplyButton(){
                      var apply = document.querySelector('.cbi-button-apply,input[name="cbi.apply"],button[name="cbi.apply"],input[value*="保存并应用"],button[value*="保存并应用"]');
                      if(apply){ return apply; }
                      return Array.from(document.querySelectorAll('button,input[type="submit"],input[type="button"],a')).find(function(e){
                        var t = text(e); return t.indexOf('保存并应用') >= 0 || /Save\s*&\s*Apply/i.test(t) || /Save.*Apply/i.test(t);
                      });
                    }

                    var login = findLogin();
                    if(login){
                      if(submitLogin(login)){ return 'LOGIN_SUBMITTED'; }
                      return 'LOGIN_FORM_NO_SUBMIT';
                    }

                    if(location.href.indexOf('/cgi-bin/luci/admin/services/passwall/acl') < 0){
                      return 'NOT_ACL:' + location.href;
                    }

                    var main = findAclSwitch();
                    if(!main){ return 'ACL_SWITCH_NOT_FOUND'; }

                    if(!DO_TOGGLE){
                      return 'STATE:' + (main.checked ? 'ON' : 'OFF');
                    }

                    var before = main.checked;
                    try{ main.scrollIntoView({block:'center'}); }catch(e){}
                    var label = main.id ? document.querySelector('label[for="' + main.id + '"]') : null;
                    if(label){ label.click(); } else { main.click(); }
                    if(before === main.checked){ main.checked = !main.checked; }
                    fire(main,'input'); fire(main,'change');
                    var after = main.checked;

                    var apply = findApplyButton();
                    if(!apply){ return 'APPLY_NOT_FOUND:' + (before ? 'ON' : 'OFF') + '->' + (after ? 'ON' : 'OFF'); }
                    apply.click();
                    return 'TOGGLED:' + (before ? 'ON' : 'OFF') + '->' + (after ? 'ON' : 'OFF');
                  }catch(e){
                    return 'ERROR:' + e.message;
                  }
                })()
                """;

        js = js.replace("__USER__", JSONObject.quote(username))
                .replace("__PASS__", JSONObject.quote(password))
                .replace("__DO_TOGGLE__", doToggle ? "true" : "false");

        webView.evaluateJavascript(js, result -> handlePageScriptResult(result == null ? "" : result));
    }

    private void handlePageScriptResult(String rawResult) {
        if (workMode == MODE_IDLE) {
            return;
        }

        String result = decodeJsString(rawResult);

        if (result.startsWith("LOGIN_SUBMITTED")) {
            if (workMode == MODE_TOGGLE) {
                statusText.setText("登录会话已超时，正在自动登录...");
                detailText.setText("登录完成后会继续切换主开关并保存应用。");
            } else {
                statusText.setText("登录会话已超时，正在自动登录...");
                detailText.setText("登录完成后会继续读取当前主开关状态。");
            }
            return;
        }

        if (result.startsWith("NOT_ACL:")) {
            statusText.setText("正在进入 PassWall 访问控制页...");
            handler.postDelayed(() -> webView.loadUrl(normalizeToAclUrl(addressInput.getText().toString().trim())), 700);
            return;
        }

        if (result.startsWith("STATE:")) {
            boolean enabled = result.contains("ON");
            aclEnabled = enabled;
            updateActionButtonState(enabled);
            finishWork(enabled ? "当前状态：访问控制已开启" : "当前状态：访问控制已关闭");
            detailText.setText("状态已刷新。点击中间按钮可切换状态并自动保存应用。");
            return;
        }

        if (result.startsWith("TOGGLED:")) {
            boolean enabled = result.endsWith("->ON");
            aclEnabled = enabled;
            updateActionButtonState(enabled);
            finishWork("执行完成");
            detailText.setText((enabled ? "已切换为：开启" : "已切换为：关闭") + "，已点击保存并应用。建议等待几秒让 PassWall 生效。");
            return;
        }

        if (result.startsWith("APPLY_NOT_FOUND:")) {
            failWork("已经切换主开关，但没有找到“保存并应用”按钮。请点“刷新状态”后确认页面结构。");
            return;
        }

        if (result.startsWith("LOGIN_FORM_NO_SUBMIT")) {
            failWork("已填写登录信息，但没有找到登录按钮。请确认 LuCI 登录页是否正常。");
            return;
        }

        if (result.startsWith("ACL_SWITCH_NOT_FOUND")) {
            failWork("没有找到访问控制主开关。请确认当前 PassWall 访问控制页面可正常打开。");
            return;
        }

        if (result.startsWith("ERROR:")) {
            failWork("自动操作失败：" + result);
            return;
        }

        failWork("自动操作失败，返回结果：" + result);
    }

    private String decodeJsString(String rawResult) {
        String result = rawResult == null ? "" : rawResult;
        if (result.startsWith("\"") && result.endsWith("\"") && result.length() >= 2) {
            result = result.substring(1, result.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\u0026", "&");
        }
        return result;
    }

    private void finishWork(String message) {
        workMode = MODE_IDLE;
        actionButton.setEnabled(true);
        settingsButton.setEnabled(true);
        statusText.setText(message);
        CookieManager.getInstance().flush();
    }

    private void failWork(String message) {
        workMode = MODE_IDLE;
        actionButton.setEnabled(true);
        settingsButton.setEnabled(true);
        statusText.setText("执行失败");
        detailText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (aclEnabled == null) {
            updateActionButtonUnknown("一键切换");
        } else {
            updateActionButtonState(aclEnabled);
        }
    }

    private void updateActionButtonState(boolean enabled) {
        if (enabled) {
            setActionButtonStyle(Color.rgb(23, 166, 92));
            actionButton.setText("当前：开启\n点击关闭");
        } else {
            setActionButtonStyle(Color.rgb(218, 62, 62));
            actionButton.setText("当前：关闭\n点击开启");
        }
    }

    private void updateActionButtonUnknown(String text) {
        setActionButtonStyle(Color.rgb(107, 119, 140));
        actionButton.setText(text);
    }

    private void setActionButtonStyle(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(18));
        actionButton.setBackground(bg);
        actionButton.setTextColor(Color.WHITE);
    }

    private String normalizeToAclUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0) {
            value = DEFAULT_ADDRESS;
        }
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
        if (path != null && path.contains(ACL_PATH)) {
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
        if (webView != null && webView.getLayoutParams().height > dp(10) && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
