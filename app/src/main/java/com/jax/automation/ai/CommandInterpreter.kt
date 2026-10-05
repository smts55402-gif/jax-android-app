package com.jax.automation.ai

import com.jax.automation.logging.JaxLogger

// ---------------------------------------------------------------------------
// Sends a natural-language command to the configured planner provider and
// validates the returned JSON into a StructuredTask via CommandValidator.
// ---------------------------------------------------------------------------

class CommandInterpreter(
    private val registry: ProviderRegistry,
    private val logger: JaxLogger
) {

    suspend fun interpret(command: String): CommandValidator.ValidationResult {
        val resolved = registry.resolve(ProviderRole.PLANNER)
            ?: return CommandValidator.ValidationResult.Invalid(
                "No planner AI provider configured: enable a provider and save an API key in Providers."
            )
        val system = """
            You are the command interpreter for JAX, an Android automation app that turns scene scripts
            into image-generation jobs run in Google Flow. Convert the user's natural-language command
            into a single JSON object with this exact schema:
            {
              "action": "GENERATE_IMAGE|BATCH_GENERATE|OPEN_APP|OPEN_URL|RETRY_FAILED|PAUSE_QUEUE|RESUME_QUEUE|STOP_QUEUE|SHOW_FAILED|CUSTOM",
              "target": "GOOGLE_FLOW|CHROME|SYSTEM",
              "project": "string (required for GENERATE_IMAGE and BATCH_GENERATE)",
              "scene_id": "string like \"017\" (required for GENERATE_IMAGE)",
              "characters": ["string"],
              "locations": ["string"],
              "objects": ["string"],
              "style": "string",
              "aspect_ratio": "W:H, e.g. \"16:9\"",
              "variations": 1-8,
              "qa_required": true/false,
              "download": true/false
            }
            Rules:
            - "action" must be exactly one of: GENERATE_IMAGE, BATCH_GENERATE, OPEN_APP, OPEN_URL, RETRY_FAILED, PAUSE_QUEUE, RESUME_QUEUE, STOP_QUEUE, SHOW_FAILED, CUSTOM.
            - "target" is required and must be exactly one of: GOOGLE_FLOW, CHROME, SYSTEM. Never omit it.
            - GENERATE_IMAGE requires a non-blank "project" and a non-blank "scene_id" (zero-padded, e.g. "017").
            - BATCH_GENERATE requires a non-blank "project".
            - "aspect_ratio" must look like "16:9".
            - "variations" must be a number from 1 to 8.
            - "qa_required" and "download" default to true.
            Respond with ONLY the JSON object, no markdown, no explanation.
        """.trimIndent()
        return try {
            val raw = resolved.provider.chatText(system, command, resolved.apiKey, resolved.model)
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end < start) {
                return CommandValidator.ValidationResult.Invalid("Planner did not return JSON")
            }
            CommandValidator.validate(raw.substring(start, end + 1))
        } catch (e: AIProviderException) {
            logger.w("CommandInterpreter", "Planner AI error: ${e.message}")
            CommandValidator.ValidationResult.Invalid("AI_ERROR: ${e.message}")
        } catch (e: Exception) {
            logger.w("CommandInterpreter", "Planner failed: ${e.message}")
            CommandValidator.ValidationResult.Invalid("AI_ERROR: ${e.message}")
        }
    }
}
