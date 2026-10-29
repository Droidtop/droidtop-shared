package dev.droidtop.shared.launchcontract

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * The key names of `enginehost.json` (and of the inline [EnginehostIntents.EXTRA_CONFIG] string), and a
 * check of its shape. Enginehost's docs/contract.md is the prose; these are its words.
 *
 * `engine` and `engineVersion` are required in a stored file. Everything else is optional. A caller's
 * inline config may leave `engineVersion` out when it wants Enginehost to choose.
 */
object EnginehostConfig {
    const val ENGINE = "engine"
    const val ENGINE_CONTEXT = "engineContext"
    const val ENGINE_VERSION = "engineVersion"
    const val RUNTIME_REQUIREMENTS = "runtimeRequirements"
    const val PLUGIN_VERSION = "pluginVersion"
    const val EXEC_FILE = "execFile"
    const val ROOT = "root"
    const val TITLE = "title"
    const val SAVE_FOLDER = "saveFolder"
    const val OPTIONS = "options"

    /** A caller's save folder for a synced launch, and whether it is synced (docs/contract.md, "A caller's save folder"). */
    const val SAVE_ROOT = "saveRoot"
    const val SAVES_SYNCED = "savesSynced"

    private val STRING_KEYS = listOf(ENGINE, ENGINE_CONTEXT, ENGINE_VERSION, PLUGIN_VERSION, EXEC_FILE, ROOT, TITLE, SAVE_FOLDER, SAVE_ROOT)
    private val DOTTED_NUMERIC = Regex("""\d+(\.\d+)*""")

    /**
     * What is wrong with [text] as an `enginehost.json`, one line each; empty when it is a usable one.
     * [requireVersion] is true for a stored file and false for a caller's inline config.
     */
    fun problems(text: String, requireVersion: Boolean = true): List<String> {
        val element: JsonElement = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return listOf("not JSON")
        }
        val json: JsonObject = try {
            element.jsonObject
        } catch (e: IllegalArgumentException) {
            return listOf("not a JSON object")
        }
        val found = mutableListOf<String>()
        fun string(key: String): String? = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        for (key in STRING_KEYS) {
            val value = json[key] ?: continue
            if (value !is JsonPrimitive || !value.isString) found += "$key must be a string"
        }
        if (string(ENGINE).isNullOrBlank()) found += "engine is required"
        if (requireVersion && string(ENGINE_VERSION).isNullOrBlank()) found += "engineVersion is required"
        string(ENGINE_VERSION)?.takeIf { it.isNotBlank() && !DOTTED_NUMERIC.matches(it.trim()) }?.let {
            found += "engineVersion must be dotted numeric (got \"$it\")"
        }
        json[RUNTIME_REQUIREMENTS]?.let { requirements ->
            val map = requirements as? JsonObject
            if (map == null) {
                found += "runtimeRequirements must be an object"
            } else {
                for ((key, value) in map) {
                    val version = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (version == null || !DOTTED_NUMERIC.matches(version)) found += "runtimeRequirements.$key must be a dotted numeric version"
                }
            }
        }
        json[OPTIONS]?.let { if (it !is JsonObject) found += "options must be an object" }
        json[SAVES_SYNCED]?.let { if (it !is JsonPrimitive || it.isString || it.content !in listOf("true", "false")) found += "savesSynced must be true or false" }
        return found
    }
}
