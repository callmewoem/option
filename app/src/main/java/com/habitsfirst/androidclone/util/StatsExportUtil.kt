package com.habitsfirst.androidclone.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.habitsfirst.androidclone.data.repository.HabitCompletionStat
import com.habitsfirst.androidclone.data.repository.HabitRepository
import com.habitsfirst.androidclone.data.repository.TodoRepository
import com.habitsfirst.androidclone.domain.model.Todo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One day's gating-completion summary in an export -- one CSV row, or one entry in the
 * JSON export's `"days"` array.
 */
data class DayExportRow(
    val date: String,
    val completionFraction: Float,
    val isPerfectDay: Boolean,
    val streakBroken: Boolean,
    val streakBreakReason: String?,
)

/**
 * Everything a data export bundles together, already fetched from the repositories --
 * the JSON export serializes all of this; the CSV export only uses [dayRows]. Kept
 * separate from the repository calls themselves so [StatsExportUtil.buildCsv] and
 * [StatsExportUtil.buildJson] are plain functions of already-fetched data, with nothing
 * Android- or Room-specific to mock in a test.
 */
data class StatsExportData(
    val startDate: String,
    val endDate: String,
    val dayRows: List<DayExportRow>,
    val habitStats: List<HabitCompletionStat>,
    val todos: List<Todo>,
)

/** A finished export file, ready to hand to [Intent.ACTION_SEND] via [exportShareIntent]. */
data class ExportedFile(val uri: Uri, val mimeType: String)

/**
 * Exports the user's habit/todo/streak history as CSV or JSON, for self-review or
 * sharing outside the app (e.g. with a psychiatrist). Reads only data every other
 * stats screen already reads -- day scores, per-habit completion rates, streak-scar
 * dates/reasons, and todos -- nothing new is tracked to support this.
 *
 * Fetching (this class) is kept separate from building the file contents
 * ([buildCsv]/[buildJson], on the companion object) so the building logic is a plain
 * function of already-fetched data and easy to unit-test without Room or a Context.
 */
