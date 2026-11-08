package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.Attachment
import com.inspiredandroid.kai.data.PendingImageBuffer
import com.inspiredandroid.kai.readAttachedImage
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlin.io.encoding.Base64

/**
 * Lets the model look at an image that was attached earlier in the conversation.
 *
 * Images are stripped out of the history when a message is sent (see [ImageMarker]) so they are
 * not re-billed on every subsequent turn. The message keeps a `[[image:<id>]]` marker instead.
 * When the model needs to actually see one again, it calls this tool with that id.
 *
 * The pixels cannot be returned as a tool result — [Tool.execute] is text/JSON only — so the
 * decoded image is parked in [pendingImages] and the tool-call loop appends it to the next
 * request as a USER message.
 */
object ViewImageTool {
    const val NAME = "view_image"

    private const val DESCRIPTION = """Load an image that was attached earlier in this conversation.

Attached images are referenced in the message text as `[[image:<id>]]` markers. You have already
seen each image once when it was attached; the pixels are not re-sent afterwards to save tokens.
Call this tool with the id from a marker when you need to look at that image again.

The image is delivered on the next turn only. Call again if you need it in a later turn."""

    fun create(pendingImages: PendingImageBuffer): Tool = object : Tool {
        override val schema = ToolSchema(
            name = NAME,
            description = DESCRIPTION,
            parameters = mapOf(
                "image_id" to ParameterSchema(
                    type = "string",
                    description = "Id from an `[[image:<id>]]` marker, e.g. `img_a1b2c3`.",
                    required = true,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val id = (args["image_id"] as? String)?.trim()?.removeSurrounding("[[image:", "]]")
                ?: return mapOf("success" to false, "error" to "image_id is required")
            if (id.isEmpty()) {
                return mapOf("success" to false, "error" to "image_id is required")
            }

            val bytes = readAttachedImage(id)
                ?: return mapOf(
                    "success" to false,
                    "error" to "No image stored for id '$id'. It may have been deleted, or the id is wrong.",
                )

            pendingImages.put(
                key = id,
                attachment = Attachment(
                    data = Base64.encode(bytes),
                    mimeType = "image/jpeg",
                    fileName = id,
                ),
            )

            return mapOf(
                "success" to true,
                "image_id" to id,
                "message" to "Image $id attached to the next message.",
            )
        }
    }
}
