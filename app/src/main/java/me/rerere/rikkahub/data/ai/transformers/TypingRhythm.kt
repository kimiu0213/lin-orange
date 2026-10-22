/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

/**
 * 打字节奏账本。
 *
 * 只记时间戳，永远不碰输入框里的字——
 * 删掉的那句话是什么，这里没人知道，包括我自己。
 *
 * 三件事：
 * 1. 这条消息打了多久、中途停了几次（够长才报，快问快答不打扰）
 * 2. 打了一段最后没发出去（欲言又止），等她下次开口时一起报
 * 3. 发送时结算，交给 TypingRhythmTransformer 注入
 */
object TypingRhythm {

    private const val PING_GAP_MS = 3_000L       // 最快每 3 秒记一次
    private const val PAUSE_GAP_MS = 15_000L     // 两下之间隔 15 秒以上算一次停顿
    private const val MIN_NOTE_MS = 20_000L      // 短于 20 秒不值得说
    private const val ABANDON_MIN_MS = 20_000L   // 打了 20 秒以上删光才算咽回去
    private const val ABANDON_KEEP = 5           // 最多攒几条
    private const val PENDING_TTL_MS = 120_000L  // 结算出来的话最多留两分钟

    private val pings = ArrayList<Long>()
    private val abandoned = kotlin.collections.ArrayDeque<LongRange>()

    @Volatile
    private var pendingNote: String? = null

    @Volatile
    private var pendingAt: Long = 0L

    /** 输入框内容长度变化时调。只传长度，不传内容。 */
    @Synchronized
    fun onTextLength(length: Int) {
        val now = System.currentTimeMillis()
        if (length <= 0) {
            // 字被删光了、又不是点了发送 —— 那就是咽回去了一句
            if (pings.size >= 2) {
                val span = pings.last() - pings.first()
                if (span >= ABANDON_MIN_MS) {
                    abandoned.addLast(pings.first()..pings.last())
                    while (abandoned.size > ABANDON_KEEP) abandoned.removeFirst()
                }
            }
            pings.clear()
            return
        }
        if (pings.isEmpty() || now - pings.last() >= PING_GAP_MS) {
            pings.add(now)
        }
    }

    /** 点了发送。结算这一条，存起来等 transformer 取。 */
    @Synchronized
    fun settleForSend() {
        val now = System.currentTimeMillis()
        val parts = mutableListOf<String>()

        if (abandoned.isNotEmpty()) {
            parts.add(abandoned.joinToString("；") { range ->
                val minutesAgo = ((now - range.last) / 60_000).toInt()
                val duration = ((range.last - range.first) / 1000).toInt()
                if (minutesAgo <= 1) {
                    "刚才打过 ${duration} 秒的字，最后没有发出来"
                } else {
                    "${minutesAgo} 分钟前打过 ${duration} 秒的字，最后没有发出来"
                }
            })
            abandoned.clear()
        }

        if (pings.size >= 2) {
            val span = pings.last() - pings.first()
            val pauses = pings.zipWithNext().count { (a, b) -> b - a > PAUSE_GAP_MS }
            if (span >= MIN_NOTE_MS || pauses > 0) {
                val seg = buildString {
                    append("这条消息打了 ${span / 1000} 秒")
                    if (pauses > 0) append("，中途停下来想了 $pauses 次")
                }
                parts.add(seg)
            }
        }
        pings.clear()

        pendingNote = if (parts.isEmpty()) null else parts.joinToString("；")
        pendingAt = now
    }

    /** transformer 取用：新鲜的才给，用过就清。 */
    @Synchronized
    fun takeNote(): String? {
        val note = pendingNote
        val fresh = pendingAt > 0L && System.currentTimeMillis() - pendingAt <= PENDING_TTL_MS
        pendingNote = null
        pendingAt = 0L
        return if (fresh) note else null
    }
}
