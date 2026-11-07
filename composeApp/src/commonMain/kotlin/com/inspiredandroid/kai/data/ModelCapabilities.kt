package com.inspiredandroid.kai.data

/**
 * Model IDs (by prefix, lower-cased) that can't drive tool/function-calling loops
 * reliably. Add new small/weak models here when they prove unable to emit well-formed
 * tool_use JSON or to parse tool schemas.
 */
internal val LIMITED_MODELS = listOf(
    "llama3.2:1b",
    "llama3.2:3b",
    "llama3.1:8b",
    "gemma2",
    "gemma:2b",
    "gemma:7b",
    "gemma3",
    "gemma-3",
    "gemma-4-e2b",
    "gemma-4-e4b",
    "phi3:mini",
    "tinyllama",
    "stablelm",
    "codellama",
    "deepseek-coder:1.3b",
    "deepseek-coder:6.7b",
    // Perplexity Sonar models use built-in web search, not OpenAI tool/function calling.
    // Sending a tools[] array is rejected (or silently ignored), so treat them as text-only.
    "sonar",
)

/** True if the model can handle tool_use / tool_result round-trips. */
internal fun supportsTools(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return LIMITED_MODELS.none { lower.startsWith(it) }
}

/**
 * GLM / Zhipu text model ids (normalized: any `provider/` prefix stripped, lower-cased) that
 * DON'T accept image input even though Z.AI's service does — Z.AI serves these text models next
 * to the multimodal GLM-V variants. Sending image content-parts to them returns a hard 400 that
 * then poisons every later turn in the chat (the image stays in history), so we strip images.
 *
 * Exact match only: the vision variants (glm-4.6v, glm-4v-plus, …) share a prefix with the text
 * ones (glm-4.6, glm-4), so prefix matching would wrongly flag them as text-only. DeepSeek, which
 * has no vision models at all, is matched by family prefix in [modelSupportsImages] instead.
 */
internal val TEXT_ONLY_IMAGE_MODELS: Set<String> = setOf(
    // GLM / Zhipu text models. The -v / v-plus variants are multimodal and are
    // deliberately left out so images still reach them.
    "glm-4.6",
    "glm-4.6-air",
    "glm-4.5",
    "glm-4.5-air",
    "glm-4.5-air:free",
    "glm-4.5-x",
    "glm-4-plus",
    "glm-4-plus-0111",
    "glm-4-air",
    "glm-4-airx",
    "glm-4-long",
    "glm-4-flash",
    "glm-4-32b",
    "glm-z1-airx",
    "glm-z1-air",
    "glm-z1-flash",
    "glm-5",
    "glm5",
    "glm-5.1",
    "glm-5.2",
    "glm5.2",
    "glm-5.2-max",
    "glm-5-turbo",
    "glm-4.7",
    "glm4.7",
    "zai-glm-4.7",
    "glm-4.7-flash",
    "chatglm3-6b",
)

/**
 * DeepSeek model ids (normalized: any `provider/` prefix stripped, lower-cased) that accept
 * image input. DeepSeek opened multimodal input on 2026-08-21 with V4-Flash-Vision-Exp and made
 * it native in V4.1-Flash (`deepseek-flash`); the retired vision ids still alias to V4.1-Flash.
 *
 * This is an ALLOWLIST, not a family rule, on purpose: DeepSeek still serves text-only models
 * (notably `deepseek-v4-pro-0813`, and every pre-V4 generation), and sending an image part to one
 * of those is a hard 400 that stays in history and poisons every later turn in the chat.
 * Anything not listed here — including future ids — is treated as text-only until verified.
 */
internal val DEEPSEEK_VISION_MODELS: Set<String> = setOf(
    "deepseek-flash",
    "deepseek-v4.1-flash",
    "deepseek-v4-flash-vision-exp",
    "deepseek-v4-flash",
    "deepseek-vl",
    "deepseek-vl2",
    "deepseek-vl2-tiny",
    "deepseek-vl2-small",
)

internal fun modelSupportsImages(modelId: String): Boolean {
    val key = modelId.substringAfterLast('/').lowercase()
    // DeepSeek is gated by allowlist: most of its catalog is still text-only, so a blanket
    // "assume multimodal" default would break those models with a permanent 400.
    if (key.startsWith("deepseek")) return key in DEEPSEEK_VISION_MODELS
    return key !in TEXT_ONLY_IMAGE_MODELS
}

/**
 * True if a service+model combo is suitable for autonomous/agentic flows —
 * heartbeat, interactive mode, and any future background feature that runs a
 * tool-calling loop without the user present to course-correct.
 *
 * Two gates, both must hold:
 *  - **Service** must be remote: on-device inference (LiteRT) can't run long
 *    agentic loops reliably.
 *  - **Model** must support tools: some small open-weight models don't
 *    (see [LIMITED_MODELS]).
 *
 * If you're filtering a service/model picker for a background feature,
 * prefer this over checking either gate in isolation.
 */
internal fun supportsAgenticFlows(serviceId: String, modelId: String): Boolean = !Service.fromId(serviceId).isOnDevice && supportsTools(modelId)

/**
 * OpenAI GPT families from 5.6 on only support function calling on the Responses API
 * (`/v1/responses`). Chat completions answers them with
 * `400 Function tools with reasoning_effort are not supported for gpt-5.6-terra in
 * /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to
 * 'none'.`
 *
 * These families reason by default, so the rejection lands even though Kai never sends
 * `reasoning_effort` — and the documented chat-completions escape hatch (`reasoning_effort:
 * "none"`) would trade away the reasoning these models are chosen for.
 *
 * Matching on the version rather than a list of prefixes means newer families (GPT-6, …) are
 * routed without a code change. Covers the tier ids (`-sol`, `-terra`, `-luna`), bare aliases
 * (`gpt-5.6`, `gpt-6`), and effort-suffixed variants (`gpt-5.6-luna-xhigh`).
 */
private val GPT_VERSION = Regex("""^gpt-(\d+)(?:\.(\d+))?(?:-|$)""")

internal fun isResponsesApiGptFamily(modelId: String): Boolean {
    val match = GPT_VERSION.find(modelId.substringAfterLast('/').lowercase()) ?: return false
    val major = match.groupValues[1].toInt()
    val minor = match.groupValues[2].toIntOrNull() ?: 0
    return major > 5 || (major == 5 && minor >= 6)
}

/**
 * True when this service+model must talk to OpenAI's Responses API instead of chat completions.
 *
 * Gated on reaching OpenAI directly: aggregators that resell the same models (OpenRouter, AI
 * HubMix, …) translate to the Responses API themselves and only accept chat completions, so
 * routing their ids to `/responses` would break them. The OpenAI-Compatible service qualifies when
 * its base URL points at OpenAI, which is what a user reaching for that service as a workaround
 * would configure.
 */
internal fun requiresResponsesApi(service: Service, modelId: String, baseUrl: String = ""): Boolean {
    if (service.responsesUrl == null) return false
    val isOpenAiEndpoint = service == Service.OpenAI || baseUrl.contains("api.openai.com", ignoreCase = true)
    if (!isOpenAiEndpoint) return false
    return isResponsesApiGptFamily(modelId)
}
