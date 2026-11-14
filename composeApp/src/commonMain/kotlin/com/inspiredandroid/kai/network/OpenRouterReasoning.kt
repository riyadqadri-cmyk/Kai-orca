package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import io.ktor.http.Url

/** Only apply OpenRouter's wire format to its own service/host or an explicit gateway model route. */
internal fun openRouterReasoning(
    isOpenRouterService: Boolean,
    baseUrl: String,
    modelId: String,
    enabled: Boolean,
    providerHint: String = "",
): OpenAICompatibleChatRequestDto.Reasoning? {
    val host = runCatching { Url(baseUrl).host.lowercase() }.getOrDefault("")
    val isOpenRouterModel = Regex("(^|[/:])openrouter([/:]|$)", RegexOption.IGNORE_CASE).containsMatchIn(modelId)
    return if (isOpenRouterService || providerHint.contains("openrouter", ignoreCase = true) || host == "openrouter.ai" || host.endsWith(".openrouter.ai") || isOpenRouterModel) {
        OpenAICompatibleChatRequestDto.Reasoning(enabled = enabled, exclude = false)
    } else {
        null
    }
}
