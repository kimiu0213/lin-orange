/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 给每条人类消息末尾附上它自己的发送时刻。
 *
 * 要点：
 * 1. 用消息自身的 createdAt，不取「现在」——每次请求渲染结果一致，不会打烂 prompt 缓存。
 * 2. 只在发请求时注入，不改存档。
 * 3. 注入的那一段带 metadata 标记；重复执行时先剔除旧的，不会越叠越多。
 */
object UserMessageTimeTransformer : InputMessageTransformer {

    private const val MARK = "user_message_time_projection"

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val zoneId = TimeZone.currentSystemDefault().id
        return messages.map { message ->
            if (message.role != MessageRole.USER) return@map message

            val cleaned = message.parts.filterNot { part ->
                part is UIMessagePart.Text &&
                    (part.metadata?.get(MARK) as? JsonPrimitive)?.content?.toBoolean() == true
            }
            // createdAt 是本地时间（LocalDateTime），形如 2026-10-16T23:00:05.503575
            val stamp = runCatching {
                message.createdAt.toString().substringBefore('.').replace('T', ' ')
            }.getOrNull().let { if (it.isNullOrBlank()) return@map message else it }

            message.copy(
                parts = cleaned + UIMessagePart.Text(
                    text = "<user_message_time>这条消息发送于 $stamp（$zoneId）。这是它的历史时点，不是现在。</user_message_time>",
                    metadata = buildJsonObject { put(MARK, true) },
                )
            )
        }
    }
}
