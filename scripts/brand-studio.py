"""Apply the Meshlit menu and product identity to the verified, pinned workbench."""
import json
import plistlib
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
    renderer.write_text(renderer.read_text(encoding="utf-8").replace(shell.MENU_RENDERER_PATCH,patch),encoding="utf-8")
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
        subprocess.run(["codesign","--force","--sign","-","--preserve-metadata=entitlements,flags",str(bundle)],check=True)
        subprocess.run(["codesign","--verify","--deep","--strict",str(bundle)],check=True)
