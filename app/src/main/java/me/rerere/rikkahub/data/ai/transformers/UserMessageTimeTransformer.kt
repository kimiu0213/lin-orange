/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

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
    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm:ss")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val zone = ZoneId.systemDefault()
        return messages.map { message ->
            if (message.role != MessageRole.USER) return@map message

            val cleaned = message.parts.filterNot { part ->
                part is UIMessagePart.Text &&
                    (part.metadata?.get(MARK) as? JsonPrimitive)?.content?.toBoolean() == true
            }
            val stamp = runCatching {
                message.createdAt.toJavaInstant().atZone(zone).format(FORMAT)
            }.getOrNull() ?: return@map message

            message.copy(
                parts = cleaned + UIMessagePart.Text(
                    text = "<user_message_time>这条消息发送于 $stamp（${zone.id}）。这是它的历史时点，不是现在。</user_message_time>",
                    metadata = buildJsonObject { put(MARK, true) },
                )
            )
        }
    }
}
