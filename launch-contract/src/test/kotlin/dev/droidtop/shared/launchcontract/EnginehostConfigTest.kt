package dev.droidtop.shared.launchcontract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginehostConfigTest {
    @Test
    fun theExampleInTheContractIsUsable() {
        val text = """
            {
              "engine": "rpgmaker",
              "engineContext": "vxace",
              "engineVersion": "3.0",
              "runtimeRequirements": { "ruby": "1.9.2" },
              "pluginVersion": "1.0.0,1.2.0-1.4.0",
              "execFile": "Game.exe",
              "options": { "rtpPaths": ["/storage/emulated/0/RTP/RPGVXAce"] }
            }
        """
        assertEquals(emptyList<String>(), EnginehostConfig.problems(text))
    }

    @Test
    fun whatDroidtopSendsInlineIsUsableWithoutAVersion() {
        val text = """{"engine":"renpy","engineContext":"python3","title":"Some Game","saveRoot":"/data/x","savesSynced":true}"""
        assertEquals(emptyList<String>(), EnginehostConfig.problems(text, requireVersion = false))
        assertEquals(listOf("engineVersion is required"), EnginehostConfig.problems(text))
    }

    @Test
    fun notJsonOrNotAnObjectIsSaidSo() {
        assertEquals(listOf("not JSON"), EnginehostConfig.problems("{engine"))
        assertEquals(listOf("not a JSON object"), EnginehostConfig.problems("[1]"))
    }

    @Test
    fun theEngineIsRequiredAndKeysHaveTheirTypes() {
        val problems = EnginehostConfig.problems("""{"engineVersion":"1.0","execFile":5,"options":[],"runtimeRequirements":"x"}""")
        assertTrue(problems.toString(), "engine is required" in problems)
        assertTrue("execFile must be a string" in problems)
        assertTrue("options must be an object" in problems)
        assertTrue("runtimeRequirements must be an object" in problems)
    }

    @Test
    fun versionsMustBeDottedNumeric() {
        assertEquals(
            listOf("engineVersion must be dotted numeric (got \"4.2-beta\")"),
            EnginehostConfig.problems("""{"engine":"godot","engineVersion":"4.2-beta"}"""),
        )
        assertEquals(
            listOf("runtimeRequirements.ruby must be a dotted numeric version"),
            EnginehostConfig.problems("""{"engine":"rpgmaker","engineVersion":"3.0","runtimeRequirements":{"ruby":"latest"}}"""),
        )
    }

    @Test
    fun savesSyncedIsABoolean() {
        assertEquals(
            listOf("savesSynced must be true or false"),
            EnginehostConfig.problems("""{"engine":"renpy","engineVersion":"8.1","savesSynced":"yes"}"""),
        )
    }
}
