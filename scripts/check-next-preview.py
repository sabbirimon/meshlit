#!/usr/bin/env python3
"""Static NEXT project/contract check only: never represents ArkTS/NDK/HAP proof."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
NEXT = ROOT / "ohosApp"

def check():
    files = list(NEXT.rglob("*.json5")) + list(NEXT.rglob("*.json"))
    files = [p for p in files if not any(v in p.parts for v in ("build", "oh_modules", ".hvigor"))]
    for path in files:
        json.loads(path.read_text(encoding="utf-8"))  # Sources intentionally use the strict JSON subset of JSON5.
    profile = json.loads((NEXT / "build-profile.json5").read_text(encoding="utf-8"))["app"]["products"][0]
    assert all(profile[k] == "26.0.0" for k in ("compileSdkVersion", "targetSdkVersion", "compatibleSdkVersion"))
    module = json.loads((NEXT / "entry/src/main/module.json5").read_text(encoding="utf-8"))["module"]
    assert module["apiType"] == "stageMode" if "apiType" in module else module["type"] == "entry"
    assert module["requestPermissions"] == [{"name": "ohos.permission.INTERNET"}]
    assert (NEXT / "entry/src/main/ets/entryability/EntryAbility.ets").is_file()
    assert json.loads((NEXT / "entry/src/main/resources/base/profile/main_pages.json").read_text(encoding="utf-8"))["src"] == ["pages/Index"]
    client = (NEXT / "entry/src/main/ets/client/HostClient.ets").read_text(encoding="utf-8")
    assert "maxRedirects: 0" in client and "usingCache: false" in client
    assert "parsed.protocol !== 'https:'" in client
    assert "131072" in client and "262144" in client and "4096" in client
    page = (NEXT / "entry/src/main/ets/pages/Index.ets").read_text(encoding="utf-8")
    assert "language: string = 'en'" in page and "zh-Hans" in page and "简体中文" in page
    assert "put('token'" not in page and "put('messages'" not in page
    native = (NEXT / "entry/src/main/cpp/runtime_capabilities.cpp").read_text(encoding="utf-8")
    assert "napi_get_boolean(env, false" in native and "not linked or qualified" in native
    for forbidden in ("libllama.so", "runanywhere", "JNIEnv", "@ohos.ability.featureAbility"):
        assert forbidden not in page + client + native
    print(f"PASS: {len(files)} strict JSON project files and static NEXT boundaries; ArkTS/NDK/HAP/device NOT tested")

if __name__ == "__main__":
    check()
