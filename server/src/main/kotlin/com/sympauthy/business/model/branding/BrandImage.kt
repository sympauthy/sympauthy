package com.sympauthy.business.model.branding

import io.micronaut.http.MediaType

/**
 * An image a deployment's pages show: an SVG or a PNG, within [MAX_SIZE], whatever it was said to be by the file
 * name or the host it came from.
 */
class BrandImage private constructor(
    val content: ByteArray,
    val type: BrandImageType
) {
    companion object {
        /**
         * An image is held in memory for as long as the server runs.
         */
        const val MAX_SIZE = 1024 * 1024

        /**
         * The image [content] is, or null where it is over [MAX_SIZE] or neither an SVG nor a PNG.
         */
        fun ofOrNull(content: ByteArray): BrandImage? {
            if (content.size > MAX_SIZE) return null
            val type = BrandImageType.detectOrNull(content) ?: return null
            return BrandImage(content, type)
        }
    }
}

enum class BrandImageType(val mediaType: String) {
    SVG(MediaType.IMAGE_SVG),
    PNG(MediaType.IMAGE_PNG);

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        fun detectOrNull(content: ByteArray): BrandImageType? = when {
            content.size >= PNG_SIGNATURE.size &&
                PNG_SIGNATURE.indices.all { content[it] == PNG_SIGNATURE[it] } -> PNG

            isSvg(String(content, Charsets.ISO_8859_1)) -> SVG
            else -> null
        }

        /**
         * Whether the first element of [text] is an `svg` one, past what an XML document may open with: a byte
         * order mark, a declaration, a comment and a doctype, which an export from a drawing tool usually has.
         *
         * The bytes are read one to a character, so a document in UTF-16 is not recognised and is refused.
         */
        private fun isSvg(text: String): Boolean {
            var position = if (text.startsWith(UTF8_BOM)) UTF8_BOM.length else 0
            while (true) {
                while (position < text.length && text[position].isWhitespace()) position++
                position = when {
                    text.startsWith("<?", position) -> endOf(text, "?>", position)
                    text.startsWith("<!--", position) -> endOf(text, "-->", position)
                    text.startsWith("<!DOCTYPE", position) -> endOfDoctype(text, position)
                    else -> return isSvgElement(text, position)
                }
                if (position < 0) return false
            }
        }

        /**
         * Whether an `svg` element opens at [position], under any namespace prefix, since a document chooses its own.
         */
        private fun isSvgElement(text: String, position: Int): Boolean {
            if (text.getOrNull(position) != '<') return false
            val nameStart = position + 1
            val nameEnd = (nameStart until text.length).firstOrNull {
                text[it].isWhitespace() || text[it] == '>' || text[it] == '/'
            } ?: return false
            return text.substring(nameStart, nameEnd).substringAfter(':') == "svg"
        }

        /**
         * The position after [terminator] from [position], or -1 where it never comes.
         */
        private fun endOf(text: String, terminator: String, position: Int): Int {
            val end = text.indexOf(terminator, position)
            return if (end < 0) -1 else end + terminator.length
        }

        /**
         * The position after a doctype, or -1 where it never ends. Its internal subset may hold a `>`, a quoted
         * value a `>` or a `]`, and a comment a lone quote.
         */
        private fun endOfDoctype(text: String, position: Int): Int {
            var index = position
            var quote: Char? = null
            var inSubset = false
            while (index in text.indices) {
                val char = text[index]
                when {
                    quote != null -> if (char == quote) quote = null
                    text.startsWith("<!--", index) -> {
                        index = endOf(text, "-->", index)
                        continue
                    }
                    char == '"' || char == '\'' -> quote = char
                    char == '[' -> inSubset = true
                    char == ']' -> inSubset = false
                    char == '>' && !inSubset -> return index + 1
                }
                index++
            }
            return -1
        }

        /**
         * The UTF-8 byte order mark, as the three characters it reads as in ISO-8859-1.
         */
        private const val UTF8_BOM = "ï»¿"
    }
}

/**
 * The images a deployment may configure, each answered under its [com.sympauthy.util.configName].
 */
enum class BrandImageName {
    ICON,
    LOGO,
    LOGO_DARK
}
