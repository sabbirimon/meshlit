package com.meshlit.desktop

/** Sets the Dock/taskbar identity when launched through Java during development. */
internal fun installDesktopIcon() {
    if (java.awt.GraphicsEnvironment.isHeadless()) return
    runCatching {
        if (java.awt.Taskbar.isTaskbarSupported()) {
            val taskbar = java.awt.Taskbar.getTaskbar()
            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                val resource = checkNotNull(Thread.currentThread().contextClassLoader.getResource("icons/meshlit.png"))
                taskbar.iconImage = javax.imageio.ImageIO.read(resource)
            }
        }
    }
}
