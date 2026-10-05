package com.jax.automation.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.jax.automation.automation.Selector

/**
 * Node-tree search helpers. All lookups are null-safe.
 */
object NodeSearch {

    /**
     * Finds the best-matching node for [sel].
     * Priority: viewId > contentDescription > text > textContains.
     * The platform lookup is tried first for viewId; the remaining
     * categories are resolved in a single DFS pass.
     */
    fun find(root: AccessibilityNodeInfo?, sel: Selector): AccessibilityNodeInfo? {
        if (root == null) return null

        // Priority 1: viewId — direct platform lookup.
        val viewId = sel.viewId
        if (!viewId.isNullOrBlank()) {
            try {
                val byId = root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()
                if (byId != null) return byId
            } catch (e: Exception) {
                // Fall through to the DFS pass.
            }
        }

        val cd = sel.contentDescription
        val text = sel.text
        val textContains = sel.textContains
        if (cd.isNullOrBlank() && text.isNullOrBlank() && textContains.isNullOrBlank()) return null

        var cdMatch: AccessibilityNodeInfo? = null
        var textMatch: AccessibilityNodeInfo? = null
        var containsMatch: AccessibilityNodeInfo? = null

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val nodeCd = node.contentDescription?.toString()
            val nodeText = node.text?.toString()

            if (cdMatch == null && !cd.isNullOrBlank() && nodeCd == cd) cdMatch = node
            if (textMatch == null && !text.isNullOrBlank() && nodeText == text) textMatch = node
            if (containsMatch == null && !textContains.isNullOrBlank() &&
                (nodeText?.contains(textContains, ignoreCase = true) == true ||
                        nodeCd?.contains(textContains, ignoreCase = true) == true)
            ) {
                containsMatch = node
            }

            if (cdMatch != null && textMatch != null && containsMatch != null) break

            val childCount = node.childCount
            for (i in 0 until childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }

        return cdMatch ?: textMatch ?: containsMatch
    }

    /**
     * Collects visible text across the tree. Falls back to contentDescription
     * when a node has no text, to catch icon buttons.
     */
    fun collectTexts(root: AccessibilityNodeInfo?): List<String> {
        val out = ArrayList<String>()
        if (root == null) return out
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                out.add(text)
            } else {
                val cd = node.contentDescription?.toString()
                if (!cd.isNullOrBlank()) out.add(cd)
            }
            val childCount = node.childCount
            for (i in 0 until childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return out
    }

    /**
     * Walks [node] and its parents, returning the first clickable node or null.
     */
    fun findClickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null && !current.isClickable) {
            current = current.parent
        }
        return current
    }
}
