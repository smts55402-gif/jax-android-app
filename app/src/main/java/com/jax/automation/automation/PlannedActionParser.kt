package com.jax.automation.automation

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses a JSON array of action objects into [PlannedAction] steps.
 *
 * Uses org.json (available on Android). This runs on-device only and is
 * intentionally NOT covered by JVM unit tests; engine tests use
 * [PlannedAction] objects directly.
 *
 * Expected shape per object:
 *   {"action":"CLICK","selector":{"viewId":"...","contentDescription":"...",
 *    "text":"...","textContains":"..."},"text":"...","url":"...",
 *    "packageName":"...","direction":"UP","x1":0,"y1":0,"x2":0,"y2":0,
 *    "durationMs":500,"timeoutMs":10000,"retries":2,"message":"..."}
 *
 * Throws [IllegalArgumentException] on malformed JSON or unknown action names.
 */
object PlannedActionParser {

    fun parse(json: String): List<PlannedAction> {
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            throw IllegalArgumentException("PlannedActionParser: malformed JSON: ${e.message}", e)
        }
        val actions = ArrayList<PlannedAction>(array.length())
        for (i in 0 until array.length()) {
            val obj = try {
                array.getJSONObject(i)
            } catch (e: JSONException) {
                throw IllegalArgumentException(
                    "PlannedActionParser: action at index $i is not a JSON object", e
                )
            }
            actions.add(parseAction(i, obj))
        }
        return actions
    }

    private fun parseAction(index: Int, obj: JSONObject): PlannedAction {
        val name = optString(obj, "action").ifBlank {
            throw IllegalArgumentException(
                "PlannedActionParser: action at index $index missing 'action'"
            )
        }
        val action = try {
            AutomationAction.valueOf(name)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "PlannedActionParser: unknown action name '$name' at index $index", e
            )
        }

        val selector = if (obj.has("selector")) {
            val selObj = try {
                obj.getJSONObject("selector")
            } catch (e: JSONException) {
                throw IllegalArgumentException(
                    "PlannedActionParser: malformed selector at index $index", e
                )
            }
            Selector(
                viewId = optString(selObj, "viewId"),
                contentDescription = optString(selObj, "contentDescription"),
                text = optString(selObj, "text"),
                textContains = optString(selObj, "textContains")
            )
        } else {
            null
        }

        val direction = optString(obj, "direction").ifBlank { null }?.let { raw ->
            try {
                ScrollDirection.valueOf(raw)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "PlannedActionParser: unknown direction '$raw' at index $index", e
                )
            }
        }

        return PlannedAction(
            action = action,
            selector = selector,
            text = optString(obj, "text"),
            url = optString(obj, "url"),
            packageName = optString(obj, "packageName"),
            direction = direction,
            x1 = obj.optInt("x1", 0),
            y1 = obj.optInt("y1", 0),
            x2 = obj.optInt("x2", 0),
            y2 = obj.optInt("y2", 0),
            durationMs = obj.optInt("durationMs", 500),
            timeoutMs = obj.optLong("timeoutMs", 10_000L),
            retries = obj.optInt("retries", 2),
            message = optString(obj, "message")
        )
    }

    /**
     * Null-safe string read: returns null when the key is absent or JSON null,
     * never the literal "null" string and never an NPE.
     */
    private fun optString(obj: JSONObject, key: String): String? {
        if (!obj.has(key)) return null
        val value = obj.opt(key)
        return if (value == null || value === JSONObject.NULL) null else value.toString()
    }
}
