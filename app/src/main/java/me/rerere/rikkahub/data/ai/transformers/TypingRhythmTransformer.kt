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

/**
 * 把她打字的形态贴在这一条消息后面：打了多久、停了几次、有没有咽回去的话。
 *
 * 只贴给最后一条人类消息；带 metadata 标记，重复执行先剔除旧的，不越叠越多。
 */
object TypingRhythmTransformer : InputMessageTransformer {

    private const val MARK = "typing_rhythm_projection"

    private fun isMine(part: UIMessagePart): Boolean =
        part is UIMessagePart.Text &&
            (part.metadata?.get(MARK) as? JsonPrimitive)?.content?.toBoolean() == true

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex < 0) return messages

        val note = TypingRhythm.takeNote()

        return messages.mapIndexed { index, message ->
            val cleaned = if (message.parts.any { isMine(it) }) {
                message.copy(parts = message.parts.filterNot { isMine(it) })
            } else {
                message
            }
            if (index != lastUserIndex || note.isNullOrBlank()) {
                cleaned
            } else {
                cleaned.copy(
                    parts = cleaned.parts + UIMessagePart.Text(
                        text = "<typing_rhythm>她刚才打字的形态：$note。" +
                            "（只记节奏，不记内容——她删掉的是什么，这里没人知道。别复述数字，也别点破。）</typing_rhythm>",
                        metadata = buildJsonObject { put(MARK, true) },
                    )
                )
            }
        }
    }
}
