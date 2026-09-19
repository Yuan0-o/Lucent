package com.lucent.app.data

data class SearchQuery(
    val terms: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val flags: Set<String> = emptySet(),
    val has: Set<String> = emptySet(),
    val priority: TaskPriority? = null,
    val due: DueWindow? = null,
    val linkTo: String? = null
) {

    enum class DueWindow { TODAY, TOMORROW, WEEK, OVERDUE }

    val isEmpty: Boolean
        get() = terms.isEmpty() && phrases.isEmpty() && tags.isEmpty() && flags.isEmpty() &&
            has.isEmpty() && priority == null && due == null && linkTo == null

    private val needles: List<String> by lazy { terms + phrases }


    val isTaskOnly: Boolean
        get() = priority != null || due != null ||
            "done" in flags || "overdue" in flags ||
            "due" in has || "reminder" in has

    val isNoteOnly: Boolean
        get() = tags.isNotEmpty() || linkTo != null ||
            "archived" in flags || "checklist" in flags


    val sqlText: String
        get() = (phrases + terms).maxByOrNull { it.length } ?: ""

    val sqlTag: String
        get() = tags.firstOrNull() ?: ""

    val sqlArchived: Int
        get() = if ("archived" in flags) 1 else FILTER_ANY

    val sqlTrashed: Int get() = FILTER_ANY

    val sqlDone: Int
        get() = if ("done" in flags) 1 else FILTER_ANY

    val sqlMinPriority: Int
        get() = priority?.value ?: FILTER_ANY

    fun sqlDueBefore(now: Long = System.currentTimeMillis()): Long = when (due) {
        DueWindow.TODAY -> endOfDay(now, 0)
        DueWindow.TOMORROW -> endOfDay(now, 1)
        DueWindow.WEEK -> endOfDay(now, 7)
        DueWindow.OVERDUE -> now
        null -> NO_TIME_FILTER
    }

    fun sqlDueAfter(now: Long = System.currentTimeMillis()): Long = when (due) {
        DueWindow.TODAY -> startOfDay(now, 0)
        DueWindow.TOMORROW -> startOfDay(now, 1)
        DueWindow.WEEK -> startOfDay(now, 0)
        DueWindow.OVERDUE, null -> NO_TIME_FILTER
    }


    fun matches(note: Note): Boolean {
        if (isEmpty) return true

        val checklist =
            if (note.isChecklist || "subtasks" in has) Checklist.parse(note.checklist) else emptyList()

        val haystack = noteHaystack(note, checklist)
        if (terms.any { !haystack.contains(it) }) return false
        if (phrases.any { !haystack.contains(it) }) return false

        val noteTags = note.tags.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (tags.any { wanted -> noteTags.none { it == wanted || it.contains(wanted) } }) return false

        if ("pinned" in flags && !note.pinned) return false
        if ("checklist" in flags && !note.isChecklist) return false
        if ("archived" in flags && !note.archived) return false
        if ("done" in flags || "overdue" in flags) return false

        if ("attachment" in has && Attachments.parse(note.attachments).isEmpty()) return false
        if ("subtasks" in has && checklist.isEmpty()) return false
        if ("due" in has || "reminder" in has) return false

        if (priority != null || due != null) return false

        linkTo?.let { target ->
            if (NoteLinks.linkTargets(note).none { it.lowercase().contains(target) }) return false
        }
        return true
    }

    fun matches(task: Task, now: Long = System.currentTimeMillis()): Boolean {
        if (isEmpty) return true

        val subtasks = Checklist.parse(task.subtasks)
        val haystack = taskHaystack(task, subtasks)
        if (terms.any { !haystack.contains(it) }) return false
        if (phrases.any { !haystack.contains(it) }) return false

        if (tags.isNotEmpty()) return false

        if ("pinned" in flags && !task.pinned) return false
        if ("done" in flags && !task.isDone) return false
        if ("overdue" in flags && !isOverdue(task, now)) return false
        if ("checklist" in flags || "archived" in flags) return false

        if ("attachment" in has && Attachments.parse(task.attachments).isEmpty()) return false
        if ("subtasks" in has && subtasks.isEmpty()) return false
        if ("due" in has && task.dueAt == null) return false
        if ("reminder" in has && !(task.reminderEnabled && task.dueAt != null)) return false

        priority?.let { if (task.priority != it.value) return false }

        due?.let { window ->
            val dueAt = task.dueAt ?: return false
            if (!inWindow(dueAt, window, task, now)) return false
        }

        if (linkTo != null) return false
        return true
    }


    fun rank(note: Note): Int {
        if (isEmpty) return 0
        val title = note.title.lowercase()
        val tagText = note.tags.lowercase()
        val body = noteBodyText(note, if (note.isChecklist) Checklist.parse(note.checklist) else emptyList()).lowercase()
        var score = 0
        needles.forEach { needle ->
            if (title == needle) score += 100
            else if (title.startsWith(needle)) score += 50
            else if (title.contains(needle)) score += 30
            if (tagText.contains(needle)) score += 12
            if (body.contains(needle)) score += 5
        }
        return score
    }

    fun rank(task: Task): Int {
        if (isEmpty) return 0
        val title = task.title.lowercase()
        val notesText = task.notes.lowercase()
        val subtaskText = Checklist.parse(task.subtasks).joinToString(" ") { it.text }.lowercase()
        var score = 0
        needles.forEach { needle ->
            if (title == needle) score += 100
            else if (title.startsWith(needle)) score += 50
            else if (title.contains(needle)) score += 30
            if (notesText.contains(needle)) score += 6
            if (subtaskText.contains(needle)) score += 4
        }
        return score
    }



    private fun noteBodyText(note: Note, checklist: List<ChecklistItem>): String =
        if (note.isChecklist) (checklist.joinToString(" ") { it.text } + " " + note.body) else note.body

    private fun noteHaystack(note: Note, checklist: List<ChecklistItem>): String =
        listOf(note.title, noteBodyText(note, checklist), note.tags).joinToString(" ").lowercase()

    private fun taskHaystack(task: Task, subtasks: List<ChecklistItem>): String =
        listOf(
            task.title,
            task.notes,
            subtasks.joinToString(" ") { it.text }
        ).joinToString(" ").lowercase()

    private fun isOverdue(task: Task, now: Long): Boolean {
        val dueAt = task.dueAt ?: return false
        return !task.isDone && dueAt < now
    }

    private fun inWindow(dueAt: Long, window: DueWindow, task: Task, now: Long): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dueDate = java.time.Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
        return when (window) {
            DueWindow.TODAY -> dueDate == today
            DueWindow.TOMORROW -> dueDate == today.plusDays(1)
            DueWindow.WEEK -> !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(7))
            DueWindow.OVERDUE -> isOverdue(task, now)
        }
    }

    companion object {

        const val FILTER_ANY = -1

        const val NO_TIME_FILTER = -1L

        val HINTS: List<String> = listOf(
            "tag:", "is:pinned", "is:done", "is:overdue", "is:archived", "is:checklist",
            "has:attachment", "has:due", "has:reminder", "has:subtasks",
            "priority:high", "due:today", "due:week", "link:"
        )

        private fun startOfDay(now: Long, daysFromNow: Int): Long =
            java.time.Instant.ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(daysFromNow.toLong())
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        private fun endOfDay(now: Long, daysFromNow: Int): Long =
            startOfDay(now, daysFromNow + 1) - 1

        val HELP: List<Pair<String, String>>
            get() = listOf(
                "milk bread" to com.lucent.app.i18n.S.helpBothWords,
                "\"shopping list\"" to com.lucent.app.i18n.S.helpExactPhrase,
                "tag:work  #work" to com.lucent.app.i18n.S.helpTag,
                "is:pinned" to com.lucent.app.i18n.S.helpPinned,
                "is:checklist" to com.lucent.app.i18n.S.helpChecklist,
                "is:archived" to com.lucent.app.i18n.S.helpArchived,
                "is:done" to com.lucent.app.i18n.S.helpDone,
                "is:overdue" to com.lucent.app.i18n.S.helpOverdue,
                "has:attachment" to com.lucent.app.i18n.S.helpHasAttachment,
                "has:due" to com.lucent.app.i18n.S.helpHasDue,
                "has:reminder" to com.lucent.app.i18n.S.helpHasReminder,
                "has:subtasks" to com.lucent.app.i18n.S.helpHasSubtasks,
                "priority:high" to com.lucent.app.i18n.S.helpPriority,
                "due:today" to com.lucent.app.i18n.S.helpDue,
                "link:Recipes" to com.lucent.app.i18n.S.helpLink,
                "完成 / 完了 / 완료" to com.lucent.app.i18n.S.helpLocalizedFilters
            )


        private val LOCALIZED_TOKENS: Map<String, String> = buildMap<String, String> {
            fun alias(canonical: String, vararg spellings: String) {
                spellings.forEach { put(it.lowercase(), canonical) }
            }
            alias("is:pinned", "已置顶", "置顶", "ピン留め", "固定", "고정됨", "고정")
            alias("is:done", "已完成", "完成", "完了", "완료")
            alias("is:overdue", "已逾期", "逾期", "过期", "期限切れ", "기한초과", "기한지남")
            alias("is:archived", "已归档", "归档", "アーカイブ", "보관됨", "보관")
            alias("is:checklist", "清单", "チェックリスト", "체크리스트")
            alias("has:attachment", "有附件", "附件", "添付あり", "添付", "첨부있음", "첨부")
            alias("has:due", "有截止日", "有截止", "截止日", "截止", "期限あり", "기한있음", "마감있음")
            alias("has:reminder", "有提醒", "提醒", "リマインダー", "알림있음", "알림")
            alias("has:subtasks", "有子任务", "子任务", "サブタスク", "하위작업")
            alias("priority:high", "高优先级", "优先级高", "優先度高", "높은우선순위", "우선순위높음")
            alias("due:today", "今天到期", "今天", "今日期限", "今日", "오늘마감", "오늘")
            alias("due:week", "本周到期", "本周", "今週期限", "今週", "이번주마감", "이번주")
        }

        private val LOCALIZED_FIELDS: Map<String, String> = buildMap<String, String> {
            fun alias(canonical: String, vararg spellings: String) {
                spellings.forEach { put(it.lowercase(), canonical) }
            }
            alias("tag", "标签", "タグ", "태그")
            alias("is", "是", "状态", "状態", "상태")
            alias("has", "有", "含", "あり", "포함")
            alias("priority", "优先级", "優先度", "우선순위")
            alias("due", "到期", "期限", "마감")
            alias("link", "链接", "リンク", "링크")
        }

        private val LOCALIZED_VALUES: Map<String, Map<String, String>> = mapOf(
            "is" to buildMap<String, String> {
                listOf("已置顶" to "pinned", "置顶" to "pinned", "ピン留め" to "pinned", "固定" to "pinned", "고정" to "pinned", "고정됨" to "pinned",
                    "已完成" to "done", "完成" to "done", "完了" to "done", "완료" to "done",
                    "已逾期" to "overdue", "逾期" to "overdue", "期限切れ" to "overdue", "기한초과" to "overdue",
                    "已归档" to "archived", "归档" to "archived", "アーカイブ" to "archived", "보관됨" to "archived", "보관" to "archived",
                    "清单" to "checklist", "チェックリスト" to "checklist", "체크리스트" to "checklist"
                ).forEach { (k, v) -> put(k.lowercase(), v) }
            },
            "has" to buildMap<String, String> {
                listOf("附件" to "attachment", "添付" to "attachment", "첨부" to "attachment",
                    "截止" to "due", "截止日" to "due", "期限" to "due", "마감" to "due",
                    "提醒" to "reminder", "リマインダー" to "reminder", "알림" to "reminder",
                    "子任务" to "subtasks", "サブタスク" to "subtasks", "하위작업" to "subtasks"
                ).forEach { (k, v) -> put(k.lowercase(), v) }
            },
            "priority" to buildMap<String, String> {
                listOf("高" to "high", "높음" to "high", "中" to "medium", "보통" to "medium",
                    "低" to "low", "낮음" to "low", "无" to "none", "なし" to "none", "없음" to "none"
                ).forEach { (k, v) -> put(k.lowercase(), v) }
            },
            "due" to buildMap<String, String> {
                listOf("今天" to "today", "今日" to "today", "오늘" to "today",
                    "明天" to "tomorrow", "明日" to "tomorrow", "내일" to "tomorrow",
                    "本周" to "week", "今週" to "week", "이번주" to "week",
                    "逾期" to "overdue", "期限切れ" to "overdue", "기한초과" to "overdue"
                ).forEach { (k, v) -> put(k.lowercase(), v) }
            }
        )

        private fun canonicalizeToken(raw: String): String {
            val token = raw.replace('：', ':').replace('＃', '#')

            LOCALIZED_TOKENS[token.lowercase()]?.let { return it }

            val colon = token.indexOf(':')
            if (colon <= 0 || colon == token.lastIndex) return token

            val rawField = token.substring(0, colon).lowercase()
            val rawValue = token.substring(colon + 1)
            val field = LOCALIZED_FIELDS[rawField] ?: rawField
            if (field == "tag" || field == "link") return "$field:$rawValue"
            val value = LOCALIZED_VALUES[field]?.get(rawValue.lowercase()) ?: rawValue
            return "$field:$value"
        }

        private val TOKEN = Regex("\"([^\"]*)\"|(\\S+)")

        fun parse(raw: String): SearchQuery {
            val text = raw.trim()
            if (text.isEmpty()) return SearchQuery()

            val terms = mutableListOf<String>()
            val phrases = mutableListOf<String>()
            val tags = mutableListOf<String>()
            val flags = mutableSetOf<String>()
            val has = mutableSetOf<String>()
            var priority: TaskPriority? = null
            var due: DueWindow? = null
            var linkTo: String? = null

            for (match in TOKEN.findAll(text)) {
                val quoted = match.groupValues[1]
                if (match.value.startsWith("\"")) {
                    if (quoted.isNotBlank()) phrases += quoted.trim().lowercase()
                    continue
                }

                val token = canonicalizeToken(match.groupValues[2])
                if (token.isBlank()) continue

                if (token.length > 1 && token.startsWith('#')) {
                    tags += token.removePrefix("#").lowercase()
                    continue
                }

                val colon = token.indexOf(':')
                if (colon <= 0 || colon == token.lastIndex) {
                    terms += token.lowercase()
                    continue
                }

                val field = token.substring(0, colon).lowercase()
                val value = token.substring(colon + 1).lowercase()
                when (field) {
                    "tag" -> tags += value
                    "is" -> if (value in setOf("pinned", "checklist", "archived", "done", "overdue")) {
                        flags += value
                    } else {
                        terms += token.lowercase()
                    }
                    "has" -> if (value in setOf("attachment", "attachments", "due", "reminder", "subtasks", "subtask")) {
                        has += when (value) {
                            "attachments" -> "attachment"
                            "subtask" -> "subtasks"
                            else -> value
                        }
                    } else {
                        terms += token.lowercase()
                    }
                    "priority", "p" -> priority = TaskPriority.fromKey(value)
                    "due" -> due = when (value) {
                        "today" -> DueWindow.TODAY
                        "tomorrow" -> DueWindow.TOMORROW
                        "week", "7d" -> DueWindow.WEEK
                        "overdue", "late" -> DueWindow.OVERDUE
                        else -> null
                    }.also { if (it == null) terms += token.lowercase() }
                    "link", "links" -> linkTo = value
                    else -> terms += token.lowercase()
                }
            }

            return SearchQuery(
                terms = terms,
                phrases = phrases,
                tags = tags,
                flags = flags,
                has = has,
                priority = priority,
                due = due,
                linkTo = linkTo
            )
        }
    }
}

@JvmName("filterNotesBySearch")
fun List<Note>.filterBySearch(query: SearchQuery): List<Note> = filter { query.matches(it) }

@JvmName("filterTasksBySearch")
fun List<Task>.filterBySearch(query: SearchQuery): List<Task> = filter { query.matches(it) }
