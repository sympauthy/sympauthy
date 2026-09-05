package com.sympauthy.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.bufferedReader
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

/**
 * Holds the codes named in the server's sources and the keys of `error_messages.properties` to the
 * same set, in both directions.
 *
 * A code the bundle does not hold renders as null and is dropped from the response, which reads
 * exactly like `print-details-in-error` being off — so the failure has no symptom on any path
 * anyone looks at. Nothing else fails: the code compiles, the throw site reads correctly, and a
 * test asserting the error code passes.
 *
 * The two directions are not established the same way, because they do not need the same evidence.
 * A code with no message has to be caught where it is *named*, so the sources are read for the
 * positions a code can occupy. A message no code names only has to be absent as a string, so the
 * whole of the sources is read for the literal — which never fails on a site the position rules do
 * not recognise.
 *
 * `display_messages` and `mail_messages` are out of scope and stay out: a deployment derives their
 * keys from the claims and the templates it configured, so there is no fixed set to compare against.
 */
class ErrorMessageBundleTest {

    @Test
    fun `Every code named in the sources has a message in the bundle`() {
        assertEquals(
            emptyList<String>(), (namedCodes - bundleKeys).sorted(),
            "These codes are named at a throw site and the bundle holds no message for them, so the " +
                "caller is told an error code and nothing else."
        )
    }

    @Test
    fun `Every message in the bundle is named in the sources`() {
        assertEquals(
            emptyList<String>(), (bundleKeys - sourceLiterals).sorted(),
            "These messages have a key that is written nowhere in the sources. Delete them, or name " +
                "them where the failure they describe is thrown."
        )
    }
}

private const val SOURCES = "src/main/kotlin"

private const val BUNDLE = "src/main/resources/error_messages.properties"

/**
 * Where a code sits in the argument list of each factory docs/exception-code-standard.md names.
 * The description, where one is passed positionally, is the argument after it.
 */
private val FACTORIES = mapOf(
    "businessExceptionOf" to 0,
    "internalBusinessExceptionOf" to 0,
    "recoverableBusinessExceptionOf" to 0,
    "localizedExceptionOf" to 0,
    "oauth2ExceptionOf" to 1,
    "httpExceptionOf" to 1,
    "recoverableHttpExceptionOf" to 1,
    "configExceptionOf" to 1
)

/**
 * A parameter carrying a code is named for what it carries, so a code given by name is found by the
 * naming rule rather than by a list this test would have to be told about.
 */
private val NAMED_CODE = Regex("""\w*(?:[Dd]etailsId|[Dd]escriptionId|[Mm]essageId)\s*=\s*$""")

private val NAMED_ARGUMENT = Regex("""\w+\s*=\s*$""")

/**
 * The module holding the sources this test reads, found by walking up from the working directory so
 * that a run started above it still resolves.
 */
private val moduleRoot: Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
    .flatMap { sequenceOf(it, it.resolve("server")) }
    .firstOrNull { it.resolve(SOURCES).isDirectory() }
    ?: error("No module holding $SOURCES above ${Path.of("").toAbsolutePath()}.")

private val sources: List<KotlinSource> by lazy {
    Files.walk(moduleRoot.resolve(SOURCES)).use { paths ->
        paths.filter { it.toString().endsWith(".kt") }
            .map { KotlinSource(it.readText()) }
            .toList()
    }
}

private val bundleKeys: Set<String> by lazy {
    Properties().apply { moduleRoot.resolve(BUNDLE).bufferedReader().use(::load) }.stringPropertyNames()
}

private val namedCodes: Set<String> by lazy { sources.flatMapTo(mutableSetOf(), KotlinSource::codes) }

private val sourceLiterals: Set<String> by lazy {
    sources.flatMapTo(mutableSetOf()) { it.literals.filterNotNull() }
}

/**
 * A Kotlin file split at its string literals, so that a code can be recognised by what precedes it.
 * [fragments] holds the code between the literals — `fragments[i]` runs up to `literals[i]`, and the
 * last one to the end of the file — with the comments removed, so a code written in an example in a
 * KDoc is not read as one a throw site names.
 *
 * A literal that interpolates or escapes is null: neither can be a bundle key.
 */
private class KotlinSource(text: String) {

    val fragments = mutableListOf<String>()
    val literals = mutableListOf<String?>()

    init {
        val fragment = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> i = text.indexOf('\n', i).takeIf { it >= 0 } ?: text.length

                text.startsWith("/*", i) -> {
                    var depth = 0
                    do {
                        when {
                            text.startsWith("/*", i) -> { depth++; i += 2 }
                            text.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> i++
                        }
                    } while (i < text.length && depth > 0)
                }

                text.startsWith("\"\"\"", i) -> {
                    i = text.indexOf("\"\"\"", i + 3).takeIf { it >= 0 }?.plus(3) ?: text.length
                    fragments += fragment.toString().also { fragment.clear() }
                    literals += null
                }

                text[i] == '"' -> {
                    val start = ++i
                    while (i < text.length && text[i] != '"' && text[i] != '\n') {
                        i += if (text[i] == '\\') 2 else 1
                    }
                    val value = text.substring(start, minOf(i, text.length))
                    i++
                    fragments += fragment.toString().also { fragment.clear() }
                    literals += value.takeUnless { it.contains('$') || it.contains('\\') }
                }

                else -> fragment.append(text[i++])
            }
        }
        fragments += fragment.toString()
    }

    /**
     * Every code this file names: a literal given to a parameter [NAMED_CODE] recognises, and a
     * literal in the code position of one of the [FACTORIES].
     *
     * A code reaching a throw site as a variable is not seen. Every such site forwards a code
     * another site already named, which is what makes the omission affordable.
     */
    fun codes(): Set<String> {
        val codes = mutableSetOf<String>()
        for (index in literals.indices) {
            val literal = literals[index] ?: continue
            if (NAMED_CODE.containsMatchIn(fragments[index])) codes += literal
        }
        for ((factory, position) in FACTORIES) {
            val call = Regex("""\b$factory\s*\(""")
            fragments.forEachIndexed { index, fragment ->
                call.findAll(fragment).forEach { match ->
                    val from = match.range.last + 1
                    literalAt(index, from, position)?.let { codes += it }
                    literalAt(index, from, position + 1)
                        ?.takeIf { it.startsWith("description.") }
                        ?.let { codes += it }
                }
            }
        }
        return codes
    }

    /**
     * The literal at argument [position] of a call whose opening parenthesis is at [from] in
     * fragment [index], or null where that argument is not a literal, is given by name, or the call
     * ends before reaching it. An argument given by name is left to [NAMED_CODE], which reads the
     * name rather than the position.
     */
    private fun literalAt(index: Int, from: Int, position: Int): String? {
        var fragmentIndex = index
        var start = from
        var argument = 0
        var depth = 0
        while (fragmentIndex < literals.size) {
            val fragment = fragments[fragmentIndex]
            for (i in start until fragment.length) {
                when (fragment[i]) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> if (depth-- == 0) return null
                    ',' -> if (depth == 0) argument++
                }
            }
            if (argument > position) return null
            if (argument == position) {
                return literals[fragmentIndex]
                    ?.takeUnless { NAMED_ARGUMENT.containsMatchIn(fragments[fragmentIndex]) }
            }
            fragmentIndex++
            start = 0
        }
        return null
    }
}
