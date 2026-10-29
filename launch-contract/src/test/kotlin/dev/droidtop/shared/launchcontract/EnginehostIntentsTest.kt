package dev.droidtop.shared.launchcontract

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Each string is what released builds of both apps send and read. These literals are written out here
 * on purpose: a test that compared a constant with itself could never fail on a rename.
 */
class EnginehostIntentsTest {
    @Test
    fun theActionsAreThoseOfReleasedBuilds() {
        assertEquals("dev.enginehost", EnginehostIntents.PACKAGE)
        assertEquals("dev.enginehost.LAUNCH", EnginehostIntents.ACTION_LAUNCH)
        assertEquals("dev.enginehost.CONFIGURE", EnginehostIntents.ACTION_CONFIGURE)
        assertEquals("dev.enginehost.CONFIGURE_SETTINGS", EnginehostIntents.ACTION_CONFIGURE_SETTINGS)
        assertEquals("dev.enginehost.CONFIGURE_SAVES", EnginehostIntents.ACTION_CONFIGURE_SAVES)
        assertEquals("dev.enginehost.SCAN", EnginehostIntents.ACTION_SCAN)
        assertEquals("dev.enginehost.END_GAME", EnginehostIntents.ACTION_END_GAME)
        assertEquals("dev.enginehost.GAME_ENDED", EnginehostIntents.ACTION_GAME_ENDED)
    }

    @Test
    fun theExtrasAreThoseOfReleasedBuilds() {
        assertEquals("path", EnginehostIntents.EXTRA_PATH)
        assertEquals("config", EnginehostIntents.EXTRA_CONFIG)
        assertEquals("autoinstallPlugin", EnginehostIntents.EXTRA_AUTOINSTALL_PLUGIN)
        assertEquals("accent", EnginehostIntents.EXTRA_ACCENT)
        assertEquals("ended", EnginehostIntents.EXTRA_ENDED)
        assertEquals("error", EnginehostIntents.EXTRA_ERROR)
        assertEquals("saveFolder", EnginehostIntents.EXTRA_SAVE_FOLDER)
        assertEquals("reason", EnginehostIntents.EXTRA_REASON)
        assertEquals("saveRootRefused", EnginehostIntents.EXTRA_SAVE_ROOT_REFUSED)
        assertEquals("untrusted_caller", EnginehostIntents.ERROR_UNTRUSTED_CALLER)
        assertEquals("exited", EnginehostIntents.REASON_EXITED)
        assertEquals("crashed", EnginehostIntents.REASON_CRASHED)
    }

    @Test
    fun theCapabilitiesProviderIsWhereReleasedBuildsLookForIt() {
        assertEquals("dev.enginehost.capabilities", EnginehostIntents.CAPABILITIES_AUTHORITY)
        assertEquals("installed", EnginehostIntents.CAPABILITIES_INSTALLED_PATH)
    }

    @Test
    fun theConfigKeysAreThoseOfReleasedBuilds() {
        assertEquals(
            listOf("engine", "engineContext", "engineVersion", "runtimeRequirements", "pluginVersion", "execFile", "root", "title", "saveFolder", "options", "saveRoot", "savesSynced"),
            listOf(
                EnginehostConfig.ENGINE, EnginehostConfig.ENGINE_CONTEXT, EnginehostConfig.ENGINE_VERSION,
                EnginehostConfig.RUNTIME_REQUIREMENTS, EnginehostConfig.PLUGIN_VERSION, EnginehostConfig.EXEC_FILE,
                EnginehostConfig.ROOT, EnginehostConfig.TITLE, EnginehostConfig.SAVE_FOLDER, EnginehostConfig.OPTIONS,
                EnginehostConfig.SAVE_ROOT, EnginehostConfig.SAVES_SYNCED,
            ),
        )
    }
}
