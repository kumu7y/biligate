# Bilibili重定向 — 哔哩哔哩国际版改道器(ColorOS 剪贴板卡片 + QQ 小程序)

把 ColorOS「复制链接弹推荐」卡片从国内版 bilibili(`tv.danmaku.bili`)改道到国际版(`com.bilibili.app.in`)的 LSPosed 模块(现代 API 102)。

- 设备验证:一加 / ColorOS 16.1 · KernelSU + LSPosed(fork)
- 构建方式:纯 javac/d8/aapt2/apksigner,无 Gradle,`bash build.sh` 一键构建

## 原理(ColorOS 16.1 实测)

```
复制文本 → com.coloros.sceneservice 监听剪贴板(500ms 去抖 + isWriteByUser)
        → com.coloros.colordirectservice (TextIntentEntranceProvider)
        → com.oplus.metis:text_intent 真正执行
           云端 POST iwisdom.apps.coloros.com 返回推荐 platform.appPackage
           DeepLinkData.parseIntent → PackageUtil.isInstalled(pkg) 本地查包
           SeedlingSender.send: isInstalled ? "打开"(bt_open) : "去安装"(bt_text_install)
           CardManager.updateDataCM → 渲染横幅
```

**关键事实(均为反编译/实测确认):**

1. 云端**不知道**装机状态,只下发推荐包名;"去安装/打开"按钮由本地 `PackageUtil.isInstalled` + `SeedlingSender` 字符串资源决定。
2. 国际版 `com.bilibili.app.in` 注册了与国内版相同的 `bilibili://` scheme 与 `b23.tv`/`*.bilibili.com` https 域名——**未装国内版时系统自动把 bilibili:// 解析到国际版**。
3. 文档误导修正:LSPosed(fork)模块识别**需要** manifest 里的 `xposedmodule=true` + `xposedminversion=100` meta-data(正常工作的现代 API 模块均带);同时需要 `META-INF/xposed/{module.prop,java_init.list,scope.list}` 与 DB(modules/modules_state/scope 三表)注册。

## Hook 点(共 2 个,从简)

| # | 目标 | 作用 |
|---|------|------|
| 1 | `com.oplus.textintent.distribution.bean.ActionData.parse(Context)` 前置 | 云端 JSON 里 `tv.danmaku.bili` → `com.bilibili.app.in`,决定点击跳转目标(点击走 `bilibili://`,国际版直接响应) |
| 2 | `com.oplus.textintent.common.utils.PackageUtil.isInstalled(Context, String)` 入参替换 | 查国内版安装状态时改查国际版 → 按钮显示"打开"、卡片按已安装渲染 |

## 部署

```bash
adb install build/BiliGate.apk
# LSPosed DB 注册(/data/adb/lspd/config/modules_config.db):
#   modules:      (com.kumu7y.biligate, <pm path>)
#   modules_state:(com.kumu7y.biligate, 0, enabled=1, 0)
#   scope:        com.oplus.metis + com.coloros.colordirectservice
# 注意:apk_path 必须与 pm path 一致;替换 db 前先删 -wal/-shm 或先 checkpoint
# 生效:killall com.oplus.metis com.oplus.metis:text_intent(无需重启手机)
```

验证:`logcat | grep ClipDirect`,复制 b23.tv 链接应看到
`platform rewritten -> com.bilibili.app.in` 与 `isInstalled query redirected`。

## 文件说明

- `src/com/kumu7y/biligate/MainHook.java` — 模块源码:met.is 双 hook + QQ 双 hook
- `build.sh` — 一键构建(路径按本机 JDK21/SDK35 写死,需按需修改)
- `module.prop` / `java_init.list` / `scope.list` / `AndroidManifest.xml` — 模块元数据
- `api.aar` / `interface.aar` — libxposed 102.0.0 编译期 API
- `build/BilibiliRedirect.apk` — 已构建产物

## QQ 小程序链路(v2.0.0)

QQ 小程序"打开App"会按配置包名查安装,查不到就弹"即将下载"。引擎是动态插件,
无法静态定位,因此注入 `com.tencent.mobileqq` 进程掐两个与实现无关的汇聚点:

1. `ApplicationPackageManager.getApplicationInfo/getPackageInfo`:查 `tv.danmaku.bili` 时改查国际版 → 判定"已安装",不弹下载框
2. `Intent.setPackage/setComponent`:绑国内版包名的拉起 Intent 改绑国际版(两家同源,Activity 类名一致)

日志:`logcat | grep BiliRedirect`(qq: pm query redirected / intent package redirected)

## 卸载

LSPosed 关闭模块 → 卸载 `com.kumu7y.biligate` → 重启 metis 进程即可,无持久化修改。
