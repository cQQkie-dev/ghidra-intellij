package com.codingmates.ghidra.intellij.ide.migration

/**
 * Numeric Gradle version used for comparing Ghidra's declared Gradle compatibility range.
 *
 * Ghidra application metadata currently uses ordinary numeric versions such as `8.5`, `9.1`,
 * and `9.6.0`. Qualifiers are deliberately rejected because they cannot be compared safely
 * without defining an additional release-channel policy.
 */
class GhidraGradleVersion private constructor(
    private val components: List<Int>,
) : Comparable<GhidraGradleVersion> {

    override fun compareTo(other: GhidraGradleVersion): Int {
        val componentCount = maxOf(components.size, other.components.size)

        for (index in 0 until componentCount) {
            val thisComponent = components.getOrElse(index) { 0 }
            val otherComponent = other.components.getOrElse(index) { 0 }

            val comparison = thisComponent.compareTo(otherComponent)
            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    override fun equals(other: Any?): Boolean =
        other is GhidraGradleVersion &&
            components == other.components

    override fun hashCode(): Int =
        components.hashCode()

    override fun toString(): String =
        components.joinToString(".")

    companion object {

        private val numericVersionPattern = Regex("""\d+(?:\.\d+)*""")

        fun parseOrNull(value: String?): GhidraGradleVersion? {
            val normalizedValue = value
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return null

            if (!numericVersionPattern.matches(normalizedValue)) {
                return null
            }

            val components = normalizedValue
                .split(".")
                .map(String::toInt)

            return GhidraGradleVersion(components)
        }
    }
}