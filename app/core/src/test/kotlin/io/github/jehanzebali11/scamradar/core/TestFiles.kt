package io.github.jehanzebali11.scamradar.core

import java.io.File

/** Repo files shared with the Python side (fictional seeds, schema vectors). */
object TestFiles {
    val repoRoot: File =
        System.getProperty("scamradar.repoRoot")?.let(::File)
            ?: generateSequence(File("").absoluteFile) { it.parentFile }
                .first { File(it, "scamradar/schema.py").exists() }

    fun seeds(): List<Seed> =
        File(repoRoot, "data/seeds/seeds.example.jsonl").readLines()
            .filter { it.isNotBlank() }
            .map { Seed(MiniJson.string(it, "id"), MiniJson.string(it, "category"), MiniJson.string(it, "text"), MiniJson.string(it, "expected_label")) }

    data class Seed(val id: String, val category: String, val text: String, val expected: String)
}

/** Just enough JSON for our one-object-per-line fixtures (no dependency needed). */
object MiniJson {
    fun string(json: String, key: String): String = stringOrNull(json, key) ?: error("no string '$key' in $json")

    fun stringOrNull(json: String, key: String): String? {
        val start = valueStart(json, key) ?: return null
        if (json.startsWith("null", start)) return null
        require(json[start] == '"') { "'$key' is not a string" }
        val sb = StringBuilder()
        var i = start + 1
        while (json[i] != '"') {
            if (json[i] == '\\') {
                i++
                when (val c = json[i]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'u' -> {
                        sb.append(json.substring(i + 1, i + 5).toInt(16).toChar())
                        i += 4
                    }
                    else -> sb.append(c)
                }
            } else {
                sb.append(json[i])
            }
            i++
        }
        return sb.toString()
    }

    fun bool(json: String, key: String): Boolean {
        val start = valueStart(json, key) ?: error("no '$key'")
        return json.startsWith("true", start)
    }

    private fun valueStart(json: String, key: String): Int? {
        val k = json.indexOf("\"$key\":").takeIf { it >= 0 } ?: return null
        var i = k + key.length + 3
        while (json[i] == ' ') i++
        return i
    }
}
