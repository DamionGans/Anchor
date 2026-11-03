package com.damiongans.anchor.spec

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Keeps `docs/SPEC.md` and the test suites in sync.
 *
 * Every requirement row (`| SPEC-<AREA>-<NNN> | ... |`) must be cited by at least one test
 * (in a test name, KDoc or comment), and every ID a test cites must exist in the spec. Rows
 * whose text contains `(manual)` are verified by hand or on CI hardware and are exempt from
 * the coverage check, but tests may still cite them.
 */
class SpecCoverageTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, SPEC_PATH).isFile }
        ?: fail("$SPEC_PATH not found above ${File("").absolutePath}")

    private val specRows: List<Pair<String, String>> = File(root, SPEC_PATH).readLines()
        .mapNotNull { line -> ROW.find(line)?.let { it.groupValues[1] to line } }

    private val citations: Map<String, Set<String>> by lazy {
        val result = mutableMapOf<String, MutableSet<String>>()
        testSources().forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            ID.findAll(file.readText()).forEach { result.getOrPut(it.value) { sortedSetOf() } += relative }
        }
        result
    }

    @Test
    fun specIdsAreUniqueAndWellFormed() {
        assertTrue(specRows.isNotEmpty(), "no SPEC rows found in $SPEC_PATH")
        val duplicates = specRows.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate spec IDs: $duplicates")
    }

    @Test
    fun everyRequirementIsCitedByATest() {
        val missing = specRows
            .filterNot { (_, line) -> MANUAL in line }
            .map { it.first }
            .filterNot { it in citations }
        assertTrue(missing.isEmpty(), "spec IDs without a test citing them:\n  " + missing.joinToString("\n  "))
    }

    @Test
    fun testsOnlyCiteExistingRequirements() {
        val known = specRows.map { it.first }.toSet()
        val unknown = citations.filterKeys { it !in known }
            .map { (id, files) -> "$id (${files.joinToString()})" }
        assertTrue(unknown.isEmpty(), "tests cite IDs missing from $SPEC_PATH:\n  " + unknown.joinToString("\n  "))
    }

    private fun testSources(): Sequence<File> = root.walkTopDown()
        .onEnter { dir -> dir.name !in SKIPPED_DIRS }
        .filter { it.isFile && it.extension in SOURCE_EXTENSIONS }
        .filter { file -> TEST_DIR.containsMatchIn(file.relativeTo(root).invariantSeparatorsPath) }

    private companion object {
        const val SPEC_PATH = "docs/SPEC.md"
        const val MANUAL = "(manual)"
        val ID = Regex("""SPEC-[A-Z]+-\d{3}""")
        val ROW = Regex("""^\|\s*`?(SPEC-[A-Z]+-\d{3})`?\s*\|""")
        // Kotlin test source sets (`src/commonTest`, `src/test`, `src/desktopTest`, ...) and the iOS test target.
        val TEST_DIR = Regex("""(^|/)src/[A-Za-z]*[Tt]est[A-Za-z]*/|^iosApp/AnchorTests/""")
        val SOURCE_EXTENSIONS = setOf("kt", "swift")
        val SKIPPED_DIRS = setOf(".git", ".gradle", ".kotlin", "build", "node_modules", ".idea")
    }
}
