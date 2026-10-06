import sys

file_path = "shared/src/commonMain/kotlin/com/lucent/app/data/Recurrence.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("import java.util.Calendar", "import kotlinx.datetime.*")

# Replace advance method body:
new_advance = """    fun advance(fromMillis: Long, rule: RepeatRule): Long {
        if (rule == RepeatRule.NONE) return fromMillis
        val instant = Instant.fromEpochMilliseconds(fromMillis)
        val tz = TimeZone.currentSystemDefault()
        val nextInstant = when (rule) {
            RepeatRule.DAILY -> instant.plus(DateTimePeriod(days = 1), tz)
            RepeatRule.WEEKLY -> instant.plus(DateTimePeriod(days = 7), tz)
            RepeatRule.MONTHLY -> instant.plus(DateTimePeriod(months = 1), tz)
            RepeatRule.YEARLY -> instant.plus(DateTimePeriod(years = 1), tz)
            RepeatRule.NONE -> instant
        }
        return nextInstant.toEpochMilliseconds()
    }"""

old_advance = """    fun advance(fromMillis: Long, rule: RepeatRule): Long {
        if (rule == RepeatRule.NONE) return fromMillis
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (rule) {
            RepeatRule.DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
            RepeatRule.WEEKLY -> cal.add(Calendar.DAY_OF_MONTH, 7)
            RepeatRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatRule.YEARLY -> cal.add(Calendar.YEAR, 1)
            RepeatRule.NONE -> Unit
        }
        return cal.timeInMillis
    }"""

content = content.replace(old_advance, new_advance)

with open(file_path, "w") as f:
    f.write(content)

