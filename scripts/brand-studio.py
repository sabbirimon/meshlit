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
from prepare_workbench import brand_workbench_icons, refresh_workbench_checksums

MENU = [
    ("1_home","General chat","meshlit.studio.chat"), ("1_home","Open coding IDE…","meshlit.studio.ide"),
    ("1_terminal","New Terminal","workbench.action.terminal.new"),
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


def brand_product(resources):
    product_file = resources / "product.json"
    product = json.loads(product_file.read_bytes())
    product.update(applicationName="meshlit", linuxIconName="meshlit",
                   linuxDesktopName="bd.com.imon.meshlit.studio",
                   win32AppUserModelId="IMON.Meshlit.Studio", win32MutexName="meshlit-studio",
                   win32DirName="Meshlit Studio", win32NameVersion="Meshlit Studio",
                   win32RegValueName="Meshlit Studio", win32ShellNameShort="Meshlit Studio")
    product_file.write_text(json.dumps(product, indent=2) + "\n", encoding="utf-8")
    brand_workbench_icons(resources, ROOT / "ohosApp/AppScope/resources/base/media/app_icon.svg")
    refresh_workbench_checksums(resources)


def brand_desktop(editor, system, rcedit=None):
    icons = ROOT / "desktopApp/distribution/icons"
    mac = system == "Darwin"
    bundle = editor / "VSCodium.app"
    resources = bundle / "Contents/Resources/app" if mac else editor / "resources/app"
    brand_product(resources)
    # Preserve the upstream CLI entry points as aliases for scripts using the portable suite.
    bin_dir = resources / "bin" if mac else editor / "bin"
    for script in bin_dir.glob("codium*"):
        if script.suffix not in ("", ".cmd") or not script.is_file() or "tunnel" in script.name:
            continue
        source = script.read_bytes().replace(b"VSCodium", b"Meshlit").replace(b"/codium\"", b"/meshlit\"")
        script.write_bytes(source)
        shutil.copy2(script, script.with_name(script.name.replace("codium", "meshlit", 1)))

    if mac:
        info = bundle / "Contents/Info.plist"
        plist = plistlib.loads(info.read_bytes())
        old_binary = bundle / "Contents/MacOS" / plist["CFBundleExecutable"]
        old_binary.rename(old_binary.with_name("Meshlit"))
        plist.update(CFBundleExecutable="Meshlit", CFBundleName="Meshlit",
                     CFBundleDisplayName="Meshlit Studio", CFBundleIdentifier="bd.com.imon.meshlit.workbench",
                     CFBundleIconFile="meshlit.icns")
        plist.pop("CFBundleIconName", None)
        info.write_bytes(plistlib.dumps(plist))
        shutil.copy2(icons / "meshlit.icns", bundle / "Contents/Resources/meshlit.icns")
        sign_mac_bundle(bundle.rename(editor / "Meshlit Studio.app"))
    elif system == "Windows":
        program = (editor / "VSCodium.exe").rename(editor / "Meshlit.exe")
        brand_windows_binary(program, rcedit)
        for icon in resources.glob("resources/win32/code*.ico"):
            if icon.stem in {"code", "code-insiders", "code-oss"}:
                shutil.copy2(icons / "meshlit.ico", icon)
    else:
        (editor / "codium").rename(editor / "meshlit")
        (editor / "codium").symlink_to("meshlit")
        for icon in [resources / "resources/linux/code.png", editor / "resources/vscodium.png"]:
            if icon.exists():
                shutil.copy2(icons / "meshlit.png", icon)


def brand_windows_binary(program, rcedit):
    command = [rcedit, program, "--set-icon", ROOT / "desktopApp/distribution/icons/meshlit.ico"]
    for field, value in {"FileDescription": "Meshlit Studio", "ProductName": "Meshlit Studio",
                         "CompanyName": "IMON", "InternalName": "Meshlit", "OriginalFilename": program.name}.items():
        command.extend(["--set-version-string", field, value])
    subprocess.run(command, check=True)


def studio_renderer(source):
    patch = shell.MENU_RENDERER_PATCH.replace(json.dumps(shell.STUDIO_MENU_ITEMS, ensure_ascii=True),
                                              json.dumps(MENU, ensure_ascii=True))
    patch = patch.replace('value:"Studio",original:"Studio",mnemonicTitle:"&&Studio"', 'value:"Meshlit",original:"Meshlit",mnemonicTitle:"&&Meshlit"').replace('order:7.5','order:0.5')
    start=source.index('/* Meshlit native Studio menu */')
    end=source.index('})();',start)+len('})();')
    source=source[:start]+patch+source[end:]
    # Both Electron and the custom Windows title bar consume this window's menu list.
    anchor='for(let t of e)t instanceof lu&&typeof t.item.title!="string"&&'
    if source.count(anchor)!=1:
        raise ValueError("Pinned renderer menu builder changed")
    return source.replace(anchor,anchor+'(!globalThis.__MESHLIT_CHAT_SHELL||["Meshlit","Terminal","Help"].includes(t.item.title.original))&&')


def studio_native(source,mac):
    anchor='if(this.shouldDrawMenu("File"))'
    if source.count(anchor)!=1 or source.count(shell.MENU_NATIVE_PATCH)!=1:
        raise ValueError("Pinned native Studio menu changed")
    menu=shell.MENU_NATIVE_PATCH.replace('"Studio"','"Meshlit"').replace('"&&Studio"','"&&Meshlit"')
    if mac:
        # The standard macOS application menu already bears the product name.
        menu=menu.replace('"&&Meshlit"','"Studio"')
    return source.replace(shell.MENU_NATIVE_PATCH,'').replace(anchor,menu+anchor)


def configure(editor, system, rcedit=None):
    mac = system == "Darwin"
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
    renderer.write_text(studio_renderer(renderer.read_text(encoding="utf-8")), encoding="utf-8")
    native=out/"mainImpl.js"
    source=studio_native(native.read_text(encoding="utf-8"),mac)
    marker='f=[t];f.push(...r),o&&f.push(Bo(),o)'
    if source.count(marker)!=1:
        raise ValueError("Pinned macOS application menu changed")
    native.write_text(source.replace(marker,'f=[t,this.createMenuItem("Meshlit Settings…","meshlit.studio.settings")];f.push(...r),o&&f.push(Bo(),o)'),encoding="utf-8")
    brand_desktop(editor, system, rcedit)
