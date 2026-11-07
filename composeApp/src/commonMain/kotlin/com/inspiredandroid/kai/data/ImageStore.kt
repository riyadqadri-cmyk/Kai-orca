package com.inspiredandroid.kai.data

/**
 * Attached images are not carried in [History] as base64. Each one is written to disk once,
 * and the message text keeps only a lightweight marker of the form `[[image:<id>]]`.
 *
 * Why: every historical user message is re-serialized into every subsequent request, and each
 * image costs up to 384 input tokens on DeepSeek's vision models. Re-sending a screenshot on
 * every turn is pure waste once the model has already looked at it.
 *
 * The model can pull a specific image back into a turn on demand via the `view_image` tool,
 * which returns a multimodal payload for that turn only.
 */
object ImageMarker {
    private const val PREFIX = "[[image:"
    private const val SUFFIX = "]]"

    /** Regex over markers, used to strip them for display and to discover which ids a text refs. */
    private val markerRegex = Regex("""\[\[image:([A-Za-z0-9_-]+)]]""")

    fun marker(id: String): String = "$PREFIX$id$SUFFIX"

    /** All image ids referenced by a message text, in order of appearance. */
    fun extractIds(text: String): List<String> = markerRegex.findAll(text).map { it.groupValues[1] }.toList()

    /** Text with every marker removed, collapsed whitespace, for UI rendering and prompt text. */
    fun strip(text: String): String = markerRegex.replace(text, "").trim().replace(Regex("""\s{2,}"""), " ")
}

/**
 * In-memory side channel between the `view_image` tool and the tool-call loop.
 *
 * [Tool.execute] can only return JSON-serializable data, so it cannot hand pixels back directly.
 * Instead the tool parks the resolved image here, keyed by call id, and the loop drains it after
 * executing that round's tools and appends the images as a USER message — DeepSeek rejects
 * images in any other role.
 */
class PendingImageBuffer {
    private val pending = mutableMapOf<String, Attachment>()

    fun put(callScopedId: String, attachment: Attachment) {
        pending[callScopedId] = attachment
    }

    /** Removes and returns everything parked, so a drained image is sent at most once. */
    fun drain(): List<Attachment> {
        if (pending.isEmpty()) return emptyList()
        val out = pending.values.toList()
        pending.clear()
        return out
    }
}
