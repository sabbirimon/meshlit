use std::{env, fs, io, path::PathBuf, process::Command};

fn launch() -> io::Result<()> {
    let executable = env::current_exe()?;
    let mut root = executable.parent().unwrap();
    if cfg!(target_os = "macos") {
        root = root
            .ancestors()
            .nth(3)
            .ok_or(io::Error::other("Incomplete app bundle"))?;
    }
    let editor = root.join(if cfg!(windows) {
        "editor/VSCodium.exe"
    } else if cfg!(target_os = "macos") {
        "editor/VSCodium.app/Contents/MacOS/VSCodium"
    } else {
        "editor/codium"
    });
    let cli = root.join(if cfg!(target_os = "macos") {
        "editor/VSCodium.app/Contents/Resources/app/out/cli.js"
    } else {
        "editor/resources/app/out/cli.js"
    });
    let home = env::var_os(if cfg!(windows) { "USERPROFILE" } else { "HOME" })
        .ok_or(io::Error::other("User home unavailable"))?;
    let mut profile = PathBuf::from(home).join(".meshlit/native-studio");
    let args: Vec<_> = env::args_os().skip(1).collect();
    let mut folder = None;
    let mut probe = None;
    let mut home = args.is_empty();
    match args.as_slice() {
        [] => (),
        [option, dir] if option == "--workspace" => folder = Some(std::path::absolute(dir)?),
        [option, dir] if option == "--self-test" || option == "--self-test-home" => {
            home = option == "--self-test-home";
            let dir = std::path::absolute(dir)?;
            profile = dir.join("profile");
            if !home {
                folder = Some(dir.join("workspace"));
            }
            probe = Some(dir.join("probe"));
        }
        _ => return Err(io::Error::other("Usage: Meshlit [--workspace DIRECTORY]")),
    }
    if folder.as_ref().is_some_and(|p| !p.is_dir()) {
        return Err(io::Error::other("Workspace must be a directory"));
    }
    let extensions = profile.join("extensions");
    let user_data = profile.join("settings");
    fs::create_dir_all(&extensions)?;
    let settings = user_data.join("User");
    fs::create_dir_all(&settings)?;
    match fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(settings.join("settings.json"))
    {
        Ok(mut file) => {
            use std::io::Write;
            file.write_all(br#"{"update.mode":"none","telemetry.telemetryLevel":"off","extensions.autoCheckUpdates":false,"extensions.autoUpdate":false,"workbench.startupEditor":"none"}"#)?;
        }
        Err(e) if e.kind() == io::ErrorKind::AlreadyExists => {}
        Err(e) => return Err(e),
    }
    let make = || {
        let mut cmd = Command::new(&editor);
        cmd.env_remove("ELECTRON_RUN_AS_NODE");
        cmd.env("MESHLIT_SUITE_ROOT", root);

        cmd.env(
            "PATH",
            env::join_paths(
                std::iter::once(root.join("toolchain"))
                    .chain(env::split_paths(&env::var_os("PATH").unwrap_or_default())),
            )
            .expect("invalid toolchain PATH"),
        );
        cmd
    };
    let vsix = root.join("meshlit-native.vsix");
    // The receipt is a packaged content digest. An update triggers a fresh offline install.
    let revision = fs::read_to_string(root.join("extension.sha256"))?;
    let marker = profile.join("installed-extension");
    if fs::read_to_string(&marker).ok().as_deref() != Some(revision.as_str())
        || !extensions
            .join("meshlit.native-studio-2.0.48/package.json")
            .is_file()
    {
        let result = make()
            .env("ELECTRON_RUN_AS_NODE", "1")
            .arg(cli)
            .arg("--user-data-dir")
            .arg(&user_data)
            .arg("--extensions-dir")
            .arg(&extensions)
            .arg("--install-extension")
            .arg(vsix)
            .arg("--force")
            .status()?;
        if !result.success() {
            return Err(io::Error::other(
                "Cannot install the bundled Studio interface",
            ));
        }
        fs::write(marker, revision)?;
    }
    let mut gui = make();
    gui.arg("--user-data-dir")
        .arg(user_data)
        .arg("--extensions-dir")
        .arg(extensions)
        .arg("--new-window");
    if home {
        gui.arg("--force-disable-user-env");
    }
    if let Some(path) = probe {
        // A disposable extension test profile must never request credentials from the operator.
        gui.arg("--use-inmemory-secretstorage");
        if cfg!(target_os = "macos") {
            gui.arg("--use-mock-keychain");
        }
        if !home {
            gui.arg("--disable-workspace-trust");
        }
        gui.arg("--disable-gpu")
            .arg(format!("--extensionDevelopmentPath={}", path.display()))
            .arg(format!(
                "--extensionTestsPath={}",
                path.join("test.js").display()
            ));
        if let Some(dir) = folder {
            gui.arg(dir);
        }
        let status = gui.status()?;
        if !status.success() {
            return Err(io::Error::other("Studio self-test failed"));
        }
    } else {
        if let Some(dir) = folder {
            gui.arg(dir);
        }
        gui.spawn()?;
    }
    Ok(())
}

fn main() {
    if let Err(error) = launch() {
        eprintln!("Meshlit: {error}");
        std::process::exit(1);
    }
}
