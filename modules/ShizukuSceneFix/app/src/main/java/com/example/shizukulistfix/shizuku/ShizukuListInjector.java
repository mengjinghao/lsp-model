package com.example.shizukulistfix.shizuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import com.example.shizukulistfix.utils.LogX;
import com.example.shizukulistfix.utils.PackageHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ShizukuListInjector {

    private static final String SCENE_PACKAGE = "com.omarea.vtools";
    private static boolean sSceneInjected = false;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        LogX.i("=== Path B: Hooking Shizuku process: %s ===", lpparam.processName);

        hookPackageManager(lpparam);
        hookRecyclerViewAdapter(lpparam);
        hookShizukuApiMethods(lpparam);
        hookPermissionManager(lpparam);
        hookListAdapterSetData(lpparam);
    }

    // ============ Strategy 1: Hook PackageManager ============
    private static void hookPackageManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pmClass = XposedHelpers.findClass(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader
            );

            hookGetInstalledApplications(pmClass, lpparam);
            hookGetInstalledPackages(pmClass, lpparam);
            hookQueryIntentActivities(pmClass, lpparam);

        } catch (XposedHelpers.ClassNotFoundError e) {
            LogX.e("  ApplicationPackageManager not found");
        } catch (Throwable t) {
            LogX.e("  Error in hookPackageManager", t);
        }
    }

    private static void hookGetInstalledApplications(Class<?> pmClass, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Method method = pmClass.getDeclaredMethod("getInstalledApplications", int.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        @SuppressWarnings("unchecked")
                        List<ApplicationInfo> result = (List<ApplicationInfo>) param.getResult();
                        if (result == null || result.isEmpty()) {
                            return;
                        }

                        boolean found = false;
                        for (ApplicationInfo info : result) {
                            if (SCENE_PACKAGE.equals(info.packageName)) {
                                found = true;
                                break;
                            }
                        }

                        if (!found && !sSceneInjected) {
                            Context ctx = getContext(lpparam);
                            ApplicationInfo sceneInfo = PackageHelper.getApplicationInfo(ctx, SCENE_PACKAGE);
                            if (sceneInfo != null) {
                                List<ApplicationInfo> newList = new ArrayList<>(result);
                                newList.add(sceneInfo);
                                param.setResult(newList);
                                sSceneInjected = true;
                                LogX.i("  [PM] Injected Scene into getInstalledApplications()");
                            }
                        }
                    } catch (Throwable t) {
                        LogX.e("  [PM] Error injecting into getInstalledApplications", t);
                    }
                }
            });
            LogX.i("  Hooked getInstalledApplications()");
        } catch (NoSuchMethodException e) {
            LogX.d("  getInstalledApplications not found");
        } catch (Throwable t) {
            LogX.e("  Error hooking getInstalledApplications", t);
        }
    }

    private static void hookGetInstalledPackages(Class<?> pmClass, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Method method = pmClass.getDeclaredMethod("getInstalledPackages", int.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        @SuppressWarnings("unchecked")
                        List<PackageInfo> result = (List<PackageInfo>) param.getResult();
                        if (result == null || result.isEmpty()) {
                            return;
                        }

                        boolean found = false;
                        for (PackageInfo info : result) {
                            if (SCENE_PACKAGE.equals(info.packageName)) {
                                found = true;
                                break;
                            }
                        }

                        if (!found && !sSceneInjected) {
                            Context ctx = getContext(lpparam);
                            PackageInfo scenePkg = PackageHelper.getPackageInfo(ctx, SCENE_PACKAGE);
                            if (scenePkg != null) {
                                List<PackageInfo> newList = new ArrayList<>(result);
                                newList.add(scenePkg);
                                param.setResult(newList);
                                sSceneInjected = true;
                                LogX.i("  [PM] Injected Scene into getInstalledPackages()");
                            }
                        }
                    } catch (Throwable t) {
                        LogX.e("  [PM] Error injecting into getInstalledPackages", t);
                    }
                }
            });
            LogX.i("  Hooked getInstalledPackages()");
        } catch (NoSuchMethodException e) {
            LogX.d("  getInstalledPackages not found");
        } catch (Throwable t) {
            LogX.e("  Error hooking getInstalledPackages", t);
        }
    }

    private static void hookQueryIntentActivities(Class<?> pmClass, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Method method = pmClass.getDeclaredMethod("queryIntentActivities",
                android.content.Intent.class, int.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        @SuppressWarnings("unchecked")
                        List<ResolveInfo> result = (List<ResolveInfo>) param.getResult();
                        if (result == null || result.isEmpty()) {
                            return;
                        }

                        boolean hasShizukuActivity = false;
                        for (ResolveInfo info : result) {
                            if (info.activityInfo != null
                                && info.activityInfo.packageName != null
                                && info.activityInfo.packageName.toLowerCase().contains("shizuku")) {
                                hasShizukuActivity = true;
                                break;
                            }
                        }

                        if (hasShizukuActivity && !sSceneInjected) {
                            LogX.d("  [PM] queryIntentActivities returned Shizuku-related results, checking for Scene...");
                        }
                    } catch (Throwable t) {
                        // Silent fail
                    }
                }
            });
            LogX.i("  Hooked queryIntentActivities()");
        } catch (NoSuchMethodException e) {
            LogX.d("  queryIntentActivities not found");
        } catch (Throwable t) {
            LogX.e("  Error hooking queryIntentActivities", t);
        }
    }

    // ============ Strategy 2: Hook RecyclerView Adapter ============
    private static void hookRecyclerViewAdapter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> adapterClass = XposedHelpers.findClass(
                "androidx.recyclerview.widget.RecyclerView$Adapter",
                lpparam.classLoader
            );
            LogX.i("  Found RecyclerView.Adapter, hooking getItemCount...");

            XposedHelpers.findAndHookMethod(adapterClass, "getItemCount",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int originalCount = (int) param.getResult();
                        if (originalCount >= 0 && !sSceneInjected) {
                            // Silently try to inject Scene info
                            // This is a UI-level indicator, actual injection happens elsewhere
                        }
                    }
                });
        } catch (XposedHelpers.ClassNotFoundError e) {
            LogX.d("  RecyclerView.Adapter not found");
        } catch (Throwable t) {
            LogX.e("  Error hooking RecyclerView adapter", t);
        }
    }

    // ============ Strategy 3: Hook Shizuku-specific API methods ============
    private static void hookShizukuApiMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] possibleClasses = {
            "rikka.shizuku.Shizuku",
            "rikka.shizuku.ShizukuProvider",
            "moe.shizuku.api.ShizukuApi",
            "moe.shizuku.server.api.ServerApi",
            "rikka.shizuku.server.api.ServerApi",
            "moe.shizuku.manager.ShizukuManager",
        };

        for (String className : possibleClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                hookShizukuClassMethods(clazz, className, lpparam);
            } catch (XposedHelpers.ClassNotFoundError e) {
                LogX.d("  Shizuku class not found: " + className);
            } catch (Throwable t) {
                LogX.d("  Error processing Shizuku class: " + className);
            }
        }
    }

    private static void hookShizukuClassMethods(Class<?> clazz, String className,
                                                 XC_LoadPackage.LoadPackageParam lpparam) {
        LogX.i("  Processing Shizuku class: " + className);

        String[] possibleMethods = {
            "getApplication",
            "getApplications",
            "getInstalledApps",
            "getPackageList",
            "getAppList",
            "queryApps",
            "getPermissionRequests",
            "getPendingRequests",
            "getRequestedApps",
        };

        for (String methodName : possibleMethods) {
            tryHookingMethod(clazz, methodName, lpparam);
        }
    }

    private static void tryHookingMethod(Class<?> clazz, String methodName,
                                          XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    XposedBridge.hookMethod(method, createListInjectionHook(lpparam, methodName));
                    LogX.i("    Hooked: " + clazz.getName() + "." + methodName + "()");
                    return;
                }
            }
            LogX.d("    Method not found: " + methodName);
        } catch (Throwable t) {
            LogX.d("    Error hooking method: " + methodName);
        }
    }

    private static XC_MethodHook createListInjectionHook(XC_LoadPackage.LoadPackageParam lpparam,
                                                          String methodName) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object result = param.getResult();
                    if (result == null) return;

                    if (result instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) result;
                        injectSceneIntoList(list, lpparam, methodName);
                    } else if (result.getClass().isArray()) {
                        List<Object> list = new ArrayList<>(Arrays.asList((Object[]) result));
                        if (injectSceneIntoList(list, lpparam, methodName)) {
                            param.setResult(list.toArray());
                        }
                    }
                } catch (Throwable t) {
                    LogX.e("    Error in list injection hook", t);
                }
            }
        };
    }

    private static boolean injectSceneIntoList(List<Object> list,
                                                XC_LoadPackage.LoadPackageParam lpparam,
                                                String methodName) {
        try {
            for (Object item : list) {
                if (item == null) continue;
                String pkg = extractPackageName(item);
                if (SCENE_PACKAGE.equals(pkg)) {
                    return false; // Already in list
                }
            }

            Context ctx = getContext(lpparam);
            ApplicationInfo sceneInfo = PackageHelper.getApplicationInfo(ctx, SCENE_PACKAGE);
            if (sceneInfo != null) {
                list.add(0, sceneInfo);
                sSceneInjected = true;
                LogX.i("    Injected Scene into list from method: " + methodName);
                return true;
            }
        } catch (Throwable t) {
            LogX.e("    Error injecting Scene", t);
        }
        return false;
    }

    private static String extractPackageName(Object obj) {
        try {
            if (obj instanceof String) {
                return (String) obj;
            }
            if (obj instanceof ApplicationInfo) {
                return ((ApplicationInfo) obj).packageName;
            }
            if (obj instanceof PackageInfo) {
                return ((PackageInfo) obj).packageName;
            }
            if (obj instanceof ResolveInfo) {
                ResolveInfo ri = (ResolveInfo) obj;
                if (ri.activityInfo != null) return ri.activityInfo.packageName;
                if (ri.serviceInfo != null) return ri.serviceInfo.packageName;
                if (ri.providerInfo != null) return ri.providerInfo.packageName;
            }
            // Try reflection for generic objects
            try {
                Method getPkg = obj.getClass().getMethod("getPackageName");
                return (String) getPkg.invoke(obj);
            } catch (Exception ignored) {
            }
            try {
                java.lang.reflect.Field pkgField = obj.getClass().getDeclaredField("packageName");
                pkgField.setAccessible(true);
                Object val = pkgField.get(obj);
                return val != null ? val.toString() : null;
            } catch (Exception ignored) {
            }
        } catch (Throwable t) {
            // Silent fail
        }
        return null;
    }

    // ============ Strategy 4: Hook permission manager ============
    private static void hookPermissionManager(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] possibleClasses = {
            "rikka.shizuku.manager.permission.PermissionManager",
            "moe.shizuku.manager.permission.PermissionManager",
            "rikka.shizuku.manager.permission.AppPermission",
            "moe.shizuku.manager.PermissionHelper",
            "rikka.shizuku.manager.PermissionHelper",
        };

        for (String className : possibleClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                LogX.i("  Found permission class: " + className);

                // Try to hook any method that returns a list
                for (Method method : clazz.getDeclaredMethods()) {
                    Class<?> returnType = method.getReturnType();
                    if (List.class.isAssignableFrom(returnType)
                        || returnType.isArray()
                        || returnType == java.util.Map.class
                        || returnType == java.util.Set.class) {

                        try {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    try {
                                        injectSceneIntoReturn(param);
                                    } catch (Throwable t) {
                                        // Silent
                                    }
                                }
                            });
                            LogX.i("    Hooked: " + method.getName());
                        } catch (Throwable t) {
                            // Method can't be hooked
                        }
                    }
                }
            } catch (XposedHelpers.ClassNotFoundError e) {
                LogX.d("  Permission class not found: " + className);
            } catch (Throwable t) {
                LogX.d("  Error with permission class: " + className);
            }
        }
    }

    private static void injectSceneIntoReturn(MethodHookParam param) {
        Object result = param.getResult();
        if (result == null) return;

        try {
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) result;
                injectSceneStringIntoList(list);
            } else if (result instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) result;
                if (!map.containsKey(SCENE_PACKAGE)) {
                    map.put(SCENE_PACKAGE, SCENE_PACKAGE);
                    sSceneInjected = true;
                    LogX.i("    Injected Scene into Map result");
                }
            } else if (result instanceof java.util.Set) {
                @SuppressWarnings("unchecked")
                java.util.Set<Object> set = (java.util.Set<Object>) result;
                if (!set.contains(SCENE_PACKAGE)) {
                    set.add(SCENE_PACKAGE);
                    sSceneInjected = true;
                    LogX.i("    Injected Scene into Set result");
                }
            }
        } catch (Throwable t) {
            // Silent
        }
    }

    private static void injectSceneStringIntoList(List<Object> list) {
        for (Object item : list) {
            if (SCENE_PACKAGE.equals(String.valueOf(item))) {
                return;
            }
        }
        list.add(SCENE_PACKAGE);
        sSceneInjected = true;
        LogX.i("    Injected Scene string into list");
    }

    // ============ Strategy 5: Hook adapter data setting ============
    private static void hookListAdapterSetData(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] adapterMethods = {
            "submitList",
            "setItems",
            "setData",
            "setList",
            "replaceAll",
            "addAll",
            "updateData",
            "setNewData",
            "notifyDataSetChanged",
            "setApps",
            "setApplications",
        };

        // Hook ListAdapter.submitList() (ListAdapter from RecyclerView)
        try {
            Class<?> listAdapter = XposedHelpers.findClass(
                "androidx.recyclerview.widget.ListAdapter",
                lpparam.classLoader
            );
            try {
                Method submitList = listAdapter.getDeclaredMethod("submitList", java.util.List.class);
                XposedBridge.hookMethod(submitList, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            @SuppressWarnings("unchecked")
                            List<Object> list = (List<Object>) param.args[0];
                            if (list != null && !list.isEmpty() && !sSceneInjected) {
                                ensureSceneInAdapterData(list, lpparam);
                            }
                        } catch (Throwable t) {
                            // Silent
                        }
                    }
                });
                LogX.i("  Hooked ListAdapter.submitList()");
            } catch (NoSuchMethodException e) {
                LogX.d("  ListAdapter.submitList not found");
            }
        } catch (XposedHelpers.ClassNotFoundError e) {
            LogX.d("  ListAdapter not found");
        }

        // Hook ArrayAdapter for legacy ListView
        try {
            Class<?> arrayAdapter = XposedHelpers.findClass(
                "android.widget.ArrayAdapter",
                lpparam.classLoader
            );

            String[] arrayAdapterHookMethods = {"addAll", "add", "clear"};
            for (String methodName : arrayAdapterHookMethods) {
                try {
                    for (Method m : arrayAdapter.getDeclaredMethods()) {
                        if (m.getName().equals(methodName)) {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    // Indirect injection through other hooks
                                }
                            });
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (XposedHelpers.ClassNotFoundError e) {
            LogX.d("  ArrayAdapter not found");
        }
    }

    private static void ensureSceneInAdapterData(List<Object> list, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (list.isEmpty()) return;

            Context ctx = getContext(lpparam);
            ApplicationInfo sceneInfo = PackageHelper.getApplicationInfo(ctx, SCENE_PACKAGE);
            if (sceneInfo == null) return;

            boolean found = false;
            for (Object item : list) {
                String pkg = extractPackageName(item);
                if (SCENE_PACKAGE.equals(pkg)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                list.add(0, sceneInfo);
                sSceneInjected = true;
                LogX.i("  [Adapter] Injected Scene into adapter data list");
            }
        } catch (Throwable t) {
            LogX.e("  [Adapter] Error ensuring Scene in data", t);
        }
    }

    // ============ Utility ============
    private static Context getContext(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass(
                "android.app.ActivityThread",
                lpparam.classLoader
            );
            Object currentActivityThread = XposedHelpers.callStaticMethod(
                activityThreadClass,
                "currentActivityThread"
            );
            return (Context) XposedHelpers.callMethod(
                currentActivityThread,
                "getApplication"
            );
        } catch (Throwable t) {
            LogX.e("  Failed to get Context via ActivityThread", t);
        }
        return null;
    }
}
