package com.example.shizukulistfix.scene;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.example.shizukulistfix.utils.LogX;
import com.example.shizukulistfix.utils.PackageHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ScenePermissionRequester {

    private static final String SCENE_PACKAGE = "com.omarea.vtools";
    private static final String ACTION_REQUEST_PERMISSION = "moe.shizuku.manager.intent.action.REQUEST_PERMISSION";
    private static final String PERMISSION_ACTIVITY_ACTION = "moe.shizuku.manager.action.MANAGE_PERMISSION";

    private static final String[] SHIZUKU_MANAGER_PACKAGES = {
        "rikka.shizuku.manager",
        "moe.shizuku.manager",
        "moe.shizuku.privileged.api",
    };

    private static final String[] SHIZUKU_PROVIDER_CLASSES = {
        "rikka.shizuku.ShizukuProvider",
        "moe.shizuku.api.ShizukuProvider",
        "rikka.shizuku.api.ShizukuApi",
    };

    private static final String[] SHIZUKU_API_CLASSES = {
        "rikka.shizuku.Shizuku",
        "moe.shizuku.api.ShizukuApi",
        "rikka.shizuku.api.ShizukuClientHelper",
    };

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        LogX.i("=== Path A: Hooking Scene process ===");

        hookApplication(lpparam);
        hookMainActivity(lpparam);
    }

    private static void hookApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> applicationClass = XposedHelpers.findClass(
                "com.omarea.vtools.App",
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                applicationClass,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        LogX.i("Scene Application.onCreate() called, scheduling permission request...");
                        Context context = (Context) param.thisObject;
                        schedulePermissionRequest(context);
                    }
                }
            );
            LogX.i("  Hooked Application.onCreate()");
        } catch (XposedHelpers.ClassNotFoundError e) {
            LogX.i("  Application class not found, trying fallback...");
            hookApplicationFallback(lpparam);
        } catch (Throwable t) {
            LogX.e("  Failed to hook Application.onCreate()", t);
            hookApplicationFallback(lpparam);
        }
    }

    private static void hookApplicationFallback(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> appClass = XposedHelpers.findClass(
                "android.app.Application",
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                appClass,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Context context = (Context) param.thisObject;
                        if (context != null && SCENE_PACKAGE.equals(context.getPackageName())) {
                            LogX.i("Scene android.app.Application.onCreate()");
                            schedulePermissionRequest(context);
                        }
                    }
                }
            );
            LogX.i("  Hooked generic Application.onCreate()");
        } catch (Throwable t) {
            LogX.e("  Failed to hook generic Application.onCreate()", t);
        }
    }

    private static void hookMainActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String[] activityClasses = {
                "com.omarea.vtools.activities.MainActivity",
                "com.omarea.vtools.MainActivity",
                "com.omarea.vtools.ui.MainActivity",
            };

            for (String activityClassName : activityClasses) {
                try {
                    Class<?> activityClass = XposedHelpers.findClass(
                        activityClassName,
                        lpparam.classLoader
                    );
                    XposedHelpers.findAndHookMethod(
                        activityClass,
                        "onCreate",
                        android.os.Bundle.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                LogX.i("Scene MainActivity.onCreate() called");
                                Context context = (Activity) param.thisObject;
                                schedulePermissionRequest(context);
                            }
                        }
                    );
                    LogX.i("  Hooked MainActivity.onCreate(): " + activityClassName);
                    return;
                } catch (XposedHelpers.ClassNotFoundError e) {
                    LogX.d("  Activity not found: " + activityClassName);
                }
            }
            LogX.i("  No known MainActivity found in Scene");
        } catch (Throwable t) {
            LogX.e("  Failed to hook Scene activities", t);
        }
    }

    private static void schedulePermissionRequest(Context context) {
        new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }

            try {
                LogX.i("Executing Scene permission request...");
                requestShizukuPermissionViaIntent(context);
            } catch (Throwable t) {
                LogX.e("Failed to request Shizuku permission", t);
            }
        }).start();
    }

    private static void requestShizukuPermissionViaIntent(Context context) {
        for (String managerPkg : SHIZUKU_MANAGER_PACKAGES) {
            Intent intent = new Intent(ACTION_REQUEST_PERMISSION);
            intent.setPackage(managerPkg);
            intent.putExtra("package_name", SCENE_PACKAGE);
            intent.putExtra("uid", PackageHelper.getUid(context, SCENE_PACKAGE));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                context.startActivity(intent);
                LogX.i("  Sent REQUEST_PERMISSION intent to: " + managerPkg);
                return;
            } catch (Exception e) {
                LogX.d("  Failed to send intent to: " + managerPkg);
            }
        }

        tryRequestViaBroadcast(context);
    }

    private static void tryRequestViaBroadcast(Context context) {
        LogX.d("  Trying broadcast approach...");
        try {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(ACTION_REQUEST_PERMISSION);
            broadcastIntent.putExtra("package_name", SCENE_PACKAGE);
            broadcastIntent.putExtra("uid", PackageHelper.getUid(context, SCENE_PACKAGE));
            broadcastIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            context.sendBroadcast(broadcastIntent);
            LogX.i("  Sent broadcast REQUEST_PERMISSION");
        } catch (Throwable t) {
            LogX.e("  Broadcast approach failed", t);
        }

        tryRequestViaExplicitActivity(context);
    }

    private static void tryRequestViaExplicitActivity(Context context) {
        LogX.d("  Trying explicit activity approach...");
        try {
            for (String managerPkg : SHIZUKU_MANAGER_PACKAGES) {
                Intent intent = new Intent(PERMISSION_ACTIVITY_ACTION);
                intent.setPackage(managerPkg);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("package_name", SCENE_PACKAGE);
                intent.putExtra("uid", PackageHelper.getUid(context, SCENE_PACKAGE));

                try {
                    context.startActivity(intent);
                    LogX.i("  Opened permission activity in: " + managerPkg);
                    return;
                } catch (Exception e) {
                    LogX.d("  Failed to open activity in: " + managerPkg);
                }
            }
        } catch (Throwable t) {
            LogX.e("  Explicit activity approach failed", t);
        }
    }
}
