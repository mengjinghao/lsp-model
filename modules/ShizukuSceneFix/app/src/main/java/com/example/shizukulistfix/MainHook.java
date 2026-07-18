package com.example.shizukulistfix;

import android.content.Context;

import com.example.shizukulistfix.scene.ScenePermissionRequester;
import com.example.shizukulistfix.shizuku.ShizukuListInjector;
import com.example.shizukulistfix.shizuku.ShizukuVariantDetector;
import com.example.shizukulistfix.utils.LogX;
import com.example.shizukulistfix.utils.PackageHelper;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String SCENE_PACKAGE = "com.omarea.vtools";

    private static final Set<String> DEFAULT_SHIZUKU_PACKAGES = new HashSet<>();

    static {
        DEFAULT_SHIZUKU_PACKAGES.add("moe.shizuku.privileged.api");
        DEFAULT_SHIZUKU_PACKAGES.add("rikka.shizuku.manager");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName == null) {
            return;
        }

        LogX.i(">>> Module loaded in process: %s (package: %s)",
            lpparam.processName, lpparam.packageName);

        try {
            if (SCENE_PACKAGE.equals(lpparam.packageName)) {
                LogX.i(">>> Scene detected - activating Path A (permission requester)");
                ScenePermissionRequester.hook(lpparam);
                return;
            }

            if (isShizukuTarget(lpparam.packageName)) {
                LogX.i(">>> Shizuku detected - activating Path B (list injector)");
                ShizukuListInjector.hook(lpparam);
                return;
            }

            // Late detection: if we get a context later, try to detect more Shizuku variants
            tryLateDetection(lpparam);

        } catch (Throwable t) {
            LogX.e("Fatal error in handleLoadPackage", t);
        }
    }

    private boolean isShizukuTarget(String packageName) {
        if (DEFAULT_SHIZUKU_PACKAGES.contains(packageName)) {
            return true;
        }

        if (ShizukuVariantDetector.isShizukuProcess(packageName)) {
            return true;
        }

        return false;
    }

    private void tryLateDetection(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Context context = (Context) param.thisObject;
                            if (context == null) return;

                            String currentPkg = context.getPackageName();
                            if (currentPkg == null) return;

                            if (ShizukuVariantDetector.isShizukuProcess(currentPkg)
                                && !DEFAULT_SHIZUKU_PACKAGES.contains(currentPkg)) {

                                LogX.i("Late-detected Shizuku variant: " + currentPkg);
                                ShizukuListInjector.hook(lpparam);
                            }
                        } catch (Throwable t) {
                            // Silent
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // This is best-effort late detection
        }
    }
}
