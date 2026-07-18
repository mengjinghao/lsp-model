package com.example.shizukulistfix.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.example.shizukulistfix.utils.LogX;

import java.util.ArrayList;
import java.util.List;

public class PackageHelper {

    private PackageHelper() {
    }

    public static String getPackageName(Context context) {
        if (context != null) {
            return context.getPackageName();
        }
        return "";
    }

    public static int getUid(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            if (appInfo != null) {
                return appInfo.uid;
            }
        } catch (PackageManager.NameNotFoundException e) {
            LogX.e("Failed to get UID for: " + packageName, e);
        } catch (Throwable t) {
            LogX.e("Failed to get UID for: " + packageName, t);
        }
        return -1;
    }

    public static ApplicationInfo getApplicationInfo(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            LogX.e("App not found: " + packageName);
        } catch (Throwable t) {
            LogX.e("Failed to get ApplicationInfo for: " + packageName, t);
        }
        return null;
    }

    public static PackageInfo getPackageInfo(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            LogX.e("App not found: " + packageName);
        } catch (Throwable t) {
            LogX.e("Failed to get PackageInfo for: " + packageName, t);
        }
        return null;
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable t) {
            LogX.e("Failed to check package: " + packageName, t);
            return false;
        }
    }

    public static List<ApplicationInfo> getAllInstalledApps(Context context) {
        List<ApplicationInfo> result = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            result.addAll(pm.getInstalledApplications(0));
        } catch (Throwable t) {
            LogX.e("Failed to get installed apps", t);
        }
        return result;
    }
}
