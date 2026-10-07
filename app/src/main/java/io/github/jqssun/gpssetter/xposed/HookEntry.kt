package io.github.jqssun.gpssetter.xposed

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class HookEntry : XposedModule() {

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        LocationHook.initSystemHooks(this, param.classLoader)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        // Location hooks are process-wide; install them once, for the app the process belongs to
        if (param.isFirstPackage) LocationHook.initAppHooks(this, param.packageName)
    }
}
