"""Apply the Meshlit menu and product identity to the verified, pinned workbench."""
import json
import plistlib
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "companions/studio-runtime"))
import prepare_shell as shell
from prepare_workbench import refresh_workbench_checksums

MENU = [
    ("1_home","General chat","meshlit.studio.chat"), ("1_home","Open coding IDE…","meshlit.studio.ide"),
    ("2_manage","Management","meshlit.studio.management"), ("2_manage","Tools","meshlit.studio.tools"),
    ("2_manage","Models and providers","meshlit.studio.models"), ("2_manage","Devices","meshlit.studio.pair"),
    ("3_settings","Meshlit Settings…","meshlit.studio.settings"),
]


def brand_mac_helpers(bundle):
    frameworks = bundle / "Contents/Frameworks"
    helpers = sorted(frameworks.glob("VSCodium Helper*.app"))
    if not any(helper.stem == "VSCodium Helper" for helper in helpers):
        raise ValueError("Pinned Electron helper bundle is missing")
    for helper in helpers:
        name = helper.stem.replace("VSCodium", "Meshlit", 1)
        info = helper / "Contents/Info.plist"
        plist = plistlib.loads(info.read_bytes())
        executable = helper / "Contents/MacOS" / plist.get("CFBundleExecutable", helper.stem)
        executable.rename(executable.with_name(name))
        suffix = helper.stem.removeprefix("VSCodium Helper").strip().strip("()").lower()
        identifier = "bd.com.imon.meshlit.workbench.helper" + ("." + suffix if suffix else "")
        plist.update(CFBundleExecutable=name, CFBundleName=name, CFBundleDisplayName=name,
                     CFBundleIdentifier=identifier)
        info.write_bytes(plistlib.dumps(plist))
        renamed = helper.rename(helper.with_name(name + ".app"))
        subprocess.run(["codesign", "--force", "--sign", "-", str(renamed)], check=True)


def sign_mac_bundle(bundle):
    # Electron locates its helpers using the parent application's bundle name.
    brand_mac_helpers(bundle)
    subprocess.run(["codesign", "--force", "--sign", "-", str(bundle)], check=True)
    subprocess.run(["codesign", "--verify", "--deep", "--strict", str(bundle)], check=True)


def configure(editor, mac):
    bundle = editor / "VSCodium.app"
    resources = bundle / "Contents/Resources/app" if mac else editor / "resources/app"
    out = resources / "out"
    product_file = resources / "product.json"
    product = json.loads(product_file.read_text(encoding="utf-8"))
    if product.get("nameShort") != "VSCodium":
        raise ValueError("Unexpected upstream product identity")
    product.update(nameShort="Meshlit",nameLong="Meshlit Studio",darwinBundleIdentifier="bd.com.imon.meshlit.workbench")
    product.setdefault("configurationDefaults", {})["window.restoreWindows"] = "none"
    product_file.write_text(json.dumps(product,indent=2)+"\n")
    package_file = resources / "package.json"
    pkg=json.loads(package_file.read_text(encoding="utf-8"))
    pkg.update(name="meshlit-studio",productName="Meshlit")
    package_file.write_text(json.dumps(pkg,indent=2)+"\n")
    shell.configure_chat_shell(resources)
    renderer=out/"vs/workbench/workbench.desktop.main.js"
    patch=shell.MENU_RENDERER_PATCH.replace(json.dumps(shell.STUDIO_MENU_ITEMS,ensure_ascii=True),json.dumps(MENU,ensure_ascii=True))
    source = renderer.read_text(encoding="utf-8").replace(shell.MENU_RENDERER_PATCH, patch)
    if not mac:
        # Keep project menus in IDE windows. The Studio menu is always available.
        pattern = r"(De\.appendMenuItem\(F\.MenubarMainMenu,\{submenu:F\.Menubar\w+Menu,title:\{[^}]+\},)([^}]+)(\}\);)"
        def project_menu(match):
            options = match[2]
            predicate = 'D.not("meshlitStudioHome")'
            if "when:" in options:
                options = re.sub(r"when:([^,]+)", lambda m: "when:D.and(" + predicate + "," + m[1] + ")", options)
            else:
                options += ",when:" + predicate
            return match[1] + options + match[3]
        source, count = re.subn(pattern, project_menu, source)
        if count != 9:
            raise ValueError(f"Unexpected pinned workbench menu count: {count}")
    renderer.write_text(source, encoding="utf-8")
    native=out/"mainImpl.js"
    source=native.read_text(encoding="utf-8")
    marker='f=[t];f.push(...r),o&&f.push(Bo(),o)'
    if source.count(marker)!=1:
        raise ValueError("Pinned macOS application menu changed")
    native.write_text(source.replace(marker,'f=[t,this.createMenuItem("Meshlit Settings…","meshlit.studio.settings")];f.push(...r),o&&f.push(Bo(),o)'),encoding="utf-8")
    refresh_workbench_checksums(resources)
    if mac:
        info=bundle/"Contents/Info.plist"
        plist=plistlib.loads(info.read_bytes())
        plist.update(CFBundleName="Meshlit",CFBundleDisplayName="Meshlit",CFBundleIdentifier="bd.com.imon.meshlit.workbench",CFBundleIconFile="meshlit.icns")
        plist.pop("CFBundleIconName",None)
        info.write_bytes(plistlib.dumps(plist))
        shutil.copy2(ROOT/"desktopApp/distribution/icons/meshlit.icns",bundle/"Contents/Resources/meshlit.icns")
        sign_mac_bundle(bundle)