@Singleton
class StatsExportUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habitRepository: HabitRepository,
    private val todoRepository: TodoRepository,
) {
    /**
     * Fetches everything in [startDate]..[endDate] (inclusive, ISO "yyyy-MM-dd") and
     * assembles it into [StatsExportData]. The four repository reads don't depend on
     * each other, so they run concurrently via [async] rather than one after another.
     */
    suspend fun collectExportData(startDate: String, endDate: String): StatsExportData = coroutineScope {
        val dayScoresDeferred = async { habitRepository.getDayScoresInRange(startDate, endDate) }
        val scarsDeferred = async { habitRepository.getStreakScarsInRange(startDate, endDate) }
        val habitStatsDeferred = async { habitRepository.getHabitCompletionStats(startDate, endDate) }
        val todosDeferred = async { todoRepository.getTodosInRange(startDate, endDate) }

        val dayScores = dayScoresDeferred.await()
        val scarReasons = scarsDeferred.await().associate { it.date to it.reason }

        // getDayScoresInRange only returns dates with recorded activity, so a day with
        // nothing logged (no habit existed yet, or the app simply wasn't opened) would
        // silently be missing from the export -- filling every calendar date in the
        // window keeps the CSV/JSON one-row-per-day, matching what the heatmap shows
        // for an empty cell (an implicit 0).
        val rows = mutableListOf<DayExportRow>()
        var cursor = DateProvider.fromDateString(startDate)
        val end = DateProvider.fromDateString(endDate)
        while (!cursor.isAfter(end)) {
            val dateStr = DateProvider.toDateString(cursor)
            val fraction = dayScores[dateStr] ?: 0f
            val reason = scarReasons[dateStr]
            rows += DayExportRow(
                date = dateStr,
                completionFraction = fraction,
                isPerfectDay = reason == null && fraction >= 1f,
                streakBroken = reason != null,
                streakBreakReason = reason,
            )
            cursor = cursor.plusDays(1)
        }

        StatsExportData(
            startDate = startDate,
            endDate = endDate,
            dayRows = rows,
            habitStats = habitStatsDeferred.await(),
            todos = todosDeferred.await(),
        )
    }

    /** Fetches [startDate]..[endDate], writes it as CSV under the export cache dir, and returns a shareable `content://` [Uri]. */
    suspend fun exportCsv(startDate: String, endDate: String): ExportedFile = withContext(Dispatchers.IO) {
        val csv = buildCsv(collectExportData(startDate, endDate).dayRows)
        write(csv, "locke_stats_${startDate}_to_$endDate.csv", "text/csv")
    }

    /** Fetches [startDate]..[endDate], writes it as JSON under the export cache dir, and returns a shareable `content://` [Uri]. */
    suspend fun exportJson(startDate: String, endDate: String): ExportedFile = withContext(Dispatchers.IO) {
        val json = buildJson(collectExportData(startDate, endDate))
        write(json, "locke_export_${startDate}_to_$endDate.json", "application/json")
    }

    /** Writes [content] under the dedicated export cache dir, first purging any stale export left over from an earlier share (see [STALE_EXPORT_AGE_MILLIS]) -- unlike [ImageStore]'s caller-owned scratch files, nothing else is responsible for cleaning these up. Called on [Dispatchers.IO] by [exportCsv]/[exportJson]; blocking disk I/O here is intentional. */
    private fun write(content: String, fileName: String, mimeType: String): ExportedFile {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        purgeStaleExports(dir)
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return ExportedFile(uri, mimeType)
    }

    private fun purgeStaleExports(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_EXPORT_AGE_MILLIS
        dir.listFiles()?.forEach { file -> if (file.lastModified() < cutoff) file.delete() }
    }

    companion object {
        /** Dedicated cache subdirectory for exports -- see the matching `<cache-path>` entry in file_paths.xml. */
        private const val EXPORT_DIR = "stats_export"

        /** How long a written export is kept around before [purgeStaleExports] deletes it on the next export -- long enough to outlive picking a target app from the share sheet, short enough that repeated exports don't pile up in the cache dir forever. */
        private const val STALE_EXPORT_AGE_MILLIS = 60 * 60 * 1000L

        /** One row per day: date, gating-completion fraction, whether it was a perfect day, and whether/why the streak broke. Pure -- no Android or Room dependency, so it's directly unit-testable. */
        fun buildCsv(rows: List<DayExportRow>): String {
            val header = "date,completion_fraction,is_perfect_day,streak_broken,streak_break_reason"
            val lines = rows.map { row ->
                listOf(
                    row.date,
                    String.format(Locale.ROOT, "%.3f", row.completionFraction),
                    row.isPerfectDay.toString(),
                    row.streakBroken.toString(),
                    csvField(row.streakBreakReason.orEmpty()),
                ).joinToString(",")
            }
            return (listOf(header) + lines).joinToString(separator = "\n", postfix = "\n")
        }

        /** A fuller export: per-day scores, per-habit completion stats, and todos in range. Pure, like [buildCsv]. */
        fun buildJson(data: StatsExportData): String {
            val days = data.dayRows.joinToString(separator = ",\n") { row ->
                jsonObject(
                    "date" to jsonString(row.date),
                    "completionFraction" to row.completionFraction.toString(),
                    "isPerfectDay" to row.isPerfectDay.toString(),
                    "streakBroken" to row.streakBroken.toString(),
                    "streakBreakReason" to (row.streakBreakReason?.let { jsonString(it) } ?: "null"),
                )
            }
            val habits = data.habitStats.joinToString(separator = ",\n") { stat ->
                jsonObject(
                    "name" to jsonString(stat.habit.name),
                    "completionRate" to stat.rate.toString(),
                    "completedCount" to stat.completedCount.toString(),
                    "totalDays" to stat.totalDays.toString(),
                )
            }
            val todos = data.todos.joinToString(separator = ",\n") { todo ->
                jsonObject(
                    "title" to jsonString(todo.title),
                    "date" to jsonString(todo.date),
                    "isDone" to todo.isDone.toString(),
                    "createdAtEpochMillis" to todo.createdAtEpochMillis.toString(),
                )
            }
            return """
                |{
                |  "startDate": ${jsonString(data.startDate)},
                |  "endDate": ${jsonString(data.endDate)},
                |  "days": [
                |${days.prependIndent("    ")}
                |  ],
                |  "habits": [
                |${habits.prependIndent("    ")}
                |  ],
                |  "todos": [
                |${todos.prependIndent("    ")}
                |  ]
                |}
                |
            """.trimMargin()
        }

        private fun jsonObject(vararg fields: Pair<String, String>): String =
            "{ " + fields.joinToString(", ") { (key, value) -> "${jsonString(key)}: $value" } + " }"

        private fun csvField(value: String): String =
            if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }

        /** Minimal JSON string escaping -- hand-rolled rather than pulling in org.json/kotlinx.serialization, since these fields (names, task titles) are simple free text. */
        private fun jsonString(value: String): String {
            val escaped = buildString {
                for (c in value) {
                    when (c) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
                    }
                }
            }
            return "\"$escaped\""
        }
    }
}

/** An `ACTION_SEND` chooser for a just-written export file, granting the receiving app read access to [exported]'s content:// [Uri]. */
fun exportShareIntent(exported: ExportedFile): Intent {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = exported.mimeType
        putExtra(Intent.EXTRA_STREAM, exported.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(sendIntent, "Export Locke data")
}
