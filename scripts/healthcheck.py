#!/usr/bin/env python3
"""
LSP-Model 模块体检脚本
输出 JSON 报告到 stdout，供 Web 工具渲染。
体检维度见 AI_DEV_GUIDE.md §6.1
"""
import os, re, glob, json, sys
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULES_DIR = os.path.join(ROOT, "modules")
VERSION = "1.0.7"

def scan_module(mod_dir, mod_name):
    """扫描单个模块"""
    result = {
        "name": mod_name,
        "version": VERSION,
        "hooks": [],
        "uiBugs": 0,
        "configIssues": [],
        "stats": {"hookFiles": 0, "ok": 0, "weak": 0, "hollow": 0, "totalHookCalls": 0}
    }

    # 1. Hook 体检
    hook_files = glob.glob(f"{mod_dir}/app/src/main/java/**/hooks/*.kt", recursive=True)
    result["stats"]["hookFiles"] = len(hook_files)

    for f in sorted(hook_files):
        with open(f, errors='replace') as fh:
            c = fh.read()
        name = os.path.basename(f)
        hook_calls = len(re.findall(r'findAndHookMethod|findAndHookConstructor|hookAllMethods|XposedBridge\.hook(?:Method|AllMethods)?\(', c))
        try_catch = len(re.findall(r'\bcatch\b', c))
        logs = len(re.findall(r'LogX\.|Log\.|XposedBridge\.log', c))
        todo = len(re.findall(r'TODO|FIXME|未实现|待实现|placeholder|stub', c, re.I))
        lines = c.count('\n') + 1

        # 判定状态
        is_utility = bool(re.search(r'工具类|工具|提供.*查询|供.*调用', c[:500]))
        if hook_calls == 0 and is_utility:
            status = "utility"  # 工具类，合理
        elif hook_calls == 0:
            status = "hollow"
            result["stats"]["hollow"] += 1
        elif hook_calls < 2 and try_catch > hook_calls * 2:
            status = "weak"
            result["stats"]["weak"] += 1
        else:
            status = "ok"
            result["stats"]["ok"] += 1

        result["stats"]["totalHookCalls"] += hook_calls
        result["hooks"].append({
            "file": name,
            "hookCalls": hook_calls,
            "tryCatch": try_catch,
            "logs": logs,
            "todo": todo,
            "lines": lines,
            "status": status
        })

    # 2. UI bug 体检（cfg.X = it 直接改字段）
    ui_files = glob.glob(f"{mod_dir}/app/src/main/java/**/ui/**/*.kt", recursive=True)
    ui_bugs = 0
    for f in ui_files:
        with open(f, errors='replace') as fh:
            c = fh.read()
        # 匹配 cfg.X = it 但不是 cfg.copy(
        bugs = re.findall(r'cfg\.\w+\s*=\s*it(?!\.\w)', c)
        bugs = [b for b in bugs if 'copy(' not in c[max(0,c.find(b)-20):c.find(b)+len(b)+20]]
        ui_bugs += len(bugs)
    result["uiBugs"] = ui_bugs

    # 3. 配置一致性
    gradle = f"{mod_dir}/app/build.gradle.kts"
    if os.path.isfile(gradle):
        with open(gradle) as fh:
            gc = fh.read()
        checks = {
            "versionName匹配VERSION": f'versionName = "{VERSION}"' in gc,
            "versionCode=1": 'versionCode = 1' in gc,
            "signingConfig": 'meng411722' in gc,
            "compose=true": 'compose = true' in gc,
            "jvmTarget=17": 'jvmTarget = "17"' in gc,
            "noBOM": 'compose-bom' not in gc,
            "xposedCompileOnly": 'compileOnly("de.robv.android.xposed:api:82")' in gc,
        }
        for k, v in checks.items():
            if not v:
                result["configIssues"].append(k)

    # 4. Manifest 检查
    manifest = f"{mod_dir}/app/src/main/AndroidManifest.xml"
    if os.path.isfile(manifest):
        with open(manifest) as fh:
            mc = fh.read()
        for meta in ['xposedmodule', 'xposedminversion', 'xposeddescription', 'xposedscope']:
            if f'android:name="{meta}"' not in mc:
                result["configIssues"].append(f"manifest缺{meta}")

    # 5. xposed_init 检查
    xposed_init = f"{mod_dir}/app/src/main/assets/xposed_init"
    if os.path.isfile(xposed_init):
        with open(xposed_init) as fh:
            init = fh.read().strip()
        if not init.endswith("XposedLoader"):
            result["configIssues"].append(f"xposed_init非XposedLoader: {init}")

    return result

def main():
    report = {
        "version": VERSION,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "modules": []
    }
    for mod in sorted(os.listdir(MODULES_DIR)):
        mod_dir = os.path.join(MODULES_DIR, mod)
        if not os.path.isdir(mod_dir) or mod == "keystore":
            continue
        report["modules"].append(scan_module(mod_dir, mod))

    # 汇总
    total = sum(m["stats"]["hookFiles"] for m in report["modules"])
    ok = sum(m["stats"]["ok"] for m in report["modules"])
    weak = sum(m["stats"]["weak"] for m in report["modules"])
    hollow = sum(m["stats"]["hollow"] for m in report["modules"])
    ui_bugs = sum(m["uiBugs"] for m in report["modules"])
    report["summary"] = {
        "totalModules": len(report["modules"]),
        "totalHookFiles": total,
        "ok": ok, "weak": weak, "hollow": hollow,
        "uiBugs": ui_bugs,
        "healthScore": round(ok * 100 / total, 1) if total else 0
    }

    print(json.dumps(report, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
