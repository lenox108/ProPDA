package forpdateam.ru.forpda.ui.fragments.theme.modules

/**
 * Common lifecycle contract for UI modules in ThemeFragmentWeb.
 * Ensures every module has explicit initialization and cleanup.
 */
interface ThemeUiModule {
    fun init()
    fun dispose()
}

/**
 * Registry that initializes all modules and disposes them in reverse order.
 * Reverse disposal ensures dependent modules are torn down before their dependencies.
 */
class ThemeUiModuleRegistry {

    private val modules = mutableListOf<ThemeUiModule>()

    fun register(module: ThemeUiModule) {
        modules.add(module)
    }

    fun initAll() {
        modules.forEach { it.init() }
    }

    fun disposeAll() {
        modules.asReversed().forEach { it.dispose() }
        modules.clear()
    }
}
