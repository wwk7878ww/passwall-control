# passwall控制 Android 项目

这是一个只打开 PassWall「访问控制」页面的 WebView APK 项目。

## 默认配置

- App 名称：passwall控制
- 默认地址：10.1.1.1
- 实际打开路径：http://10.1.1.1/cgi-bin/luci/admin/services/passwall/acl
- 最低 Android 版本：Android 10 / API 29
- 支持 HTTP 明文访问
- 支持 Cookie / DOM Storage，用于保存 LuCI 登录状态

## 功能

1. 打开 PassWall 访问控制 ACL 页面。
2. 自动把输入的 `10.1.1.1` 转换为：
   `http://10.1.1.1/cgi-bin/luci/admin/services/passwall/acl`
3. 可输入带端口的地址，例如：
   `http://10.1.1.1:10086`
4. 记住上次输入的软路由地址。
5. WebView 保留 Cookie，登录一次后通常可保持登录状态。
6. 页面加载后自动注入简化样式，隐藏 LuCI 左侧菜单和部分顶部元素，便于手机查看访问控制页面。

## 打包 APK 方法

1. 用 Android Studio 打开本项目目录。
2. 等待 Gradle Sync 完成。
3. 点击：`Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`。
4. 生成的调试 APK 一般在：
   `app/build/outputs/apk/debug/app-debug.apk`

也可以在项目根目录执行：

```bash
./gradlew assembleDebug
```

如果没有 `gradlew`，可以用 Android Studio 打包，或在本机安装 Gradle 后执行：

```bash
gradle assembleDebug
```

## 安全提醒

- 不建议把 OpenWrt / LuCI 管理页面暴露到公网。
- 建议只在内网、VPN 或可信网络下使用。
- 本 App 不硬编码保存路由器账号密码，只依赖 WebView Cookie 保存登录状态。
