package com.kumu7y.biligate;

import android.content.Context;
import android.content.Intent;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class MainHook extends XposedModule {

    private static final String TAG = "BiliGate";
    private static final Pattern MATCH = Pattern.compile("(bilibili\\.com|b23\\.tv)", Pattern.CASE_INSENSITIVE);
    private static final String FROM_PKG = "tv.danmaku.bili";
    private static final String TO_PKG = "com.bilibili.app.in";

    public MainHook() {
        super();
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(3, TAG, "loaded into " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        switch (pkg) {
            case "com.oplus.metis":
            case "com.coloros.colordirectservice":
                hookMetis(param);
                break;
            case "com.tencent.mobileqq":
                hookQQ(param);
                break;
            default:
                return;
        }
        log(3, TAG, "hooking " + pkg);
    }

    /* ---------------- ColorOS 剪贴板推荐链路 ---------------- */

    private void hookMetis(PackageLoadedParam param) {
        ClassLoader cl = param.getDefaultClassLoader();
        try {
            Class<?> ad = cl.loadClass("com.oplus.textintent.distribution.bean.ActionData");
            Method parse = ad.getDeclaredMethod("parse", Context.class);
            parse.setAccessible(true);
            Method getText = ad.getMethod("getText");
            Method setText = ad.getMethod("setText", String.class);
            hook(parse).intercept(chain -> {
                Object self = chain.getThisObject();
                String text = (String) getText.invoke(self);
                if (text != null && text.contains(FROM_PKG) && MATCH.matcher(text).find()) {
                    setText.invoke(self, text.replace(FROM_PKG, TO_PKG));
                    log(3, TAG, "metis: platform rewritten -> " + TO_PKG);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(6, TAG, "hookActionData failed: " + t);
        }
        try {
            Class<?> pu = cl.loadClass("com.oplus.textintent.common.utils.PackageUtil");
            Method isInstalled = pu.getDeclaredMethod("isInstalled", Context.class, String.class);
            isInstalled.setAccessible(true);
            hook(isInstalled).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                if (FROM_PKG.equals(args[1])) {
                    args[1] = TO_PKG;
                    log(3, TAG, "metis: isInstalled redirected -> " + TO_PKG);
                }
                return chain.proceed(args);
            });
        } catch (Throwable t) {
            log(6, TAG, "hookPackageUtil failed: " + t);
        }
    }

    /* ---------------- QQ 小程序"打开App"链路 ----------------
     * 引擎是动态插件,不 hook 插件代码,掐两个汇聚点:
     * 1) PackageManager 安装查询:tv.danmaku.bili -> com.bilibili.app.in
     *    (查询通过 → 跳过"即将下载"弹窗)
     * 2) Intent.setPackage/setComponent:绑国内版包名的拉起 Intent 改绑国际版
     *    (国际版类名与国内版同源,IntentHandlerActivity 等同名存在)
     */
    private void hookQQ(PackageLoadedParam param) {
        ClassLoader cl = param.getDefaultClassLoader();
        try {
            Class<?> apm = cl.loadClass("android.app.ApplicationPackageManager");
            for (String m : new String[]{"getApplicationInfo", "getPackageInfo"}) {
                for (Method method : apm.getMethods()) {
                    if (!m.equals(method.getName())) continue;
                    Class<?>[] ps = method.getParameterTypes();
                    if (ps.length == 0 || !ps[0].equals(String.class)) continue;
                    Method mm = method;
                    hook(mm).intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        if (FROM_PKG.equals(args[0])) {
                            args[0] = TO_PKG;
                            log(3, TAG, "qq: pm query redirected -> " + TO_PKG);
                        }
                        return chain.proceed(args);
                    });
                }
            }
            log(3, TAG, "qq: pm hooked");
        } catch (Throwable t) {
            log(6, TAG, "hookQQ pm failed: " + t);
        }
        try {
            hook(Intent.class.getMethod("setPackage", String.class)).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                if (FROM_PKG.equals(args[0])) {
                    args[0] = TO_PKG;
                    log(3, TAG, "qq: intent package redirected -> " + TO_PKG);
                }
                return chain.proceed(args);
            });
            hook(Intent.class.getMethod("setComponent", android.content.ComponentName.class)).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                Object cn = args[0];
                if (cn != null && FROM_PKG.equals(((android.content.ComponentName) cn).getPackageName())) {
                    args[0] = new android.content.ComponentName(TO_PKG, ((android.content.ComponentName) cn).getClassName());
                    log(3, TAG, "qq: intent component redirected -> " + TO_PKG);
                }
                return chain.proceed(args);
            });
            log(3, TAG, "qq: intent hooked");
        } catch (Throwable t) {
            log(6, TAG, "hookQQ intent failed: " + t);
        }
    }
}
