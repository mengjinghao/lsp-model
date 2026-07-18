package com.example.shizukulistfix.shizuku;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.example.shizukulistfix.utils.LogX;
import com.example.shizukulistfix.utils.PackageHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShizukuVariantDetector {

    private static final String TAG = "ShizukuVariantDetector";

    private static final String[] KNOWN_SHIZUKU_PACKAGES = {
        "moe.shizuku.privileged.api",
        "rikka.shizuku.manager",
        "moe.shizuku.privileged.api.plus",
        "com.shizuku.plus",
        "stellar.shizuku.api",
        "com.stellar.shizuku",
        "com.shizuku",
        "rikka.shizuku",
        "moe.shizuku",
    };

    private static final String[] SHIZUKU_NAME_KEYWORDS = {
        "shizuku", "Shizuku", "SHIZUKU"
    };

    private ShizukuVariantDetector() {
    }

    public static Set<String> detectShizukuVariants(Context context) {
        Set<String> detected = new HashSet<>();
        LogX.i("Detecting Shizuku variants...");

        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);

            for (ApplicationInfo appInfo : apps) {
                if (appInfo == null || appInfo.packageName == null) {
                    continue;
                }

                String pkgName = appInfo.packageName;

                if (isKnownShizukuPackage(pkgName)) {
                    detected.add(pkgName);
                    LogX.i("  [Known] Detected Shizuku package: " + pkgName);
                    continue;
                }

                if (isShizukuByName(pkgName)) {
                    LogX.d("  [Name match] Checking package: " + pkgName);
                    if (hasShizukuService(context, appInfo)) {
                        detected.add(pkgName);
                        LogX.i("  [Name+Service] Detected Shizuku variant: " + pkgName);
                    }
                }
            }
        } catch (Throwable t) {
            LogX.e("Error detecting Shizuku variants", t);
        }

        LogX.i("Detected %d Shizuku variants: %s", detected.size(), detected.toString());
        return detected;
    }

    private static boolean isKnownShizukuPackage(String packageName) {
        for (String known : KNOWN_SHIZUKU_PACKAGES) {
            if (known.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isShizukuByName(String packageName) {
        String lower = packageName.toLowerCase();
        for (String keyword : SHIZUKU_NAME_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasShizukuService(Context context, ApplicationInfo appInfo) {
        try {
            PackageManager pm = context.getPackageManager();
            android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(
                appInfo.packageName,
                PackageManager.GET_SERVICES
                    | PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_PROVIDERS
            );

            if (pkgInfo.services != null) {
                for (android.content.pm.ServiceInfo si : pkgInfo.services) {
                    String name = si.name != null ? si.name.toLowerCase() : "";
                    if (name.contains("shizuku")) {
                        return true;
                    }
                }
            }

            if (pkgInfo.providers != null) {
                for (android.content.pm.ProviderInfo pi : pkgInfo.providers) {
                    String name = pi.name != null ? pi.name.toLowerCase() : "";
                    if (name.contains("shizuku")) {
                        return true;
                    }
                }
            }

            if (pkgInfo.activities != null) {
                for (android.content.pm.ActivityInfo ai : pkgInfo.activities) {
                    String name = ai.name != null ? ai.name.toLowerCase() : "";
                    if (name.contains("permission") || name.contains("shizuku")) {
                        return true;
                    }
                }
            }

        } catch (Throwable t) {
            LogX.d("Failed to check services for: " + appInfo.packageName);
        }
        return false;
    }

    public static boolean isShizukuProcess(String packageName) {
        if (isKnownShizukuPackage(packageName)) {
            return true;
        }
        if (isShizukuByName(packageName)) {
            return true;
        }
        return false;
    }
}
