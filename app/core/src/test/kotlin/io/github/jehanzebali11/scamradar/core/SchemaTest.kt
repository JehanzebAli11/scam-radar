package io.github.jehanzebali11.scamradar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class SchemaTest {
    @Test
    fun `shared vectors match schema py`() {
        val file = File(TestFiles.repoRoot, "tests/fixtures/schema_vectors.jsonl")
        val lines = file.readLines().filter { it.isNotBlank() }
        assertTrue("expected vectors", lines.size > 20)
        for (json in lines) {
            val line = MiniJson.string(json, "line")
            val strictValid = MiniJson.bool(json, "strict_valid")
            val lenient = MiniJson.stringOrNull(json, "lenient")
            assertEquals("strict validity of ${line.quoted()}", strictValid, CompactLine.validate(line).isEmpty())
            assertEquals("lenient parse of ${line.quoted()}", lenient, CompactLine.parseOrNull(line)?.toLine())
            if (strictValid) {
                assertEquals(line, CompactLine.parse(line).toLine())
            } else {
                try {
                    CompactLine.parse(line, strict = true)
                    fail("strict parse should fail for ${line.quoted()}")
                } catch (e: SchemaException) {
                    assertTrue(e.errors.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun `every seed expected label parses strictly`() {
        for (seed in TestFiles.seeds()) {
            assertEquals(seed.id, seed.expected, CompactLine.parse(seed.expected).toLine())
        }
    }

    @Test
    fun `error messages name the problem`() {
        assertTrue(CompactLine.validate("scam|none|none").any { "requires a scam_type" in it })
        assertTrue(CompactLine.validate("scam|code_theft|urgency,asks_for_code").any { "canonical order" in it })
        assertTrue(CompactLine.validate("safe|none|none,urgency").any { "cannot be combined" in it })
    }

    @Test
    fun `format writes canonical flag order`() {
        val p = Prediction(Label.SCAM, ScamType.OTHER, setOf(Flag.CLAIMS_OFFICIAL, Flag.ASKS_FOR_CODE))
        assertEquals("scam|other|asks_for_code,claims_official", p.toLine())
        assertEquals("safe|none|none", Prediction(Label.SAFE).toLine())
    }

    @Test
    fun `parseOrNull returns null on garbage`() {
        assertNull(CompactLine.parseOrNull("<end_of_turn>"))
        assertNull(CompactLine.parseOrNull("scam|code_theft|asks_for_pin"))
    }

    @Test
    fun `prompt matches training prompt`() {
        assertEquals("Classify this message:\nhello", Prompt.userContent("hello"))
    }

    private fun String.quoted() = "\"" + replace("\n", "\\n") + "\""
}
