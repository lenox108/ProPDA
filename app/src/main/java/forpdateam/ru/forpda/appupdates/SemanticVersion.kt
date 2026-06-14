package forpdateam.ru.forpda.appupdates

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        return compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_PATTERN = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun parse(value: String?): SemanticVersion? {
            if (value.isNullOrBlank()) return null
            val match = VERSION_PATTERN.find(value) ?: return null
            return SemanticVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null
            )
        }
    }
}
