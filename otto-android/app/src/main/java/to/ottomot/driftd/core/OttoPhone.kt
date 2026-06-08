package to.ottomot.driftd.core

/** US phone normalization (matches backend `DEMO_NANP_LAST_10`). */
object OttoPhone {
    private const val DEMO_BYPASS_LAST_10 = "5555555555"

    fun normalizedUS10Digits(raw: String?): String? {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("1") -> digits.takeLast(10)
            else -> null
        }
    }

    fun isDemoBypassPhone(phoneNumber: String?): Boolean =
        normalizedUS10Digits(phoneNumber) == DEMO_BYPASS_LAST_10
}
